package org.jahia.modules.formidable.engine.config.formactions;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormActionsConfigServiceTest {

    private static FormActionsConfigService activated(Map<String, Object> values) {
        FormActionsConfigService service = new FormActionsConfigService();
        service.activate(TestConfigs.of(FormActionsConfig.class, values));
        return service;
    }

    private static FormActionsConfigService withTargets(String standard, boolean enableDevelopment, String development) {
        return activated(Map.of("forwardTargets", standard, "enableDevForwardTargets", enableDevelopment, "devForwardTargets", development));
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(FormActionsConfig.class, FormActionsConfigService.PID);
    }

    @Test
    void activateAcceptsValidHttpsForwardTarget() {
        // Verifies the standard target happy path: a valid HTTPS target is kept and resolved by id.
        FormActionsConfigService service = withTargets("good|Good|https://api.example.com/forms", false, "");

        assertEquals(1, service.getForwardTargets().size());
        assertEquals("https://api.example.com/forms", service.resolveForwardTarget("good").orElseThrow().uri().toString());
    }

    @Test
    void activateRejectsForwardTargetsWithEmbeddedCredentials() {
        // Verifies hardening against credential-bearing forward target URLs.
        FormActionsConfigService service = withTargets("good|Good|https://api.example.com/forms\nbad-creds|Bad creds|https://user:pass@api.example.com/forms", false, "");

        assertEquals(1, service.getForwardTargets().size());
        assertTrue(service.resolveForwardTarget("good").isPresent());
        assertFalse(service.resolveForwardTarget("bad-creds").isPresent());
    }

    @Test
    void activateRejectsStandardForwardTargetUsingHttp() {
        // Verifies the standard target scheme guard: non-development targets must use HTTPS.
        FormActionsConfigService service = withTargets("bad-http|Bad HTTP|http://api.example.com/forms", false, "");

        assertTrue(service.getForwardTargets().isEmpty());
    }

    @Test
    void activateSkipsMalformedLinesAndKeepsTheFirstOfADuplicateId() {
        // Verifies resilience: a two-part line, an empty id, a malformed URI never poison the rest; the first id wins.
        FormActionsConfigService service = withTargets("two|parts\n|Blank|https://a.example\nbad|Bad|ht tp://x\ngood|Good|https://a.example\ngood|Again|https://b.example", false, "");

        assertEquals(1, service.getForwardTargets().size());
        assertEquals("https://a.example", service.resolveForwardTarget("good").orElseThrow().uri().toString());
    }

    @Test
    void activateExposesDevelopmentTargetWhenExplicitlyEnabled() {
        // Verifies the development-target happy path for localhost.
        FormActionsConfigService service = withTargets("", true, "local|Local|http://localhost:8081/hook");

        assertEquals(1, service.getForwardTargets().size());
        assertTrue(service.resolveForwardTarget("local").orElseThrow().development());
    }

    @Test
    void activateRejectsDevelopmentTargetWhenHostIsNotAllowed() {
        // Verifies the development-target host allowlist.
        FormActionsConfigService service = withTargets("", true, "bad-dev|Bad Dev|http://example.com/hook");

        assertTrue(service.getForwardTargets().isEmpty());
    }

    @Test
    void activateIgnoresDevelopmentTargetsWhenDisabled() {
        // Verifies the disabled-dev-targets path: the list is ignored whole when the switch is off.
        FormActionsConfigService service = withTargets("", false, "local|Local|http://localhost:8081/hook");

        assertTrue(service.getForwardTargets().isEmpty());
    }

    @Test
    void activateKeepsStandardTargetWhenDevelopmentTargetUsesSameId() {
        // Verifies duplicate-id precedence across standard and development target registries.
        FormActionsConfigService service = withTargets("shared|Standard|https://api.example.com/forms", true, "shared|Dev|http://localhost:8081/hook");

        assertEquals(1, service.getForwardTargets().size());
        assertEquals("https://api.example.com/forms", service.resolveForwardTarget("shared").orElseThrow().uri().toString());
        assertFalse(service.resolveForwardTarget("shared").orElseThrow().development());
    }

    @Test
    void activateFallsBackToDefaultTimeoutsWhenConfiguredValuesAreInvalidAndExposesValidOnes() {
        // Verifies timeout hardening: zero or negative values fall back to the defaults, explicit ones are exposed as is.
        FormActionsConfigService broken = activated(Map.of("forwardHttpConnectTimeoutSeconds", 0L, "forwardHttpRequestTimeoutSeconds", -1L));
        assertEquals(Duration.ofSeconds(ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS), broken.getForwardHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS), broken.getForwardHttpRequestTimeout());

        FormActionsConfigService configured = activated(Map.of("forwardHttpConnectTimeoutSeconds", 13L, "forwardHttpRequestTimeoutSeconds", 17L));
        assertEquals(Duration.ofSeconds(13), configured.getForwardHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(17), configured.getForwardHttpRequestTimeout());
    }

    @Test
    void activateBuildsReusableForwardHttpClientAndRefreshesItOnConfigChange() {
        // Verifies HttpClient reuse within one config snapshot and refresh after re-activation.
        FormActionsConfigService service = new FormActionsConfigService();
        service.activate(TestConfigs.of(FormActionsConfig.class));
        HttpClient first = service.getForwardHttpClient();
        HttpClient second = service.getForwardHttpClient();
        service.activate(TestConfigs.of(FormActionsConfig.class, Map.of("forwardHttpConnectTimeoutSeconds", 7L)));
        HttpClient third = service.getForwardHttpClient();

        assertSame(first, second);
        assertNotSame(second, third);
    }
}
