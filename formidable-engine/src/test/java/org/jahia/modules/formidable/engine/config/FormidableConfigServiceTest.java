package org.jahia.modules.formidable.engine.config;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormidableConfigServiceTest {

    @Test
    void activateAcceptsValidHttpsForwardTarget() {
        // Verifies the standard target happy path: a valid HTTPS target is kept and resolved by id.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "good|Good|https://api.example.com/forms",
                false,
                ""
        ));

        // Expected outcome: the valid HTTPS target is exposed through the service.
        assertEquals(1, service.getForwardTargets().size());
        assertTrue(service.resolveForwardTarget("good").isPresent());
        assertEquals("https://api.example.com/forms", service.resolveForwardTarget("good").orElseThrow().uri().toString());
    }

    @Test
    void activateRejectsForwardTargetsWithEmbeddedCredentials() {
        // Verifies hardening against credential-bearing forward target URLs.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "good|Good|https://api.example.com/forms\n"
                        + "bad-creds|Bad creds|https://user:pass@api.example.com/forms",
                false,
                ""
        ));

        // Expected outcome: only the credential-free HTTPS target is kept.
        assertEquals(1, service.getForwardTargets().size());
        assertTrue(service.resolveForwardTarget("good").isPresent());
        assertFalse(service.resolveForwardTarget("bad-creds").isPresent());
    }

    @Test
    void activateRejectsStandardForwardTargetUsingHttp() {
        // Verifies the standard target scheme guard: non-development targets must use HTTPS.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "bad-http|Bad HTTP|http://api.example.com/forms",
                false,
                ""
        ));

        // Expected outcome: the HTTP target is skipped.
        assertTrue(service.getForwardTargets().isEmpty());
        assertFalse(service.resolveForwardTarget("bad-http").isPresent());
    }

    @Test
    void activateExposesDevelopmentTargetWhenExplicitlyEnabled() {
        // Verifies the development-target happy path for localhost.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "",
                true,
                "local|Local|http://localhost:8081/hook"
        ));

        // Expected outcome: the localhost dev target is exposed when dev targets are enabled.
        assertEquals(1, service.getForwardTargets().size());
        assertTrue(service.resolveForwardTarget("local").isPresent());
        assertTrue(service.resolveForwardTarget("local").orElseThrow().development());
    }

    @Test
    void activateRejectsDevelopmentTargetWhenHostIsNotAllowed() {
        // Verifies the development-target host allowlist.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "",
                true,
                "bad-dev|Bad Dev|http://example.com/hook"
        ));

        // Expected outcome: non-local development targets are skipped.
        assertTrue(service.getForwardTargets().isEmpty());
        assertFalse(service.resolveForwardTarget("bad-dev").isPresent());
    }

    @Test
    void activateIgnoresDevelopmentTargetsWhenDisabled() {
        // Verifies the disabled-dev-targets path.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "",
                false,
                "local|Local|http://localhost:8081/hook"
        ));

        // Expected outcome: dev targets are ignored completely when the feature flag is false.
        assertTrue(service.getForwardTargets().isEmpty());
        assertFalse(service.resolveForwardTarget("local").isPresent());
    }

    @Test
    void activateKeepsStandardTargetWhenDevelopmentTargetUsesSameId() {
        // Verifies duplicate-id precedence across standard and development target registries.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "shared|Standard|https://api.example.com/forms",
                true,
                "shared|Dev|http://localhost:8081/hook"
        ));

        // Expected outcome: the standard target wins and remains the resolved entry.
        assertEquals(1, service.getForwardTargets().size());
        assertEquals("https://api.example.com/forms", service.resolveForwardTarget("shared").orElseThrow().uri().toString());
        assertFalse(service.resolveForwardTarget("shared").orElseThrow().development());
    }

    @Test
    void activateFallsBackToDefaultTimeoutsWhenConfiguredValuesAreInvalid() {
        // Verifies timeout hardening: zero or negative configured values fall back to module defaults.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "",
                false,
                "",
                0L,
                -1L,
                0L,
                -1L
        ));

        // Expected outcome: invalid timeout values are replaced with the documented defaults.
        assertEquals(Duration.ofSeconds(FormidableConfig.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS), service.getCaptchaHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(FormidableConfig.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS), service.getCaptchaHttpRequestTimeout());
        assertEquals(Duration.ofSeconds(FormidableConfig.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS), service.getForwardHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(FormidableConfig.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS), service.getForwardHttpRequestTimeout());
    }

    @Test
    void activateExposesConfiguredHttpTimeouts() {
        // Verifies that explicit timeout values are exposed without modification.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "",
                false,
                "",
                7L,
                11L,
                13L,
                17L
        ));

        // Expected outcome: the service returns the configured durations for CAPTCHA and forward requests.
        assertEquals(Duration.ofSeconds(7), service.getCaptchaHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(11), service.getCaptchaHttpRequestTimeout());
        assertEquals(Duration.ofSeconds(13), service.getForwardHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(17), service.getForwardHttpRequestTimeout());
    }

    @Test
    void activateParsesAndTrimsConfiguredUploadMimeTypes() {
        // Verifies parsing of the fallback upload MIME allowlist.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "",
                false,
                "",
                5L,
                10L,
                5L,
                10L,
                " text/plain , application/pdf ,, image/png "
        ));

        // Expected outcome: blank entries are removed and remaining MIME types are trimmed.
        assertEquals(Set.of("text/plain", "application/pdf", "image/png"), service.getUploadAllowedMimeTypes());
    }

    @Test
    void activateReportsCaptchaConfigurationStateFromCurrentSnapshot() {
        // Verifies the public CAPTCHA configuration flags exposed by the service.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "",
                false,
                "",
                5L,
                10L,
                5L,
                10L,
                "text/plain",
                "site-key",
                "secret-key",
                "https://captcha.example/api.js",
                "captchaWidget",
                "captcha-token",
                "https://captcha.example/siteverify"
        ));

        // Expected outcome: both verification and widget configuration are reported as complete.
        assertTrue(service.isCaptchaVerificationConfigured());
        assertTrue(service.isCaptchaWidgetConfigured());
    }

    @Test
    void activateBuildsReusableForwardHttpClientAndRefreshesItOnConfigChange() {
        // Verifies HttpClient reuse within one config snapshot and refresh after re-activation.
        FormidableConfigService service = new FormidableConfigService();
        TestFormidableConfig firstConfig = new TestFormidableConfig("", false, "", 5L, 10L, 5L, 10L);
        TestFormidableConfig secondConfig = new TestFormidableConfig("", false, "", 5L, 10L, 7L, 10L);

        service.activate(firstConfig);
        HttpClient first = service.getForwardHttpClient();
        HttpClient second = service.getForwardHttpClient();
        service.activate(secondConfig);
        HttpClient third = service.getForwardHttpClient();

        // Expected outcome: repeated reads reuse the same client, and a config change replaces it.
        assertSame(first, second);
        assertNotSame(second, third);
    }

    private static final class TestFormidableConfig implements FormidableConfig {
        private final String forwardTargets;
        private final boolean enableDevForwardTargets;
        private final String devForwardTargets;
        private final long captchaHttpConnectTimeoutSeconds;
        private final long captchaHttpRequestTimeoutSeconds;
        private final long forwardHttpConnectTimeoutSeconds;
        private final long forwardHttpRequestTimeoutSeconds;
        private final String uploadAllowedMimeTypes;
        private final String captchaSiteKey;
        private final String captchaSecretKey;
        private final String captchaScriptUrl;
        private final String captchaWidgetVar;
        private final String captchaTokenField;
        private final String captchaVerifyUrl;

        private TestFormidableConfig(String forwardTargets, boolean enableDevForwardTargets, String devForwardTargets) {
            this(forwardTargets, enableDevForwardTargets, devForwardTargets, 5L, 10L, 5L, 10L);
        }

        private TestFormidableConfig(
                String forwardTargets,
                boolean enableDevForwardTargets,
                String devForwardTargets,
                long captchaHttpConnectTimeoutSeconds,
                long captchaHttpRequestTimeoutSeconds,
                long forwardHttpConnectTimeoutSeconds,
                long forwardHttpRequestTimeoutSeconds
        ) {
            this(
                    forwardTargets,
                    enableDevForwardTargets,
                    devForwardTargets,
                    captchaHttpConnectTimeoutSeconds,
                    captchaHttpRequestTimeoutSeconds,
                    forwardHttpConnectTimeoutSeconds,
                    forwardHttpRequestTimeoutSeconds,
                    "text/plain",
                    "",
                    "",
                    "",
                    "",
                    "",
                    ""
            );
        }

        private TestFormidableConfig(
                String forwardTargets,
                boolean enableDevForwardTargets,
                String devForwardTargets,
                long captchaHttpConnectTimeoutSeconds,
                long captchaHttpRequestTimeoutSeconds,
                long forwardHttpConnectTimeoutSeconds,
                long forwardHttpRequestTimeoutSeconds,
                String uploadAllowedMimeTypes
        ) {
            this(
                    forwardTargets,
                    enableDevForwardTargets,
                    devForwardTargets,
                    captchaHttpConnectTimeoutSeconds,
                    captchaHttpRequestTimeoutSeconds,
                    forwardHttpConnectTimeoutSeconds,
                    forwardHttpRequestTimeoutSeconds,
                    uploadAllowedMimeTypes,
                    "",
                    "",
                    "",
                    "",
                    "",
                    ""
            );
        }

        private TestFormidableConfig(
                String forwardTargets,
                boolean enableDevForwardTargets,
                String devForwardTargets,
                long captchaHttpConnectTimeoutSeconds,
                long captchaHttpRequestTimeoutSeconds,
                long forwardHttpConnectTimeoutSeconds,
                long forwardHttpRequestTimeoutSeconds,
                String uploadAllowedMimeTypes,
                String captchaSiteKey,
                String captchaSecretKey,
                String captchaScriptUrl,
                String captchaWidgetVar,
                String captchaTokenField,
                String captchaVerifyUrl
        ) {
            this.forwardTargets = forwardTargets;
            this.enableDevForwardTargets = enableDevForwardTargets;
            this.devForwardTargets = devForwardTargets;
            this.captchaHttpConnectTimeoutSeconds = captchaHttpConnectTimeoutSeconds;
            this.captchaHttpRequestTimeoutSeconds = captchaHttpRequestTimeoutSeconds;
            this.forwardHttpConnectTimeoutSeconds = forwardHttpConnectTimeoutSeconds;
            this.forwardHttpRequestTimeoutSeconds = forwardHttpRequestTimeoutSeconds;
            this.uploadAllowedMimeTypes = uploadAllowedMimeTypes;
            this.captchaSiteKey = captchaSiteKey;
            this.captchaSecretKey = captchaSecretKey;
            this.captchaScriptUrl = captchaScriptUrl;
            this.captchaWidgetVar = captchaWidgetVar;
            this.captchaTokenField = captchaTokenField;
            this.captchaVerifyUrl = captchaVerifyUrl;
        }

        @Override
        public String captchaSiteKey() {
            return captchaSiteKey;
        }

        @Override
        public String captchaSecretKey() {
            return captchaSecretKey;
        }

        @Override
        public String captchaScriptUrl() {
            return captchaScriptUrl;
        }

        @Override
        public String captchaWidgetVar() {
            return captchaWidgetVar;
        }

        @Override
        public String captchaTokenField() {
            return captchaTokenField;
        }

        @Override
        public String captchaVerifyUrl() {
            return captchaVerifyUrl;
        }

        @Override
        public long captchaHttpConnectTimeoutSeconds() {
            return captchaHttpConnectTimeoutSeconds;
        }

        @Override
        public long captchaHttpRequestTimeoutSeconds() {
            return captchaHttpRequestTimeoutSeconds;
        }

        @Override
        public long uploadMaxFileSizeBytes() {
            return 10_485_760L;
        }

        @Override
        public long uploadMaxRequestSizeBytes() {
            return 52_428_800L;
        }

        @Override
        public int uploadMaxFileCount() {
            return 10;
        }

        @Override
        public String forwardTargets() {
            return forwardTargets;
        }

        @Override
        public boolean enableDevForwardTargets() {
            return enableDevForwardTargets;
        }

        @Override
        public String devForwardTargets() {
            return devForwardTargets;
        }

        @Override
        public long forwardHttpConnectTimeoutSeconds() {
            return forwardHttpConnectTimeoutSeconds;
        }

        @Override
        public long forwardHttpRequestTimeoutSeconds() {
            return forwardHttpRequestTimeoutSeconds;
        }

        @Override
        public String uploadAllowedMimeTypes() {
            return uploadAllowedMimeTypes;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return FormidableConfig.class;
        }
    }
}
