package org.jahia.modules.formidable.engine.config.common;

import org.jahia.modules.formidable.engine.config.formactions.FormActionsConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigurationAttributesTest {

    @Test
    void theDefaultsOfADefinitionAreItsAttributesAsText() {
        // Verifies what the migration compares against: every attribute of the definition, by its metatype id, at
        // the annotation's default rendered as the .cfg file renders it.
        Map<String, String> defaults = ConfigurationAttributes.defaultsOf(FormActionsConfig.class);

        assertEquals(Map.of(
                "enableDevForwardTargets", "false",
                "forwardHttpConnectTimeoutSeconds", "5",
                "forwardHttpRequestTimeoutSeconds", "10"), defaults);
    }

    @Test
    void attributeIdsFollowTheMetatypeNameMangling() {
        assertEquals("forwardTargets", ConfigurationAttributes.attributeId("forwardTargets"));
        assertEquals("my.setting", ConfigurationAttributes.attributeId("my_setting"));
        assertEquals("my_setting", ConfigurationAttributes.attributeId("my__setting"));
        assertEquals("my-setting", ConfigurationAttributes.attributeId("my$_$setting"));
        assertEquals("my$setting", ConfigurationAttributes.attributeId("my$$setting"));
        assertEquals("mysetting", ConfigurationAttributes.attributeId("my$setting"));
    }

    @Test
    void multiValuedSettingsCompareAndWriteByTheirElements() {
        assertEquals(ConfigurationAttributes.asText(new String[] {"a", "b"}), ConfigurationAttributes.asText(new String[] {"a", "b"}));
        assertNotEquals(ConfigurationAttributes.asText(new String[] {"a"}), ConfigurationAttributes.asText(new String[] {"b"}));
        assertArrayEquals(new String[] {"1", "2"}, (String[]) ConfigurationAttributes.asStrings(new Object[] {1L, 2L}));
        assertEquals("7", ConfigurationAttributes.asStrings(7L));
        assertFalse(ConfigurationAttributes.asText(7L).isEmpty());
    }

    @Test
    void theDefaultStringsAreTheDefinitionsAttributesAsWrittenIntoAConfiguration() {
        // Verifies the values the completion writes are strings, the form a .cfg file reads back (a typed value would
        // be persisted in fileinstall's typed syntax).
        assertEquals(Map.of("enableDevForwardTargets", "false", "forwardHttpConnectTimeoutSeconds", "5",
                "forwardHttpRequestTimeoutSeconds", "10"), ConfigurationAttributes.defaultStringsOf(FormActionsConfig.class));
    }
}
