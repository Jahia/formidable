package org.jahia.modules.formidable.engine.config.fieldactions;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionProvider;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionSettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    private static FieldActionsConfigService withProviders(String providers) {
        return activated(Map.of("fieldActionProviders", providers));
    }

    private static FieldActionsConfigService withProviders(String providers, boolean enableDevelopment, String development) {
        return activated(Map.of("fieldActionProviders", providers, "enableDevFieldActionProviders", enableDevelopment, "devFieldActionProviders", development));
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(FieldActionsConfig.class, FieldActionsConfigService.PID);
    }

    @Test
    void activateParsesFieldActionProvidersAndSkipsWhatItCannotTrust() {
        // Verifies the provider registry: a full entry and a credential-less one are kept, the empty label falling
        // back to the id; an entry with a header but no credential, an HTTP base, embedded credentials, a duplicate
        // id and a two-part line are skipped without poisoning the rest — and a parsed provider never prints its
        // secret, since a toString reaches the logs.
        FieldActionsConfigService service = withProviders("""
                email-check|Email verification|https://api.example.com/v1|X-Api-Key|s3cr3t
                crm||https://crm.internal/api
                half|Half|https://api.example.com|X-Api-Key
                plain|Plain|http://api.example.com
                creds|Creds|https://user:pw@api.example.com
                email-check|Again|https://other.example.com
                two|parts""");

        FieldActionSettings settings = service.getFieldActionSettings();
        assertEquals(List.of("email-check", "crm"), List.copyOf(settings.providers().keySet()));
        FieldActionProvider emailCheck = service.resolveFieldActionProvider("email-check").orElseThrow();
        assertEquals("X-Api-Key", emailCheck.credentialHeader());
        assertEquals("s3cr3t", emailCheck.credential());
        assertFalse(emailCheck.toString().contains("s3cr3t"));
        assertEquals("crm", service.resolveFieldActionProvider("crm").orElseThrow().label());
        assertTrue(service.resolveFieldActionProvider("half").isEmpty());
        assertTrue(service.resolveFieldActionProvider(null).isEmpty());
        assertEquals(300L, settings.verdictCacheTtl().toSeconds());
        assertEquals(30, settings.rateLimitPerMinute());
        assertEquals(512, settings.maxValueLength());
        assertEquals(50, settings.maxValuesPerField());
    }

    @Test
    void activateReadsWhereTheCredentialGoesAndSkipsAnEntryThatSaysSomethingElse() {
        // Verifies the sixth part of a provider line: query puts the key on the URL (ZeroBounce), header is the
        // default spelled out, anything else is a typo the administrator must see rather than a header sent by
        // accident — and a query credential without a value has no parameter to build.
        FieldActionsConfigService service = withProviders("""
                zb|ZeroBounce|https://api.zerobounce.net|api_key|s3cr3t|query
                exp|Experian|https://api.experianaperture.io|Auth-Token|t0k3n|header
                odd|Odd|https://api.example.com|X-Key|k|cookie
                bare|Bare|https://api.example.com|||query""");

        assertEquals(List.of("zb", "exp"), List.copyOf(service.getFieldActionSettings().providers().keySet()));
        assertTrue(service.resolveFieldActionProvider("zb").orElseThrow().credentialInQuery());
        assertFalse(service.resolveFieldActionProvider("exp").orElseThrow().credentialInQuery());
        assertTrue(service.resolveFieldActionProvider("odd").isEmpty());
        assertTrue(service.resolveFieldActionProvider("bare").isEmpty());
    }

    @Test
    void aRefusedProviderLineIsLoggedWithItsIdAndNeverItsCredential() {
        // Verifies the administration page's promise on the one refusal that reads past the fifth '|': a credential
        // carrying one is split there, its tail lands in the sixth part, and the warning must name the two accepted
        // values rather than echo what it found. Put the part back on the WARN line and this test fails.
        PrintStream previous = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        FieldActionsConfigService service;
        try {
            service = withProviders("x|X|https://api.example.com|K|se|cret");
        } finally {
            System.setErr(previous);
        }

        String logged = captured.toString(StandardCharsets.UTF_8);
        assertTrue(service.getFieldActionSettings().providers().isEmpty());
        assertTrue(logged.contains("entry 'x'"), "the operator still learns which line was refused: " + logged);
        assertFalse(logged.contains("cret"), logged);
    }

    @Test
    void activateExposesADevelopmentFieldActionProviderBehindItsSwitchAndKeepsTheStandardRuleOtherwise() {
        // Verifies the development list of the providers, the mirror of the forward targets': a provider over plain
        // HTTP on localhost — the samples' double of Experian — is accepted behind the switch only, a remote HTTP one
        // never, a development id never shadows a standard one, and the standard list keeps its HTTPS rule whatever
        // the switch says. Without the switch the list is ignored whole, as devForwardTargets is.
        String standard = "experian|Experian|https://api.experianaperture.io|Auth-Token|real";
        String development = """
                experian-stub|Experian (stub)|http://localhost:8080/modules/formidable-samples/experian-stub|Auth-Token|stub-token
                experian|Shadow|http://localhost:8080/shadow
                remote|Remote|http://api.example.com""";

        FieldActionsConfigService enabled = withProviders(standard, true, development);
        assertEquals(List.of("experian", "experian-stub"), List.copyOf(enabled.getFieldActionSettings().providers().keySet()));
        assertEquals("real", enabled.resolveFieldActionProvider("experian").orElseThrow().credential());
        assertEquals("http://localhost:8080/modules/formidable-samples/experian-stub",
                enabled.resolveFieldActionProvider("experian-stub").orElseThrow().baseUri().toString());

        FieldActionsConfigService disabled = withProviders(standard, false, development);
        assertEquals(List.of("experian"), List.copyOf(disabled.getFieldActionSettings().providers().keySet()));

        FieldActionsConfigService httpStandard = withProviders("plain|Plain|http://localhost:8080/plain", true, "");
        assertTrue(httpStandard.getFieldActionSettings().providers().isEmpty());
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
