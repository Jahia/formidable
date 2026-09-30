package org.jahia.modules.formidable.engine.config.fieldactions;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionSettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldActionsConfigServiceTest {

    private static FieldActionsConfigService activated(Map<String, Object> values) {
        FieldActionsConfigService service = new FieldActionsConfigService();
        service.activate(TestConfigs.of(FieldActionsConfig.class, values));
        return service;
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(FieldActionsConfig.class, FieldActionsConfigService.PID);
    }

    @Test
    void theProviderSettingsOfEarlierBuildsAreNamedAndIgnored() {
        // Verifies the one trace of the provider lists: a file still holding them gets a warning naming them — the
        // service of a field action is in its own module's configuration now — and nothing else changes.
        PrintStream previous = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        FieldActionsConfigService service = new FieldActionsConfigService();
        try {
            service.configure(TestConfigs.of(FieldActionsConfig.class), Map.of(
                    "fieldActionProviders", "zb|ZeroBounce|https://api.zerobounce.net|api_key|s3cr3t|query",
                    "enableDevFieldActionProviders", "true"));
        } finally {
            System.setErr(previous);
        }

        String logged = captured.toString(StandardCharsets.UTF_8);
        assertTrue(logged.contains("[fieldActionProviders, enableDevFieldActionProviders] no longer read"), logged);
        assertFalse(logged.contains("s3cr3t"), logged);
        assertEquals(FieldActionsConfig.DEFAULT_FIELD_ACTION_MAX_VALUE_LENGTH, service.getFieldActionSettings().maxValueLength());
    }

    @Test
    void activateGuardsTheEndpointsBoundsEachInItsOwnWay() {
        // Verifies the four guards: a non-positive TTL disables the cache, a non-positive rate limit disables the
        // endpoint (both meaningful zeros), while a non-positive length or count falls back to the default.
        FieldActionSettings settings = activated(Map.of(
                "fieldActionVerdictCacheTtlSeconds", -5L,
                "fieldActionRateLimitPerMinute", -1,
                "fieldActionMaxValueLength", 0,
                "fieldActionMaxValuesPerField", 0)).getFieldActionSettings();

        assertEquals(0L, settings.verdictCacheTtl().toSeconds());
        assertEquals(0, settings.rateLimitPerMinute());
        assertEquals(FieldActionsConfig.DEFAULT_FIELD_ACTION_MAX_VALUE_LENGTH, settings.maxValueLength());
        assertEquals(FieldActionsConfig.DEFAULT_FIELD_ACTION_MAX_VALUES_PER_FIELD, settings.maxValuesPerField());
    }
}
