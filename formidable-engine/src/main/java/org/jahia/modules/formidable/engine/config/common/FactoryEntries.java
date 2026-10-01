package org.jahia.modules.formidable.engine.config.common;

import org.jahia.modules.formidable.engine.config.LegacyConfigurationMigration;
import org.jahia.services.modulemanager.spi.Config;
import org.jahia.services.modulemanager.spi.ConfigService;
import org.jahia.services.modulemanager.util.PropertiesValues;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * An administrator's list as one configuration file per entry — {@code karaf/etc/<factory PID>-<id>.cfg}, a factory
 * configuration each — held by the configuration service of its theme, which binds the entries' components and
 * hands them here. Three things are the same for every such list and live here:
 * <ul>
 *     <li>the entries bound, and a callback to the theme whenever they change;</li>
 *     <li>an entry the Felix console created — an unnamed factory configuration, a generated PID, no file, so out
 *     of sight in ConfigurationAdmin's own storage — stored again under its {@code id} through Jahia's
 *     {@link ConfigService}, which writes the file, and the console's configuration deleted;</li>
 *     <li>the lines of earlier builds ({@code id|Label|…} in one setting of the theme's file, where the migration from
 *     the single PID put them, or still in that PID) turned into one entry each, once, then removed from the
 *     theme's file with a marker.</li>
 * </ul>
 * Everything is stored through {@link ConfigService} — the service the provisioning API's {@code editConfiguration}
 * uses — never written by hand.
 *
 * @param <E> the entries' component
 */
public final class FactoryEntries<E extends FactoryEntry> {

    /** Written into the theme's configuration once the former lines are entries. */
    public static final String LINES_CONVERTED = "formidable.linesConverted";
    /**
     * Written into the theme's configuration while a conversion is pending — a line refused for its id —: the ids
     * already turned into files, which a later run leaves alone, so that an entry the administrator deleted meanwhile
     * is not written back. Removed with the lines once the conversion completes.
     */
    public static final String LINES_CONVERTED_IDS = "formidable.linesConvertedIds";

    private static final Logger log = LoggerFactory.getLogger(FactoryEntries.class);

    private final String themePid;
    private final String factoryPid;
    /** What an entry is, for the log lines: "forward target", "options source". */
    private final String what;
    /** The settings of an entry, in the order its file lists them, id first. */
    private final List<String> keys;
    private final Runnable onChange;

    private final List<E> entries = new CopyOnWriteArrayList<>();
    private final AtomicReference<ConfigService> configService = new AtomicReference<>();
    private final AtomicReference<ConfigurationAdmin> configurationAdmin = new AtomicReference<>();
    /**
     * Where a console creation or a former line becomes a file: off the DS callback that brought it. Jahia's
     * configuration service waits for the configuration event of what it stores, and that event is delivered on the
     * thread running the callback — storing from the callback would wait out its timeout for each entry.
     */
    private Executor adoptions = CompletableFuture::runAsync;
    /** The console creations already handed to an adoption: a bind and a theme's callback may both see one. */
    private final Set<String> adopting = ConcurrentHashMap.newKeySet();

    public FactoryEntries(String themePid, String factoryPid, String what, List<String> keys, Runnable onChange) {
        this.themePid = themePid;
        this.factoryPid = factoryPid;
        this.what = what;
        this.keys = List.copyOf(keys);
        this.onChange = onChange;
    }

    /** The file an entry is stored in: the name Jahia's configuration service and the provisioning API give it. */
    public static String fileName(String factoryPid, String id) {
        return factoryPid + "-" + id + ".cfg";
    }

    public List<E> entries() {
        return List.copyOf(entries);
    }

    public void bind(E entry) {
        entries.add(entry);
        onChange.run();
        adopt(entry);
    }

    public void updated(E entry) {
        onChange.run();
        adopt(entry);
    }

    public void unbind(E entry) {
        entries.remove(entry);
        onChange.run();
    }

    public void setConfigService(ConfigService service) {
        configService.set(service);
        entries.forEach(this::adopt);
    }

