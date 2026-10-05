package org.jahia.modules.formidable.engine.config;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes into a theme's file every setting it does not hold, at its built-in default. Jahia copies a module's
 * configuration file once and keeps the administrator's copy as edited, so a setting added by a later version never
 * reaches an existing installation's file: the module applies its default all the same, but the administrator reading
 * the file does not know the setting exists. The completion only adds: a value present in the file, edited or not, is
 * never touched, and a key the module does not know is never removed. It writes only when something is missing — the
 * write's own callback then brings a complete configuration, which writes nothing — and only into a configuration that
 * comes from its file ({@value LegacyConfigurationMigration#FILEINSTALL_FILENAME}), the one fileinstall persists the
 * update into. A write that fails is logged and not tried again before the next callback: meanwhile the module applies
 * the defaults the write would have written.
 */
final class MissingSettingsCompletion {

    private static final Logger log = LoggerFactory.getLogger(MissingSettingsCompletion.class);

    private final String pid;
    /** The theme's settings and their defaults, as the strings written into a configuration. */
    private final Map<String, Object> defaults;

    MissingSettingsCompletion(String pid, Class<? extends Annotation> definition) {
        this.pid = pid;
        this.defaults = defaultsOf(definition);
    }

    /**
     * Writes the settings missing from the theme's configuration, when it comes from its file and ConfigurationAdmin
     * is bound.
     *
     * @param properties the raw properties DS handed over, null when the service is driven without a file (the tests)
     * @return the settings written, empty when nothing was missing, nothing was due or the write failed
     */
    synchronized Map<String, Object> run(ConfigurationAdmin admin, Map<String, Object> properties) {
        if (admin == null || properties == null || !properties.containsKey(LegacyConfigurationMigration.FILEINSTALL_FILENAME)) {
            return Map.of();
        }
        try {
            Configuration configuration = admin.getConfiguration(pid, "?");
            // ConfigurationAdmin's copy, never the properties DS handed over: those always hold every setting, the
            // component description carrying the definition's defaults as component properties (bnd writes them).
            // The caller's private copy (Configuration#getProperties): edited in place, then written back.
            Dictionary<String, Object> updated = configuration.getProperties();
            if (updated == null) {
                return Map.of();
            }
            Map<String, Object> added = new TreeMap<>();
            defaults.forEach((setting, value) -> {
                if (updated.get(setting) == null) {
                    updated.put(setting, value);
                    added.put(setting, value);
                }
            });
            if (added.isEmpty()) {
                return Map.of();
            }
            configuration.update(updated);
            log.info("[{}] Added the settings missing from the theme's file, at their defaults: {}", pid, added.keySet());
            return added;
        } catch (IOException e) {
            log.error("[{}] Could not add the settings missing from the theme's file; their defaults apply all the same", pid, e);
            return Map.of();
        }
    }

    /** The definition's settings and their defaults as strings, the way the migration writes its values. */
    static Map<String, Object> defaultsOf(Class<? extends Annotation> definition) {
        Map<String, Object> defaults = new TreeMap<>();
        for (Method method : definition.getDeclaredMethods()) {
            Object defaultValue = method.getDefaultValue();
            if (defaultValue != null) {
                defaults.put(LegacyConfigurationMigration.attributeId(method.getName()), LegacyConfigurationMigration.asStrings(defaultValue));
            }
        }
        return defaults;
    }
}
