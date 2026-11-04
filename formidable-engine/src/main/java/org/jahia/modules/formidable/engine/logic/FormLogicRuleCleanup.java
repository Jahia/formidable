package org.jahia.modules.formidable.engine.logic;

import org.json.JSONObject;

import java.util.Map;

/**
 * Save-time cleanup of logic rules that cannot do anything but hide their field:
 * rules whose target was never chosen (the leftovers of an "Add" click). A field
 * rule without a source field, or a rule of a built-in source of the core whose
 * reference is empty, is removed when the logics property is synchronised.
 *
 * Deliberately narrow: a rule of any other source type is never touched. An
 * extension module registers such a source, or a newer module version authored
 * it, and this version cannot read its configuration. A reference that is filled
 * but invalid is kept as well: it carries intent, the editor shows it in error
 * and the runtime fails closed.
 */
final class FormLogicRuleCleanup {

    /**
     * The config key of each built-in source of the core. It mirrors the built-in
     * providers of the editor, and FormLogicRuleCleanupTest checks this. Only these
     * sources are cleanup candidates. A source that an extension module registers is
     * never one: this module cannot tell an empty configuration of that source from
     * one it cannot read.
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
        return configKey != null && rule.optString(configKey, "").isBlank();
    }
}
