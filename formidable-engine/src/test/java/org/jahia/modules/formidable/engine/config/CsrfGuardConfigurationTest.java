package org.jahia.modules.formidable.engine.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfGuardConfigurationTest {

    @Test
    void protectsFormSubmitServletViaUrlPatterns() throws IOException {
        // Verifies the module CSRFGuard config: the authenticated submit servlet
        // must be protected through urlPatterns, not through page-oriented resolvedUrlPatterns.
        String config = readConfig("META-INF/configurations/org.jahia.modules.jahiacsrfguard-formidable-engine.cfg");

        // Expected outcome: the config explicitly protects /modules/formidable-engine/form-submit
        // and does not rely on resolvedUrlPatterns anymore.
        assertTrue(config.contains("urlPatterns = /modules/formidable-engine/form-submit"));
        assertFalse(config.contains("resolvedUrlPatterns"));
    }

    private static String readConfig(String resourcePath) throws IOException {
        try (InputStream input = CsrfGuardConfigurationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(input, "Missing test resource: " + resourcePath);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
