package org.jahia.modules.formidable.engine.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final class TestFormidableConfig implements FormidableConfig {
        private final String forwardTargets;
        private final boolean enableDevForwardTargets;
        private final String devForwardTargets;

        private TestFormidableConfig(String forwardTargets, boolean enableDevForwardTargets, String devForwardTargets) {
            this.forwardTargets = forwardTargets;
            this.enableDevForwardTargets = enableDevForwardTargets;
            this.devForwardTargets = devForwardTargets;
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
        public String uploadAllowedMimeTypes() {
            return "text/plain";
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return FormidableConfig.class;
        }
    }
}
