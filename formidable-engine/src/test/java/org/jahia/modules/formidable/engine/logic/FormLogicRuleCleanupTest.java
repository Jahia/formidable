package org.jahia.modules.formidable.engine.logic;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
    void theSharedRuleKeysAreTheLibrarys() throws IOException {
        // The page and the editor take the one string key outside this list as a rule's reference, as the server does:
        // a key shared on one side only makes the two judge a field differently
        String library = Files.readString(Path.of("../packages/formidable/src/logicSources.ts"), StandardCharsets.UTF_8);
        Matcher list = Pattern.compile("SHARED_RULE_KEYS: readonly string\\[] = \\[([^\\]]*)]").matcher(library);
        assertTrue(list.find(), "no SHARED_RULE_KEYS found in logicSources.ts: the pattern no longer matches it");
        Set<String> declared = new LinkedHashSet<>();
        Matcher key = Pattern.compile("\"([^\"]+)\"").matcher(list.group(1));
        while (key.find()) {
            declared.add(key.group(1));
        }

        assertEquals(ConditionalLogicRule.SHARED_RULE_KEYS, declared);
    }

    @Test
    void aBuiltInRuleWithoutAReferenceIsALeftover() {
        assertTrue(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"urlParam\",\"param\":\" \",\"operator\":\"equals\"}")));
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"urlParam\",\"param\":\"utm_source\",\"operator\":\"equals\"}")));
    }

    @Test
    void aRuleOfASourceAnExtensionRegistersIsALeftoverWhenItsOneReferenceIsBlank() {
        // An extension's rule stores its reference under one string key, as every source does
        assertTrue(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"segment\":\"\",\"operator\":\"exists\"}")));
        assertTrue(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"logicId\":\"l1\",\"sourceType\":\"acme:segment\",\"segment\":\" \",\"operator\":\"equals\",\"value\":\"\"}")));
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"segment\":\"returning\",\"operator\":\"equals\"}")));
    }

    @Test
    void aRuleOfAnotherSourceWhoseConfigurationIsNotOneStringIsKept() {
        // A newer module version may store another shape: this version cannot tell it empty
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"operator\":\"exists\"}")));
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"segment\":{\"name\":\"\"},\"operator\":\"exists\"}")));
        assertFalse(FormLogicRuleCleanup.isTargetlessLeftover(
                new JSONObject("{\"sourceType\":\"acme:segment\",\"segment\":\"\",\"scope\":\"\",\"operator\":\"exists\"}")));
    }
}
