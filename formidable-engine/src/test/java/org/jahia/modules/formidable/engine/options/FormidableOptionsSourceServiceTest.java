package org.jahia.modules.formidable.engine.options;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.modules.formidable.engine.config.FormidableConfigService.OptionsSource;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormidableOptionsSourceServiceTest {

    private static final OptionsSource COUNTRIES = new OptionsSource("countries", "Countries", "country", "");

    private static FormidableConfigService configWith(OptionsSource source, Duration ttl) {
        FormidableConfigService config = mock(FormidableConfigService.class);
        when(config.resolveOptionsSource(anyString())).thenReturn(Optional.empty());
        if (source != null) {
            when(config.resolveOptionsSource(source.id())).thenReturn(Optional.of(source));
        }
        when(config.getOptionsSourcesCacheTtl()).thenReturn(ttl);
        return config;
    }

    private static ChoiceListInitializer initializerReturning(List<ChoiceListValue> values, AtomicInteger calls) {
        ChoiceListInitializer initializer = mock(ChoiceListInitializer.class);
        when(initializer.getChoiceListValues(any(), any(), any(), any(Locale.class), any()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return values;
                });
        return initializer;
    }

    @Test
    void resolveRejectsUnknownSource() {
        // Verifies the allowlist guard: a key outside the declared sources is refused (D9/D10).
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        service.setConfig(configWith(null, Duration.ofMinutes(5)));

        // Expected outcome: unknown keys fail loudly, they are never resolved best-effort.
        assertThrows(IllegalArgumentException.class, () -> service.resolve("not-declared", "en"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(null, "en"));
    }

    @Test
    void resolveMapsChoiceListValuesToManualOptionsJsonFormat() {
        // Verifies the bridge contract: ChoiceListValue in, manual-options JSON strings out.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        service.setConfig(configWith(COUNTRIES, Duration.ofMinutes(5)));
        AtomicInteger calls = new AtomicInteger();
        service.setInitializerLookup(key -> initializerReturning(List.of(
                new ChoiceListValue("France", "FR"),
                new ChoiceListValue("", "DE"),
                new ChoiceListValue("Blank value", "")
        ), calls));

        String[] options = service.resolve("countries", "en");

        // Expected outcome: blank values are dropped, blank labels fall back to the value.
        assertEquals(2, options.length);
        JSONObject first = new JSONObject(options[0]);
        assertEquals("FR", first.getString("value"));
        assertEquals("France", first.getString("label"));
        assertEquals(false, first.getBoolean("selected"));
        assertEquals("DE", new JSONObject(options[1]).getString("label"));
    }

    @Test
    void resolveCachesPerSourceAndLanguageUntilTtlExpires() {
        // Verifies the TTL cache: same (source, language) hits the cache, expiry re-resolves.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        service.setConfig(configWith(COUNTRIES, Duration.ofSeconds(60)));
        AtomicInteger calls = new AtomicInteger();
        ChoiceListInitializer initializer = initializerReturning(List.of(new ChoiceListValue("France", "FR")), calls);
        service.setInitializerLookup(key -> initializer);

        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-12T10:00:00Z"));
        service.setClock(new Clock() {
            @Override public Instant instant() { return now.get(); }
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        });

        service.resolve("countries", "en");
        service.resolve("countries", "en");
        assertEquals(1, calls.get());

        // A different language is a different cache entry.
        service.resolve("countries", "fr");
        assertEquals(2, calls.get());

        // Past the TTL, the source is asked again.
        now.set(now.get().plusSeconds(61));
        service.resolve("countries", "en");
        assertEquals(3, calls.get());
    }

    @Test
    void resolveBypassesCacheWhenSourceDefinitionChanges() {
        // Verifies config-change freshness: a redefined source does not serve stale options.
        FormidableConfigService config = mock(FormidableConfigService.class);
        when(config.getOptionsSourcesCacheTtl()).thenReturn(Duration.ofMinutes(10));
        when(config.resolveOptionsSource("countries")).thenReturn(Optional.of(COUNTRIES));

        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        service.setConfig(config);
        AtomicInteger calls = new AtomicInteger();
        ChoiceListInitializer initializer = initializerReturning(List.of(new ChoiceListValue("France", "FR")), calls);
        service.setInitializerLookup(key -> initializer);

        service.resolve("countries", "en");
        assertEquals(1, calls.get());

        // The admin re-points the same id to another initializer: the cache entry no longer matches.
        when(config.resolveOptionsSource("countries"))
                .thenReturn(Optional.of(new OptionsSource("countries", "Countries", "language", "")));
        service.resolve("countries", "en");
        assertEquals(2, calls.get());
    }

    @Test
    void resolveWrapsInitializerFailuresAndDoesNotCacheThem() {
        // Verifies the failure contract (D10 upstream): failures surface as IllegalStateException
        // and the next render retries instead of serving a cached failure.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        service.setConfig(configWith(COUNTRIES, Duration.ofMinutes(5)));
        AtomicInteger calls = new AtomicInteger();
        ChoiceListInitializer failing = mock(ChoiceListInitializer.class);
        when(failing.getChoiceListValues(any(), any(), any(), any(Locale.class), any()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("backend down");
                });
        service.setInitializerLookup(key -> failing);

        assertThrows(IllegalStateException.class, () -> service.resolve("countries", "en"));
        assertThrows(IllegalStateException.class, () -> service.resolve("countries", "en"));
        assertEquals(2, calls.get());
    }

    @Test
    void resolveFailsWhenInitializerIsMissing() {
        // Verifies the declared-but-unavailable case: a clear error, not an empty list.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        service.setConfig(configWith(COUNTRIES, Duration.ofMinutes(5)));
        service.setInitializerLookup(key -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.resolve("countries", "en"));
        assertTrue(error.getMessage().contains("country"));
    }

    @Test
    void resolveForFieldReturnsNullForManualFields() throws Exception {
        // Verifies the mode dispatch: a field without a source mixin is manual, the
        // caller keeps its stored options.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.isNodeType(anyString())).thenReturn(false);

        assertEquals(null, service.resolveForField(field, "en"));
    }

    @Test
    void resolveForFieldListsDirectChildCategories() throws Exception {
        // Verifies the category mode contract: direct jnt:category children become
        // options, value = category name, label = localized title.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        JCRNodeWrapper root = mock(JCRNodeWrapper.class);
        JCRNodeWrapper oled = categoryNode("oled", "OLED");
        JCRNodeWrapper translation = mock(JCRNodeWrapper.class);
        when(translation.isNodeType(anyString())).thenReturn(false);
        org.jahia.services.content.JCRNodeIteratorWrapper children =
                nodeIterator(java.util.List.of(oled, translation));
        when(root.getNodes()).thenReturn(children);

        JCRNodeWrapper field = fieldWithRootCategory(root);

        String[] options = service.resolveForField(field, "en");

        // Expected outcome: only the category child is exposed, in the manual JSON format.
        assertEquals(1, options.length);
        JSONObject option = new JSONObject(options[0]);
        assertEquals("oled", option.getString("value"));
        assertEquals("OLED", option.getString("label"));
        assertEquals(false, option.getBoolean("selected"));
    }

    @Test
    void resolveForFieldFailsWhenNoRootCategoryIsSelected() throws Exception {
        // Verifies the misconfiguration path: category mode without a picked root is a
        // resolution failure (D10/D11), never an empty list.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.isNodeType("fmdbmix:categoryOptions")).thenReturn(true);
        when(field.hasProperty("fmdb:optionsRootCategory")).thenReturn(false);
        when(field.getPath()).thenReturn("/form/fields/tv");

        assertThrows(IllegalStateException.class, () -> service.resolveForField(field, "en"));
    }

    @Test
    void resolveForFieldFailsWhenRootCategoryIsUnreachable() throws Exception {
        // Verifies the broken-weakref path: a deleted or unpublished root category is a
        // resolution failure, so live forms degrade (D10) instead of showing nothing.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.isNodeType("fmdbmix:categoryOptions")).thenReturn(true);
        when(field.hasProperty("fmdb:optionsRootCategory")).thenReturn(true);
        when(field.getPath()).thenReturn("/form/fields/tv");
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(field.getProperty("fmdb:optionsRootCategory")).thenReturn(property);
        when(property.getNode()).thenThrow(new javax.jcr.ItemNotFoundException("gone"));

        assertThrows(IllegalStateException.class, () -> service.resolveForField(field, "en"));
    }

    private static org.jahia.services.content.JCRNodeIteratorWrapper nodeIterator(
            java.util.List<? extends javax.jcr.Node> nodes) {
        org.jahia.services.content.JCRNodeIteratorWrapper iterator =
                mock(org.jahia.services.content.JCRNodeIteratorWrapper.class);
        java.util.Iterator<? extends javax.jcr.Node> delegate = nodes.iterator();
        when(iterator.hasNext()).thenAnswer(invocation -> delegate.hasNext());
        when(iterator.nextNode()).thenAnswer(invocation -> delegate.next());
        return iterator;
    }

    private static JCRNodeWrapper categoryNode(String name, String title) throws Exception {
        JCRNodeWrapper category = mock(JCRNodeWrapper.class);
        when(category.isNodeType("jnt:category")).thenReturn(true);
        when(category.getName()).thenReturn(name);
        when(category.getDisplayableName()).thenReturn(title);
        return category;
    }

    private static JCRNodeWrapper fieldWithRootCategory(JCRNodeWrapper root) throws Exception {
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.isNodeType("fmdbmix:categoryOptions")).thenReturn(true);
        when(field.hasProperty("fmdb:optionsRootCategory")).thenReturn(true);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(field.getProperty("fmdb:optionsRootCategory")).thenReturn(property);
        when(property.getNode()).thenReturn(root);
        return field;
    }

    @Test
    void resolveReturnsDefensiveCopies() {
        // Verifies cache integrity: a caller mutating the returned array cannot poison the cache.
        FormidableOptionsSourceService service = new FormidableOptionsSourceService();
        service.setConfig(configWith(COUNTRIES, Duration.ofMinutes(5)));
        AtomicInteger calls = new AtomicInteger();
        service.setInitializerLookup(key ->
                initializerReturning(List.of(new ChoiceListValue("France", "FR")), calls));

        String[] first = service.resolve("countries", "en");
        first[0] = "mutated";
        String[] second = service.resolve("countries", "en");

        assertEquals("FR", new JSONObject(second[0]).getString("value"));
    }
}
