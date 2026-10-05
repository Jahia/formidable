package org.jahia.modules.formidable.engine.migration.v05;

import org.jahia.modules.formidable.engine.config.common.ConfigurationAttributes;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.jahia.modules.formidable.engine.config.common.FactoryEntries;
import org.jahia.modules.formidable.engine.config.common.FactoryEntry;
import org.jahia.modules.formidable.engine.migration.RemovedIn;
import org.jahia.services.modulemanager.spi.ConfigService;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The lists of earlier builds — one entry per line, {@code id|Label|…}, in one setting of the theme's file (where the
 * migration from the single PID put them) or still in that PID — turned into one entry file each, once, then removed
 * from the theme's file with a marker. Two lists had lines: the options sources ({@link #optionsSources}) and the
 * forward targets, standard and development ({@link #forwardTargets}). {@link FactoryEntries} runs the conversion
 * off the theme's callback, as it stores a console creation.
 *
 * <p>Lifecycle: part of the 0.5.0 wave, removed in 0.6 with {@link LegacyConfigurationMigration} — see
 * docs/administration/upgrade-notes.md, "Startup migrations".
 */
@RemovedIn("0.6")
public final class FormerListLines {

    /** Written into the theme's configuration once the former lines are entries. */
    public static final String LINES_CONVERTED = "formidable.linesConverted";
    /**
     * Written into the theme's configuration while a conversion is pending — a line refused for its id —: the ids
     * already turned into files, which a later run leaves alone, so that an entry the administrator deleted meanwhile
     * is not written back. Removed with the lines once the conversion completes.
     */
    public static final String LINES_CONVERTED_IDS = "formidable.linesConvertedIds";

    static final String OPTIONS_SOURCES = "optionsSources";
    static final String FORWARD_TARGETS = "forwardTargets";
    static final String DEV_FORWARD_TARGETS = "devForwardTargets";

    private static final Logger log = LoggerFactory.getLogger(FormerListLines.class);

    private final String themePid;
    private final String factoryPid;
    /** What an entry is, for the log lines: "forward target", "options source". */
    private final String what;
    /** The settings of earlier builds that held the list, one entry per line. */
    private final List<String> lineKeys;
    /** The entries those settings describe — each a map of the list's settings, id included — from their texts by key. */
    private final Function<Map<String, String>, List<Map<String, String>>> converter;

    private FormerListLines(String themePid, String factoryPid, String what, List<String> lineKeys,
                            Function<Map<String, String>, List<Map<String, String>>> converter) {
        this.themePid = themePid;
        this.factoryPid = factoryPid;
        this.what = what;
        this.lineKeys = List.copyOf(lineKeys);
        this.converter = converter;
    }

    /** The {@code optionsSources} lines of the choice options theme. */
    public static FormerListLines optionsSources(String themePid, String factoryPid) {
        return new FormerListLines(themePid, factoryPid, "options source", List.of(OPTIONS_SOURCES),
                texts -> optionsSourceEntries(texts.getOrDefault(OPTIONS_SOURCES, "")));
    }

    /** The {@code forwardTargets} and {@code devForwardTargets} lines of the form actions theme. */
    public static FormerListLines forwardTargets(String themePid, String factoryPid) {
        return new FormerListLines(themePid, factoryPid, "forward target", List.of(FORWARD_TARGETS, DEV_FORWARD_TARGETS),
                texts -> forwardTargetEntries(texts.getOrDefault(FORWARD_TARGETS, ""), texts.getOrDefault(DEV_FORWARD_TARGETS, "")));
    }

    /** Whether a conversion is due for these properties: they come from the theme's file, without the marker. */
    public boolean due(Map<String, Object> properties) {
        return properties != null && properties.containsKey(ConfigurationAttributes.FILEINSTALL_FILENAME)
                && !properties.containsKey(LINES_CONVERTED);
    }

    /**
     * Stores the lines as entries, then removes them from the theme's file with the marker. The caller runs one
     * conversion at a time, and the marker is read again from ConfigurationAdmin: two callbacks in a row convert once.
     * A line whose id is not letters, digits, dashes and underscores (a dot, a space…) cannot become a file: the lines
     * then stay where they are — the theme's file, or the single PID of earlier builds —, the others stored, and the
     * error names the ids and that configuration.
     *
     * @param declared whether an entry bound already declares an id, whatever its file is called
     */
    public void convert(ConfigurationAdmin admin, ConfigService configs, Map<String, Object> properties, Predicate<String> declared) {
        if (admin == null || configs == null) {
            return;
        }
        try {
            Configuration theme = admin.getConfiguration(themePid, "?");
            Dictionary<String, Object> current = theme.getProperties();
            if (current == null || current.get(LINES_CONVERTED) != null) {
                return;
            }
            Map<String, String> texts = texts(properties, lineKeys);
            String source = themePid;
            if (texts.isEmpty()) {
                Dictionary<String, Object> legacy = legacyProperties(admin);
                texts = legacy == null ? Map.of() : texts(legacy, lineKeys);
                source = LegacyConfigurationMigration.LEGACY_PID;
            }
            Set<String> converted = new LinkedHashSet<>(ConfigurationValues.commaSeparated(asText(current.get(LINES_CONVERTED_IDS))));
            List<String> written = new ArrayList<>();
            List<String> invalid = new ArrayList<>();
            storeLines(configs, converter.apply(texts), declared, converted, written, invalid);
            String files = FactoryEntries.fileName(factoryPid, "<id>");
            if (!written.isEmpty()) {
                log.warn("[{}] The {} lines became one file each, karaf/etc/{}: {}", themePid, what, files, written);
            }
            if (!invalid.isEmpty()) {
                // Only when this run stored something: an unchanged configuration written back would call this again.
                if (!written.isEmpty()) {
                    current.put(LINES_CONVERTED_IDS, String.join(",", converted));
                    theme.update(current);
                }
                log.error("[{}] The {} lines {} of {} have an id that is not letters, digits, dashes and underscores, "
                        + "which cannot name a file: the lines stay there. Declare each as a file with a valid id, point "
                        + "the content storing the former id at the new one, then remove the lines from {}", themePid,
                        what, invalid, source, source);
                return;
            }
            lineKeys.forEach(current::remove);
            current.remove(LINES_CONVERTED_IDS);
            current.put(LINES_CONVERTED, "true");
            theme.update(current);
        } catch (IOException | InvalidSyntaxException | RuntimeException e) {
            log.error("[{}] Could not turn the {} lines into files; they will be tried again on the next change", themePid, what, e);
        }
    }

    /**
     * Stores each line not converted yet: a line whose id cannot name a file goes to {@code invalid}, one whose id an
     * entry already declares is left as it is, the others are stored and go to {@code written}; every id handled joins
     * {@code converted}.
     */
    private void storeLines(ConfigService configs, List<Map<String, String>> lines, Predicate<String> declared,
                            Set<String> converted, List<String> written, List<String> invalid) throws IOException {
        for (Map<String, String> settings : lines) {
            String id = settings.get("id");
            if (!FactoryEntry.validId(id)) {
                invalid.add(id);
            } else if (converted.add(id)) {
                if (!declared.test(id) && FactoryEntries.store(configs, factoryPid, id, settings)) {
                    written.add(id);
                } else {
                    log.info("[{}] The {} '{}' is already configured; its line is not carried over it", themePid, what, id);
                }
            }
        }
    }

    /**
     * The source files the lines of the former list describe: {@code id|Label|initializerKey} or
     * {@code id|Label|initializerKey|param} each; a malformed line is logged and skipped.
     */
    static List<Map<String, String>> optionsSourceEntries(String lines) {
        List<Map<String, String>> entries = new ArrayList<>();
        for (String line : ConfigurationValues.lines(lines)) {
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3 || parts[0].isBlank() || parts[2].isBlank()) {
                log.warn("[ChoiceOptionsConfigService] Skipping malformed optionsSources line (expected id|Label|initializerKey[|param]): '{}'", line);
                continue;
            }
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("id", parts[0].trim());
            settings.put("label", parts[1].trim());
            settings.put("initializerKey", parts[2].trim());
            settings.put("param", parts.length == 4 ? parts[3].trim() : "");
            entries.add(settings);
        }
        return entries;
    }

    /** The target files the lines of the former lists describe, {@code id|Label|url} each, the development list's marked. */
    static List<Map<String, String>> forwardTargetEntries(String standardLines, String developmentLines) {
        List<Map<String, String>> entries = new ArrayList<>();
        for (boolean development : new boolean[] {false, true}) {
            for (String line : ConfigurationValues.lines(development ? developmentLines : standardLines)) {
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3 || parts[0].isBlank()) {
                    log.warn("[FormActionsConfigService] Skipping malformed forward target line (expected id|label|url): '{}'", line);
                    continue;
                }
                Map<String, String> settings = new LinkedHashMap<>();
                settings.put("id", parts[0].trim());
                settings.put("label", parts[1].trim());
                settings.put("url", parts[2].trim());
                settings.put("development", String.valueOf(development));
                entries.add(settings);
            }
        }
        return entries;
    }

    private static Map<String, String> texts(Map<String, Object> properties, List<String> keys) {
        Map<String, String> texts = new HashMap<>();
        keys.forEach(key -> {
            Object value = properties.get(key);
            if (value != null) {
                texts.put(key, String.valueOf(value));
            }
        });
        return texts;
    }

    private static Map<String, String> texts(Dictionary<String, Object> properties, List<String> keys) {
        Map<String, Object> copy = new HashMap<>();
        keys.forEach(key -> {
            Object value = properties.get(key);
            if (value != null) {
                copy.put(key, value);
            }
        });
        return texts(copy, keys);
    }

    private static Dictionary<String, Object> legacyProperties(ConfigurationAdmin admin) throws IOException, InvalidSyntaxException {
        Configuration[] found = admin.listConfigurations("(service.pid=" + LegacyConfigurationMigration.LEGACY_PID + ")");
        return found == null || found.length == 0 ? null : found[0].getProperties();
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
