package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormsChoicesTest {

    @Test
    void theFormsKeyBecomesTheValueAndTheFormsLabelTheLabel() {
        Map<String, List<String>> options = FormsChoices.options(Map.of(
                "en", "[{\"key\":\"red\",\"value\":\"Red\"},{\"key\":\"green\",\"value\":\"Green\"}]",
                "fr", "[{\"key\":\"red\",\"value\":\"Rouge\"},{\"key\":\"green\",\"value\":\"Vert\"}]"));

        assertEquals(2, options.get("en").size());
        JSONObject red = new JSONObject(options.get("en").get(0));
        assertEquals("red", red.getString("value"));
        assertEquals("Red", red.getString("label"));
        assertFalse(red.getBoolean("selected"));
        assertEquals("Rouge", new JSONObject(options.get("fr").get(0)).getString("label"));
    }

    @Test
    void aLanguageWhoseChoicesDoNotParseIsLeftOut() {
        Map<String, List<String>> options = FormsChoices.options(Map.of("en", "[{\"key\":\"a\"}]", "fr", "not json", "de", ""));
        assertEquals(List.of("en"), List.copyOf(options.keySet()));
        assertEquals("a", new JSONObject(options.get("en").get(0)).getString("label"));
        assertTrue(FormsChoices.options(Map.of()).isEmpty());
    }
}
