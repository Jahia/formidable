package org.jahia.modules.formidable.engine.logic;

import org.json.JSONObject;

import java.util.Map;

/**
 * Save-time cleanup of logic rules that cannot do anything but hide their field:
 * rules whose target was never chosen (the leftovers of an "Add" click). A field
 * rule without a source field, or a rule of one of the providers this module
 * version ships whose reference is empty, is removed when the logics property is
 * synchronised.
 *
 * Deliberately narrow: a rule with an unknown source type — authored by a newer
 * module version, whose configuration this version cannot read — is never
 * touched, and a reference that is filled but invalid is kept as well: it
 * carries intent, the editor shows it in error and the runtime fails closed.
 */
final class FormLogicRuleCleanup {

    /**
     * The config key of each provider shipped with this module version (mirrors
     * the editor-side provider registry). Only these are cleanup candidates: for
     * any other source type this version cannot tell an empty configuration from
     * one it simply cannot read.
     */
    private static final Map<String, String> SHIPPED_PROVIDER_CONFIG_KEYS = Map.of(
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
        return configKey != null && rule.optString(configKey, "").isBlank();
    }
}
