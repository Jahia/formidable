package org.jahia.modules.formidable.engine.options;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.modules.formidable.engine.config.FormidableConfigService.OptionsSource;
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
