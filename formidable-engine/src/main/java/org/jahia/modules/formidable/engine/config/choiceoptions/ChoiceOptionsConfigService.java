package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The choice options theme read from {@code org.jahia.modules.formidable.choiceOptions.cfg}: the sources a
 * contributor may pick, resolved by their stable id, their cache, and the cap on a content query.
 */
@Component(service = ChoiceOptionsConfigService.class, configurationPid = ChoiceOptionsConfigService.PID, immediate = true)
@Designate(ocd = ChoiceOptionsConfig.class)
public class ChoiceOptionsConfigService {

    public static final String PID = "org.jahia.modules.formidable.choiceOptions";

    /**
     * An admin-declared options source for choice fields: a curated Jahia choicelist initializer exposed to
     * contributors under a stable id.
     *
     * @param id             stored in JCR ({@code optionsSourceKey})
     * @param label          shown in the source picker
     * @param initializerKey key of the Jahia choicelist initializer to evaluate
     * @param param          optional initializer parameter (empty when absent)
     */
    public record OptionsSource(String id, String label, String initializerKey, String param) {}

    private record Snapshot(Map<String, OptionsSource> sources, Duration cacheTtl, int queryMaxResults) {}

    private static final Logger log = LoggerFactory.getLogger(ChoiceOptionsConfigService.class);

    private final ThemeLifecycle<ChoiceOptionsConfig, Snapshot> lifecycle = new ThemeLifecycle<>(PID, ChoiceOptionsConfig.class, ChoiceOptionsConfigService::read);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
    }

    @Activate
    @Modified
    public void configure(ChoiceOptionsConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, config);
    }

    /** Reads the configuration into the snapshot the getters serve, no file behind it; public for the tests. */
    public void activate(ChoiceOptionsConfig config) {
        lifecycle.configure(null, config);
    }

    private static Snapshot read(ChoiceOptionsConfig config) {
        Snapshot snapshot = new Snapshot(
                Collections.unmodifiableMap(parseSources(config.optionsSources())),
                ConfigurationValues.timeoutSeconds("optionsSourcesCacheTtlSeconds", config.optionsSourcesCacheTtlSeconds(),
                        ChoiceOptionsConfig.DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS),
                ConfigurationValues.positiveOrDefault(config.optionsQueryMaxResults(), ChoiceOptionsConfig.DEFAULT_OPTIONS_QUERY_MAX_RESULTS)
        );
        log.info("ChoiceOptionsConfigService configured: {} source(s) declared, cacheTtl={}s, queryMaxResults={}",
                snapshot.sources().size(), snapshot.cacheTtl().toSeconds(), snapshot.queryMaxResults());
        return snapshot;
    }

    /**
     * Parses {@code optionsSources}: one {@code id|Label|initializerKey} or {@code id|Label|initializerKey|param}
     * per line. A defective line is logged and skipped; the first occurrence of a duplicate id wins.
     */
    private static Map<String, OptionsSource> parseSources(String raw) {
        Map<String, OptionsSource> result = new LinkedHashMap<>();
        for (String line : ConfigurationValues.lines(raw)) {
            parseSource(line).ifPresent(source -> {
                if (result.putIfAbsent(source.id(), source) != null) {
                    log.warn("[ChoiceOptionsConfigService] Duplicate optionsSources id '{}', keeping first occurrence.", source.id());
                }
            });
        }
        return result;
    }

    private static Optional<OptionsSource> parseSource(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 3) {
            log.warn("[ChoiceOptionsConfigService] Skipping malformed optionsSources entry (expected id|Label|initializerKey[|param]): '{}'", line);
            return Optional.empty();
        }
        String id = parts[0].trim();
        String label = parts[1].trim();
        String initializerKey = parts[2].trim();
        String param = parts.length == 4 ? parts[3].trim() : "";
        if (id.isEmpty() || label.isEmpty() || initializerKey.isEmpty()) {
            log.warn("[ChoiceOptionsConfigService] Skipping optionsSources entry with a blank id, label or initializerKey: '{}'", line);
            return Optional.empty();
        }
        return Optional.of(new OptionsSource(id, label, initializerKey, param));
    }

    /** Every configured source, in declaration order. */
    public Collection<OptionsSource> getOptionsSources() {
        return lifecycle.current().sources().values();
    }

    /**
     * Resolves an options source by its stable id.
     *
     * @param id the value stored in the JCR {@code optionsSourceKey} property
     * @return the configured options source, or empty if the id is unknown
     */
    public Optional<OptionsSource> resolveOptionsSource(String id) {
        return Optional.ofNullable(lifecycle.current().sources().get(id));
    }

    /** Maximum number of options a content-mode choice field may resolve; above it the field fails like a failing source. */
    public int getOptionsQueryMaxResults() {
        return lifecycle.current().queryMaxResults();
    }

    public Duration getOptionsSourcesCacheTtl() {
        return lifecycle.current().cacheTtl();
    }
}
