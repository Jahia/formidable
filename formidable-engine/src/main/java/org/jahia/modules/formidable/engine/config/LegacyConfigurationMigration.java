package org.jahia.modules.formidable.engine.config;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Carries a configuration made before the themes existed into a theme's own configuration. Until 0.5 the
 * module read one PID, {@value #LEGACY_PID}: a configuration that may live in
 * {@code karaf/etc/org.jahia.modules.formidable.cfg} (written by the provisioning API or by hand) or in
 * ConfigAdmin alone (the Felix console). Neither is read any more; both are still there, and ConfigAdmin
 * knows both.
 * <p>
 * The migration runs for one theme once that theme's configuration comes from its own file — the properties
 * carry {@code felix.fileinstall.filename} — since a value written before fileinstall loads the copied file
 * would be replaced by the file's defaults a moment later. It copies every setting of the theme that the
 * legacy configuration holds at a value other than the default, as long as the theme's file still holds the
 * default for it: a value the administrator already set in the new file wins, with a warning naming the
 * setting. The values are written as strings (a typed value, as the Felix console stores them, would be
 * persisted in fileinstall's typed syntax, which a .cfg file does not read back), together with a marker
 * property that keeps the migration from running again for that theme; fileinstall persists the update
 * into the theme's file and the service's next callback brings the merged configuration. The legacy file,
 * when there is one, gets a first line saying it is no longer read — it is never deleted: the administrator
 * wrote it. A write that fails is retried on the next callback, up to {@value #MAX_ATTEMPTS} times, after
 * which the theme's file rules as it stands and an error names what to re-enter.
 */
public final class LegacyConfigurationMigration {

    public static final String LEGACY_PID = "org.jahia.modules.formidable";
    /** A configuration loaded from a file carries the file's name under this key; one made without a file does not. */
    static final String FILEINSTALL_FILENAME = "felix.fileinstall.filename";
    /** Written into a theme's configuration with the carried values: the migration then never runs again for it. */
    static final String MARKER = "formidable.migratedFrom";
    /** How many writes may fail before the theme's file is declared authoritative as it stands. */
    static final int MAX_ATTEMPTS = 3;
    static final String LEGACY_FILE_NOTICE_PREFIX = "# Superseded";

    private static final Logger log = LoggerFactory.getLogger(LegacyConfigurationMigration.class);

    /** What one run did. {@code RETRY} is the only outcome that leaves the migration pending. */
    public enum Outcome { NOT_DUE, NOTHING, WRITTEN, RETRY, GIVEN_UP }

    private final String pid;
    /** The theme's settings and their defaults as text, derived from its definition the way the metatype does. */
    private final Map<String, String> defaults;
    private int failedAttempts;
    private boolean done;

    LegacyConfigurationMigration(String pid, Class<? extends Annotation> definition) {
        this.pid = pid;
        this.defaults = defaultsOf(definition);
    }

    /**
     * Runs the migration when it is due: the theme's configuration comes from its file, carries no marker,
     * and ConfigurationAdmin is bound. Anything else is {@link Outcome#NOT_DUE}, the migration untouched.
     */
    synchronized Outcome run(ConfigurationAdmin admin, Map<String, Object> properties) {
        if (done || properties == null || !properties.containsKey(FILEINSTALL_FILENAME)) {
            return Outcome.NOT_DUE;
        }
        if (properties.containsKey(MARKER)) {
            done = true;
            return Outcome.NOT_DUE;
        }
        if (admin == null) {
            log.debug("[{}] The migration of {} waits for ConfigurationAdmin", pid, LEGACY_PID);
            return Outcome.NOT_DUE;
        }
        try {
            Dictionary<String, Object> legacy = legacyProperties(admin);
            if (legacy == null) {
                done = true;
                return Outcome.NOTHING;
            }
            Map<String, Object> carried = carried(legacy, properties);
            if (carried.isEmpty()) {
                done = true;
                log.info("[{}] Nothing of {} to carry over: every setting of this theme is at its default there", pid, LEGACY_PID);
                return Outcome.NOTHING;
            }
            Configuration configuration = admin.getConfiguration(pid, "?");
            // The dictionary returned is the caller's private copy (Configuration#getProperties): edited in place,
            // then written back. Null means the configuration holds nothing yet: nothing to carry the settings into.
            Dictionary<String, Object> updated = configuration.getProperties();
            if (updated == null) {
                log.warn("[{}] The configuration holds no properties yet; the migration of {} waits", pid, carried.keySet());
                return retry();
            }
            carried.forEach(updated::put);
            updated.put(MARKER, LEGACY_PID);
            configuration.update(updated);
            done = true;
            log.warn("[{}] Carried over from {} into the theme's file: {}", pid, LEGACY_PID, carried.keySet());
            noteLegacyFile(legacy.get(FILEINSTALL_FILENAME));
            return Outcome.WRITTEN;
        } catch (IOException | InvalidSyntaxException e) {
            log.error("[{}] Could not carry the settings of {} over into the theme's file", pid, LEGACY_PID, e);
            return retry();
        }
    }

    /** The legacy configuration's properties, or null when ConfigAdmin holds no such configuration (a fresh install). */
    private static Dictionary<String, Object> legacyProperties(ConfigurationAdmin admin) throws IOException, InvalidSyntaxException {
        Configuration[] found = admin.listConfigurations("(service.pid=" + LEGACY_PID + ")");
        return found == null || found.length == 0 ? null : found[0].getProperties();
    }

    /**
     * The settings to write: held by the legacy configuration at a value other than the default, still at the
     * default in the theme's configuration. A setting the administrator already changed in the theme's file is
     * kept as the file says, and named in a warning when the two disagree.
     */
    private Map<String, Object> carried(Dictionary<String, Object> legacy, Map<String, Object> properties) {
        Map<String, Object> carried = new LinkedHashMap<>();
        List<String> kept = new ArrayList<>();
        defaults.forEach((setting, defaultText) -> {
            Object legacyValue = legacy.get(setting);
            if (legacyValue == null || asText(legacyValue).equals(defaultText)) {
                return;
            }
            Object current = properties.get(setting);
            String currentText = current == null ? defaultText : asText(current);
            if (currentText.equals(defaultText)) {
                carried.put(setting, asStrings(legacyValue));
            } else if (!currentText.equals(asText(legacyValue))) {
                kept.add(setting);
            }
        });
        if (!kept.isEmpty()) {
            log.warn("[{}] {} already set in the theme's file: keeping the file's values over those of {}", pid, kept, LEGACY_PID);
        }
        return carried;
    }

    private Outcome retry() {
        failedAttempts++;
        if (failedAttempts < MAX_ATTEMPTS) {
            log.warn("[{}] The migration of {} will be retried ({} of {} attempts made)", pid, LEGACY_PID, failedAttempts, MAX_ATTEMPTS);
            return Outcome.RETRY;
        }
        done = true;
        log.error("[{}] Gave up carrying the settings of {} over after {} attempts; the theme's file is in force as it stands — "
                + "re-enter the settings of this theme in it", pid, LEGACY_PID, MAX_ATTEMPTS);
        return Outcome.GIVEN_UP;
    }

    /**
     * A first line on the legacy file saying it is no longer read, written once — the notice is looked for before
     * writing, so the five themes leave one. The file is the administrator's and is never deleted; a file that
     * cannot be written is left alone, the log line above being the record.
     */
    private void noteLegacyFile(Object fileName) {
        if (fileName == null) {
            return;
        }
        try {
            Path file = Path.of(URI.create(String.valueOf(fileName)));
            if (!Files.isRegularFile(file)) {
                return;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.startsWith(LEGACY_FILE_NOTICE_PREFIX)) {
                return;
            }
            String notice = LEGACY_FILE_NOTICE_PREFIX + " on " + LocalDate.now() + ": the settings of this file moved to "
                    + "org.jahia.modules.formidable.<theme>.cfg (captcha, uploads, choiceOptions, formActions, fieldActions); "
                    + "this file is no longer read.\n";
            Files.writeString(file, notice + content, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            log.debug("[{}] Could not note on {} that it is no longer read", pid, fileName, e);
        }
    }

    /**
     * The theme's settings and their defaults as text: the attribute ids of the definition, derived from its
     * methods the way the metatype does ({@link #attributeId}), so an attribute whose id differs from its method
     * name is carried like the others.
     */
    static Map<String, String> defaultsOf(Class<? extends Annotation> definition) {
        Map<String, String> defaults = new TreeMap<>();
        for (Method method : definition.getDeclaredMethods()) {
            Object defaultValue = method.getDefaultValue();
            if (defaultValue != null) {
                defaults.put(attributeId(method.getName()), asText(defaultValue));
            }
        }
        return defaults;
    }

    /**
     * The attribute id the metatype derives from an annotation method name (OSGi Compendium, component property
     * types and metatype annotations): {@code $_$} becomes {@code -}, {@code $$} becomes {@code $}, a lone
     * {@code $} is dropped, {@code __} becomes {@code _} and a lone {@code _} becomes {@code .}. Plain camelCase
     * names are their own id.
     */
    static String attributeId(String methodName) {
        StringBuilder id = new StringBuilder(methodName.length());
        int i = 0;
        while (i < methodName.length()) {
            if (methodName.startsWith("$_$", i)) {
                id.append('-');
                i += 3;
            } else if (methodName.startsWith("$$", i)) {
                id.append('$');
                i += 2;
            } else if (methodName.startsWith("__", i)) {
                id.append('_');
                i += 2;
            } else {
                char c = methodName.charAt(i);
                if (c == '_') {
                    id.append('.');
                } else if (c != '$') {
                    // a lone $ is dropped
                    id.append(c);
                }
                i++;
            }
        }
        return id.toString();
    }

    /** A value as text, a multi-valued one by its elements — what "the same value" means here. */
    static String asText(Object value) {
        return value instanceof Object[] array ? Arrays.toString(array) : String.valueOf(value);
    }

    /**
     * A value as the strings to write into a configuration: a multi-valued one as an array of strings, a single
     * one as a string — the metatype coerces the strings back to the attribute's type.
     */
    static Object asStrings(Object value) {
        if (value instanceof Object[] array) {
            return Arrays.stream(array).map(String::valueOf).toArray(String[]::new);
        }
        return String.valueOf(value);
    }
}
