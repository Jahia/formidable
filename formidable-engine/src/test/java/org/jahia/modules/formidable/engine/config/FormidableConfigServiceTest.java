package org.jahia.modules.formidable.engine.config;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormidableConfigServiceTest {

    @Test
    void activateRejectsForwardTargetsWithEmbeddedCredentials() {
        // Given one valid forward target and one target embedding user credentials in the URL,
        // activation must keep only the valid entry and reject the credential-bearing target.
        FormidableConfigService service = new FormidableConfigService();

        service.activate(new TestFormidableConfig(
                "good|Good|https://api.example.com/forms\n"
                        + "bad-creds|Bad creds|https://user:pass@api.example.com/forms",
                false,
                ""
        ));

        assertEquals(1, service.getForwardTargets().size());
        assertTrue(service.resolveForwardTarget("good").isPresent());
        assertFalse(service.resolveForwardTarget("bad-creds").isPresent());
    }

    @Test
    void activateExposesConfiguredHttpTimeouts() {
        // Given explicit timeout values in the OSGi configuration,
        // activation must expose them through the config service getters.
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

        // Then the CAPTCHA and forward-action timeout values are available as configured durations.
        assertEquals(Duration.ofSeconds(7), service.getCaptchaHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(11), service.getCaptchaHttpRequestTimeout());
        assertEquals(Duration.ofSeconds(13), service.getForwardHttpConnectTimeout());
        assertEquals(Duration.ofSeconds(17), service.getForwardHttpRequestTimeout());
    }

    @Test
    void activateBuildsReusableForwardHttpClientAndRefreshesItOnConfigChange() {
        // Given one activation followed by a modified forward connect timeout,
        // the service must reuse the same forward HttpClient within one config state and rebuild it on re-activation.
        FormidableConfigService service = new FormidableConfigService();
        TestFormidableConfig firstConfig = new TestFormidableConfig("", false, "", 5L, 10L, 5L, 10L);
        TestFormidableConfig secondConfig = new TestFormidableConfig("", false, "", 5L, 10L, 7L, 10L);

        service.activate(firstConfig);
        HttpClient first = service.getForwardHttpClient();
        HttpClient second = service.getForwardHttpClient();
        service.activate(secondConfig);
        HttpClient third = service.getForwardHttpClient();

        // Then repeated reads within the same activation reuse the same client, and a config update replaces it.
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
            this.forwardTargets = forwardTargets;
            this.enableDevForwardTargets = enableDevForwardTargets;
            this.devForwardTargets = devForwardTargets;
            this.captchaHttpConnectTimeoutSeconds = captchaHttpConnectTimeoutSeconds;
            this.captchaHttpRequestTimeoutSeconds = captchaHttpRequestTimeoutSeconds;
            this.forwardHttpConnectTimeoutSeconds = forwardHttpConnectTimeoutSeconds;
            this.forwardHttpRequestTimeoutSeconds = forwardHttpRequestTimeoutSeconds;
        }

        @Override
        public String captchaSiteKey() {
            return "";
        }

        @Override
        public String captchaSecretKey() {
            return "";
        }

        @Override
        public String captchaScriptUrl() {
            return "";
        }

        @Override
        public String captchaWidgetVar() {
            return "";
        }

        @Override
        public String captchaTokenField() {
            return "";
        }

        @Override
        public String captchaVerifyUrl() {
            return "";
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
            return "text/plain";
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return FormidableConfig.class;
        }
    }
}
