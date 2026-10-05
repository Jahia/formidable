package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.jahia.modules.formidable.engine.config.common.FactoryEntries;
import org.jahia.modules.formidable.engine.migration.RemovedIn;
import org.jahia.modules.formidable.engine.migration.v05.FormerListLines;
import org.jahia.services.modulemanager.spi.ConfigService;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The choice options configuration read from {@code org.jahia.modules.formidable.choiceOptions.cfg} — the cache of
 * the resolved options and the cap on a content query — together with the options sources, one file each
 * ({@link OptionsSourceComponent}), resolved by their id.
 * <p>
 * The list {@code optionsSources} of earlier builds is turned into one source file per line, once, then removed from
 * the configuration's file ({@link FactoryEntries}).
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

    /** The configuration's own file: everything but the sources. */
    private record Theme(Duration cacheTtl, int queryMaxResults) {}

    private static final Logger log = LoggerFactory.getLogger(ChoiceOptionsConfigService.class);

    private final ThemeLifecycle<ChoiceOptionsConfig, Theme> lifecycle = new ThemeLifecycle<>(PID, ChoiceOptionsConfig.class, ChoiceOptionsConfigService::read);
    /** The source files: bound, adopted from the console, converted from the lines of earlier builds. */
    private final FactoryEntries<OptionsSourceComponent> sourceFiles = new FactoryEntries<>(PID,
            OptionsSourceComponent.FACTORY_PID, "options source", OptionsSourceComponent.SETTINGS, this::merge);
    /** The optionsSources lines of earlier builds, converted once into source files (0.5.0 migration wave). */
    @RemovedIn("0.6")
    private final FormerListLines formerLines = FormerListLines.optionsSources(PID, OptionsSourceComponent.FACTORY_PID);
    /** The sources by id, recomputed whenever a file changes. */
    private final AtomicReference<Map<String, OptionsSource>> sources = new AtomicReference<>(Map.of());

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
        sourceFiles.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
        sourceFiles.unsetConfigurationAdmin(admin);
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigService")
    public void setConfigService(ConfigService service) {
        sourceFiles.setConfigService(service);
    }

    public void unsetConfigService(ConfigService service) {
        sourceFiles.unsetConfigService(service);
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY, unbind = "unbindSource", updated = "updatedSource")
    public void bindSource(OptionsSourceComponent source) {
        sourceFiles.bind(source);
    }

    public void updatedSource(OptionsSourceComponent source) {
        sourceFiles.updated(source);
    }

    public void unbindSource(OptionsSourceComponent source) {
        sourceFiles.unbind(source);
    }

    @Activate
    @Modified
    public void configure(ChoiceOptionsConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, config);
        merge();
        sourceFiles.themeConfigured(properties, formerLines);
    }

    /** Reads the configuration into the snapshot the getters serve, no file behind it; public for the tests. */
    public void activate(ChoiceOptionsConfig config) {
        lifecycle.configure(null, config);
        merge();
    }

    /** The tests' seam: adoptions run in the calling thread. */
    void useForTests(Executor executor) {
        sourceFiles.useForTests(executor);
    }

    private static Theme read(ChoiceOptionsConfig config) {
        return new Theme(
                ConfigurationValues.timeoutSeconds("optionsSourcesCacheTtlSeconds", config.optionsSourcesCacheTtlSeconds(),
                        ChoiceOptionsConfig.DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS),
                ConfigurationValues.positiveOrDefault(config.optionsQueryMaxResults(), ChoiceOptionsConfig.DEFAULT_OPTIONS_QUERY_MAX_RESULTS));
    }

    private synchronized void merge() {
        Map<String, OptionsSource> byId = sourceFiles.merged(false, OptionsSourceComponent::source, file -> false, OptionsSource::id).byId();
        sources.set(byId);
        Theme theme;
        try {
            theme = lifecycle.current();
        } catch (IllegalStateException notYet) {
            return;
        }
        log.info("ChoiceOptionsConfigService configured: {} source(s) {}, cacheTtl={}s, queryMaxResults={}",
                byId.size(), byId.keySet(), theme.cacheTtl().toSeconds(), theme.queryMaxResults());
    }

    /** Every configured source, by id. */
    public Collection<OptionsSource> getOptionsSources() {
        return sources.get().values();
    }

    /**
     * Resolves an options source by its stable id.
     *
     * @param id the value stored in the JCR {@code optionsSourceKey} property
     * @return the configured options source, or empty if the id is unknown
     */
    public Optional<OptionsSource> resolveOptionsSource(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(sources.get().get(id));
    }

    /** Maximum number of options a content-mode choice field may resolve; above it the field fails like a failing source. */
    public int getOptionsQueryMaxResults() {
        return lifecycle.current().queryMaxResults();
    }

    public Duration getOptionsSourcesCacheTtl() {
        return lifecycle.current().cacheTtl();
    }
}
