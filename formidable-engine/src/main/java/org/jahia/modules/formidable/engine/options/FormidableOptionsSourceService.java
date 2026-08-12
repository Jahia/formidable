package org.jahia.modules.formidable.engine.options;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.modules.formidable.engine.config.FormidableConfigService.OptionsSource;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializerService;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Resolves the option list of a sourced choice field at display time.
 *
 * The service bridges the admin-declared options sources (curated Jahia choicelist
 * initializers, see {@code optionsSources} in org.jahia.modules.formidable.cfg) to a
 * primitive-friendly contract usable from the GraalVM JS server views through
 * {@code server.osgi.getService}: a source key and a BCP-47 language tag in, an array
 * of JSON-encoded {@code {"value","label","selected"}} strings out — the exact storage
 * format of manual options, so downstream rendering code cannot tell them apart.
 *
 * Results are cached in memory per (source, language) for the configured TTL. A cache
 * entry also remembers the source definition it was resolved from, so an admin config
 * change takes effect on the next resolution instead of waiting for the TTL. Failures
 * are never cached: a source that errors is retried on the next render.
 */
@Component(service = FormidableOptionsSourceService.class, immediate = true)
public class FormidableOptionsSourceService {

    private static final Logger log = LoggerFactory.getLogger(FormidableOptionsSourceService.class);

    private record CacheEntry(OptionsSource source, String[] options, Instant expiresAt) {}

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private FormidableConfigService config;

    // Seams for unit tests: production values bridge to the Jahia platform services.
    private Clock clock = Clock.systemUTC();
    private Function<String, ChoiceListInitializer> initializerLookup =
            key -> ChoiceListInitializerService.getInstance().getInitializers().get(key);

    @Reference
    public void setConfig(FormidableConfigService config) {
        this.config = config;
    }

    /**
     * Resolves the options of a declared source for one language.
     *
     * @param sourceKey   id of an admin-declared options source ({@code fmdb:optionsSourceKey})
     * @param languageTag BCP-47 language tag of the rendered form (for example {@code en}, {@code fr-FR})
     * @return the options as JSON-encoded {@code {"value","label","selected"}} strings,
     *         possibly empty — never null
     * @throws IllegalArgumentException when the key does not match any declared source
     *                                  (unknown, or removed from the allowlist)
     * @throws IllegalStateException    when the source is declared but cannot deliver
     *                                  (initializer missing or failing)
     */
    public String[] resolve(String sourceKey, String languageTag) {
        OptionsSource source = config.resolveOptionsSource(sourceKey == null ? "" : sourceKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown options source '" + sourceKey + "': it is not declared in optionsSources "
                                + "(org.jahia.modules.formidable.cfg)"));

        String cacheKey = source.id() + '|' + languageTag;
        CacheEntry cached = cache.get(cacheKey);
        Instant now = clock.instant();
        if (cached != null && cached.source().equals(source) && now.isBefore(cached.expiresAt())) {
            return cached.options().clone();
        }

        String[] options = resolveFromInitializer(source, languageTag);
        cache.put(cacheKey, new CacheEntry(source, options, now.plus(config.getOptionsSourcesCacheTtl())));
        return options.clone();
    }

    private String[] resolveFromInitializer(OptionsSource source, String languageTag) {
        ChoiceListInitializer initializer = initializerLookup.apply(source.initializerKey());
        if (initializer == null) {
            throw new IllegalStateException("Options source '" + source.id() + "' points to choicelist "
                    + "initializer '" + source.initializerKey() + "', which is not available on this platform");
        }

        List<ChoiceListValue> values;
        try {
            values = initializer.getChoiceListValues(
                    null,
                    source.param().isEmpty() ? null : source.param(),
                    null,
                    Locale.forLanguageTag(languageTag),
                    new HashMap<>());
        } catch (Exception e) {
            throw new IllegalStateException("Options source '" + source.id() + "' failed to resolve for "
                    + "language '" + languageTag + "': " + e.getMessage(), e);
        }

        if (values == null) {
            return new String[0];
        }

        return values.stream()
                .map(value -> toOptionJson(source, value))
                .filter(java.util.Objects::nonNull)
                .toArray(String[]::new);
    }

    private static String toOptionJson(OptionsSource source, ChoiceListValue choice) {
        try {
            String value = choice.getValue() != null ? choice.getValue().getString() : null;
            if (value == null || value.isEmpty()) {
                return null;
            }
            String label = choice.getDisplayName() != null && !choice.getDisplayName().isEmpty()
                    ? choice.getDisplayName()
                    : value;
            return new JSONObject(Map.of("value", value, "label", label, "selected", false)).toString();
        } catch (Exception e) {
            log.debug("[FormidableOptionsSourceService] Skipping unreadable choice value from source '{}'",
                    source.id(), e);
            return null;
        }
    }

    // Test seams

    void setClock(Clock clock) {
        this.clock = clock;
    }

    void setInitializerLookup(Function<String, ChoiceListInitializer> initializerLookup) {
        this.initializerLookup = initializerLookup;
    }
}
