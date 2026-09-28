package org.jahia.modules.formidable.engine.config.captcha;

import org.jahia.modules.formidable.engine.config.TestConfigs;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaConfigServiceTest {

    private static final Map<String, Object> CONFIGURED = Map.of(
            "captchaSiteKey", "site-key",
            "captchaSecretKey", "secret-key",
            "captchaScriptUrl", "https://captcha.example/api.js",
            "captchaWidgetVar", "captchaWidget",
            "captchaTokenField", "captcha-token",
            "captchaVerifyUrl", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    private static CaptchaConfigService activated(Map<String, Object> values) {
        CaptchaConfigService service = new CaptchaConfigService();
        service.activate(TestConfigs.of(CaptchaConfig.class, values));
        return service;
    }

    @Test
    void theShippedFileAgreesWithTheDefinitionsDefaults() throws Exception {
        TestConfigs.assertShippedFileMatchesDefaults(CaptchaConfig.class, CaptchaConfigService.PID);
    }

    @Test
    void activateFallsBackToDefaultTimeoutsWhenConfiguredValuesAreInvalid() {
        // Verifies timeout hardening: zero or negative configured values fall back to module defaults.
        CaptchaConfigService service = activated(Map.of("captchaHttpConnectTimeoutSeconds", 0L, "captchaHttpRequestTimeoutSeconds", -1L));

        assertEquals(Duration.ofSeconds(ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS), service.getCaptchaHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS), service.getCaptchaHttpRequestTimeout());
    }

    @Test
    void activateExposesConfiguredHttpTimeouts() {
        // Verifies that explicit timeout values are exposed without modification.
        CaptchaConfigService service = activated(Map.of("captchaHttpConnectTimeoutSeconds", 7L, "captchaHttpRequestTimeoutSeconds", 11L));

        assertEquals(Duration.ofSeconds(7), service.getCaptchaHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(11), service.getCaptchaHttpRequestTimeout());
    }

    @Test
    void activateReportsCaptchaConfigurationStateFromCurrentSnapshot() {
        // Verifies the public CAPTCHA configuration flags exposed by the service, and what the render filter reads.
        CaptchaConfigService service = activated(CONFIGURED);

        assertTrue(service.isCaptchaVerificationConfigured());
        assertTrue(service.isCaptchaWidgetConfigured());
        assertEquals("site-key", service.getCaptchaSiteKey());
        assertEquals("https://captcha.example/api.js", service.getCaptchaScriptUrl());
        assertEquals("captchaWidget", service.getCaptchaWidgetVar());
        assertEquals("captcha-token", service.getCaptchaTokenField());
    }

    @Test
    void neitherIsConfiguredAtTheDefaults() {
        // Verifies the fail-safe default: without keys, no verification and no widget — every submission of a
        // captcha form is then refused (FMDB-005), and nothing is injected in the page.
        CaptchaConfigService service = activated(Map.of());

        assertFalse(service.isCaptchaVerificationConfigured());
        assertFalse(service.isCaptchaWidgetConfigured());
    }

    @Test
    void activateRejectsCaptchaVerificationUrlWhenSchemeIsNotHttps() {
        // Verifies the CAPTCHA verification endpoint scheme guard: a non-HTTPS endpoint disables verification,
        // and so do embedded credentials or a missing host — the widget side is untouched.
        for (String url : new String[] {"http://challenges.cloudflare.com/turnstile/v0/siteverify", "https://user:pw@verify.example/x", "https:///x", "::bad"}) {
            Map<String, Object> values = new java.util.HashMap<>(CONFIGURED);
            values.put("captchaVerifyUrl", url);
            CaptchaConfigService service = activated(values);

            assertFalse(service.isCaptchaVerificationConfigured(), url);
            assertTrue(service.isCaptchaWidgetConfigured(), url);
        }
    }

    @Test
    void verifyCaptchaAnswersFalseWithoutACallWhenNotConfiguredOrWithoutAToken() throws Exception {
        // Verifies the two short-circuits of the verification: no configuration, no token — neither reaches the provider.
        assertFalse(activated(Map.of()).verifyCaptcha("token", "203.0.113.10"));
        assertFalse(activated(CONFIGURED).verifyCaptcha(" ", "203.0.113.10"));
    }
}
