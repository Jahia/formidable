package org.jahia.modules.formidable.engine.logic;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which rules the save-time cleanup removes, and that its list of providers is the editor's: a
 * provider the editor offers and the cleanup does not know keeps its empty leftovers forever.
 */
class FormLogicRuleCleanupTest {

    /** An id followed by its config key, as each entry of the editor's PROVIDERS spells them. */
    private static final Pattern EDITOR_ENTRY = Pattern.compile("id: '([^']+)',\\s*configKey: '([^']+)'");

    @Test
    void theProvidersAreTheEditors() throws IOException {
        // Maven runs the tests from the module's directory, where the editor's sources are
        String editor = Files.readString(Path.of("src/javascript/ConditionalLogic/providers.ts"), StandardCharsets.UTF_8);
        Map<String, String> declared = new LinkedHashMap<>();
        Matcher entry = EDITOR_ENTRY.matcher(editor);
        while (entry.find()) {
            declared.put(entry.group(1), entry.group(2));
        }

        assertFalse(declared.isEmpty(), "no provider found in providers.ts: the pattern no longer matches its entries");
        assertEquals(FormLogicRuleCleanup.SHIPPED_PROVIDER_CONFIG_KEYS, declared);
    }

    @Test
    void aBuiltInRuleWithoutAReferenceIsALeftover() {
        assertTrue(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"urlParam\",\"param\":\" \",\"operator\":\"equals\"}")));
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"urlParam\",\"param\":\"utm_source\",\"operator\":\"equals\"}")));
    }

    @Test
    void aRuleOfASourceAnExtensionRegistersIsNeverALeftover() {
        // The core cannot read the configuration of a source it does not ship: even an empty reference stays
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"segment\":\"\",\"operator\":\"exists\"}")));
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"segment\":\" \",\"operator\":\"equals\"}")));
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"segment\":\"returning\",\"operator\":\"equals\"}")));
    }
}
