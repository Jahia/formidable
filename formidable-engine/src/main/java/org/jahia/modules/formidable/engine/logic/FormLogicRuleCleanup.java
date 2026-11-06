package org.jahia.modules.formidable.engine.logic;

import org.json.JSONObject;

import java.util.Map;

/**
 * Save-time cleanup of logic rules that cannot do anything but hide their field:
 * rules whose target was never chosen (the leftovers of an "Add" click). Removed
 * when the logics property is synchronised:
 * <ul>
 *   <li>a field rule without a source field;</li>
 *   <li>a rule of a built-in source of the core whose reference is empty;</li>
 *   <li>a rule of any other source (one an extension module registers) whose
 *       configuration is one string key, the shape every source stores, and that
 *       string is blank.</li>
 * </ul>
 *
 * Deliberately narrow: a rule of another source whose configuration is not one
 * string (a newer module version may store another shape) is never touched, as
 * this version cannot tell it empty. A reference that is filled but invalid is
 * kept as well: it carries intent, the editor shows it in error and the runtime
 * fails closed.
 */
final class FormLogicRuleCleanup {

    /**
     * The config key of each built-in source of the core. It mirrors the built-in
     * providers of the editor, and FormLogicRuleCleanupTest checks this. A rule of
     * one of these is a leftover whenever its own key is blank, missing included.
     */
    static final Map<String, String> SHIPPED_PROVIDER_CONFIG_KEYS = Map.of(
            "jsVariable", "variable",
            "urlParam", "param",
            "cookie", "cookie");

    private FormLogicRuleCleanup() {
    }

    static boolean isTargetlessLeftover(JSONObject rule) {
        String sourceType = rule.optString("sourceType", "");
        if (ConditionalLogicRule.isFieldSourceType(sourceType)) {
            return rule.optString("sourceFieldName", "").isBlank();
        }

        String configKey = SHIPPED_PROVIDER_CONFIG_KEYS.get(sourceType);
        if (configKey != null) {
            return rule.optString(configKey, "").isBlank();
        }

        return hasOneBlankStringConfig(rule);
    }

    private static boolean hasOneBlankStringConfig(JSONObject rule) {
        Object config = null;
        int keys = 0;
        for (String key : rule.keySet()) {
            if (!ConditionalLogicRule.SHARED_RULE_KEYS.contains(key)) {
                keys++;
                config = rule.opt(key);
            }
        }

        return keys == 1 && config instanceof String reference && reference.isBlank();
    }
}