    public void unsetConfigService(ConfigService service) {
        configService.compareAndSet(service, null);
    }

    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        configurationAdmin.set(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        configurationAdmin.compareAndSet(admin, null);
    }

    /** The tests' seam: adoptions run in the calling thread. */
    public void useForTests(Executor executor) {
        adoptions = executor;
    }

    /**
     * The theme's configuration was received: adopt what the console created meanwhile, and convert the lines of
     * earlier builds when there are any — off this callback, as an adoption is.
     *
     * @param properties the theme's raw properties, null when driven without a file (the tests)
     * @param lineKeys   the settings of earlier builds that held the list, one entry per line
     * @param converter  the entries those settings describe — each a map of this list's settings, id included — from
     *                   the settings' texts by key (a key absent is an empty text)
     */
    public void themeConfigured(Map<String, Object> properties, List<String> lineKeys,
                                Function<Map<String, String>, List<Map<String, String>>> converter) {
        entries.forEach(this::adopt);
        if (properties == null || !properties.containsKey("felix.fileinstall.filename") || properties.containsKey(LINES_CONVERTED)) {
            return;
        }
        Map<String, Object> received = Map.copyOf(properties);
        adoptions.execute(() -> convertLines(received, lineKeys, converter));
    }

    /**
     * Stores the lines as entries, then removes them from the theme's file with the marker. One conversion at a time,
     * the marker read again from ConfigurationAdmin: two callbacks in a row convert once. A line whose id is not
     * letters, digits, dashes and underscores (a dot, a space…) cannot become a file: the lines then stay where they
     * are — the theme's file, or the single PID of earlier builds —, the others stored, and the error names the ids
     * and that configuration.
     */
    private synchronized void convertLines(Map<String, Object> properties, List<String> lineKeys,
                                           Function<Map<String, String>, List<Map<String, String>>> converter) {
        ConfigurationAdmin admin = configurationAdmin.get();
        ConfigService configs = configService.get();
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
            storeLines(configs, converter.apply(texts), converted, written, invalid);
            String files = fileName(factoryPid, "<id>");
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

    /**
     * Turns an entry the Felix console created into a file: its settings stored again under its id through Jahia's
     * {@link ConfigService}, and the console's configuration deleted. Nothing without an id (the console form says
     * it is required), or when the id is configured already — that configuration wins, the console's one is left
     * for the administrator to delete.
     */
    private void adopt(E entry) {
        ConfigService configs = configService.get();
        if (configs == null || !entry.createdWithoutFile(factoryPid)) {
            return;
        }
        String id = entry.id();
        String pid = entry.pid();
        if (id.isBlank()) {
            log.warn("[{}] A {} created in the console ({}) has no valid Id: set one, letters, digits, dashes and underscores", themePid, what, pid);
            return;
        }
        if (!adopting.add(pid)) {
            return;
        }
        adoptions.execute(() -> {
            boolean adopted = false;
            try {
                if (declaredElsewhere(id, entry) || !store(configs, factoryPid, id, entry.settings(keys))) {
                    log.warn("[{}] A {} created in the console ({}) names the id '{}', which is configured already; that "
                            + "configuration wins — delete the console's one", themePid, what, pid, id);
                    return;
                }
                configs.deleteConfig(configs.getConfig(pid));
                adopted = true;
                String file = fileName(factoryPid, id);
                log.warn("[{}] The {} '{}' created in the console is now the file {}", themePid, what, id, file);
            } catch (IOException | RuntimeException e) {
                log.error("[{}] Could not turn the {} '{}' created in the console into its file", themePid, what, id, e);
            } finally {
                // A conflict or a failure is tried again at the next callback: the administrator may have removed
                // the file the console's entry collided with.
                if (!adopted) {
                    adopting.remove(pid);
                }
            }
        });
    }

    /**
     * What the entries bound describe, by id: the standard ones, then — while {@code developmentEnabled} — the
     * development ones, a development id never shadowing a standard one; by id within each, so a picker has a stable
     * order whatever order DS bound the files in. An entry describing nothing usable counts for nothing.
     *
     * @param described   what an entry describes, empty when it describes nothing usable
     * @param development whether an entry is a development one (always false for a list without any)
     * @param idOf        the id of what an entry describes
     * @return the entries by id, and how many development entries the switch left out
     */
    public <T> Merged<T> merged(boolean developmentEnabled, Function<E, java.util.Optional<T>> described,
                                java.util.function.Predicate<E> development, Function<T, String> idOf) {
        Map<String, T> byId = new java.util.LinkedHashMap<>();
        Map<String, E> keptBy = new HashMap<>();
        int ignored = 0;
        for (boolean ofDevelopment : new boolean[] {false, true}) {
            // By id, then by PID: of two entries declaring one id, the same one is kept whatever order DS bound them in.
            List<Map.Entry<E, T>> ofKind = entries.stream()
                    .filter(entry -> development.test(entry) == ofDevelopment)
                    .flatMap(entry -> described.apply(entry).stream().map(value -> Map.entry(entry, value)))
                    .sorted(java.util.Comparator.<Map.Entry<E, T>, String>comparing(pair -> idOf.apply(pair.getValue()))
                            .thenComparing(pair -> pair.getKey().pid()))
                    .toList();
            if (ofDevelopment && !developmentEnabled) {
                ignored = ofKind.size();
                continue;
            }
            for (Map.Entry<E, T> pair : ofKind) {
                String id = idOf.apply(pair.getValue());
                E kept = keptBy.putIfAbsent(id, pair.getKey());
                if (kept == null) {
                    byId.put(id, pair.getValue());
                } else {
                    String keptPid = kept.pid();
                    String ignoredPid = pair.getKey().pid();
                    boolean standardWins = ofDevelopment && !development.test(kept);
                    String why = standardWins ? " (a standard entry wins over a development one)" : "";
                    log.warn("[{}] Two {} configurations declare the id '{}': {} is kept, {} is ignored{}", themePid, what,
                            id, keptPid, ignoredPid, why);
                }
            }
        }
        return new Merged<>(java.util.Collections.unmodifiableMap(byId), ignored);
    }

    /** The entries by id, and the development entries the switch left out. */
    public record Merged<T>(Map<String, T> byId, int ignoredDevelopment) {}

    /**
     * Stores each line not converted yet: a line whose id cannot name a file goes to {@code invalid}, one whose id an
     * entry already declares is left as it is, the others are stored and go to {@code written}; every id handled joins
     * {@code converted}.
     */
    private void storeLines(ConfigService configs, List<Map<String, String>> lines, Set<String> converted,
                            List<String> written, List<String> invalid) throws IOException {
        for (Map<String, String> settings : lines) {
            String id = settings.get("id");
            if (!FactoryEntry.validId(id)) {
                invalid.add(id);
            } else if (converted.add(id)) {
                if (!declaredElsewhere(id, null) && store(configs, factoryPid, id, settings)) {
                    written.add(id);
                } else {
                    log.info("[{}] The {} '{}' is already configured; its line is not carried over it", themePid, what, id);
                }
            }
        }
    }

    /**
     * Whether an entry bound already declares this id, whatever its file is called: core's configuration service
     * finds an entry by its file's name only, and a file's {@code -<id>} is a convention, not the rule.
     *
     * @param except the entry being adopted, which declares the id itself
     */
    private boolean declaredElsewhere(String id, E except) {
        return entries.stream().anyMatch(entry -> entry != except && id.equals(entry.id()));
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Stores an entry for this id through Jahia's {@link ConfigService} — which writes
     * {@code karaf/etc/<factory PID>-<id>.cfg} and loads it — unless one exists: false then, nothing written.
     */
    public static boolean store(ConfigService configs, String factoryPid, String id, Map<String, String> settings) throws IOException {
        Config config = configs.getConfig(factoryPid, id);
        if (!config.getRawProperties().isEmpty()) {
            return false;
        }
        PropertiesValues values = config.getValues();
        settings.forEach(values::setProperty);
        configs.storeConfig(config);
        return true;
    }
}
