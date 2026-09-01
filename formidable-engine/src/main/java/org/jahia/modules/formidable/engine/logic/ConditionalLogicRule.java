package org.jahia.modules.formidable.engine.logic;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed conditional logic rule stored as JSON in the "logics" JCR property.
 * Used server-side to determine if a field should be considered hidden (and thus skip validation).
 */
public record ConditionalLogicRule(
        String logicId,
        String sourceType,
        String sourceFieldName,
        String sourceFieldType,
        String valueKind,
        /**
         * What a provider rule designates (a variable path, a parameter name, a cookie
         * name…): the value of the single config key the rule carries besides the shared
         * keys. Null on field rules, and on provider rules whose config is missing or
         * ambiguous — those can only be evaluated as the fail-safe.
         */
        String providerRef,
        String operator,
        String value,
        List<String> values
) {
    private static final Logger log = LoggerFactory.getLogger(ConditionalLogicRule.class);

    public static final String SOURCE_TYPE_FIELD = "field";
    public static final String VALUE_KIND_NUMBER = "number";
    public static final String VALUE_KIND_DATE = "date";

    /**
     * Sentinel a date rule may carry instead of a fixed date: the submission day.
     * Unambiguous by construction — a date input can never produce this literal.
     */
    public static final String TODAY_SENTINEL = "today";

    /**
     * Rule keys that are not provider configuration. Everything else with a string value
     * is provider config, and a provider rule carries exactly one such key (its configKey,
     * declared by the provider on the editor side). Keeping this an exclusion list is what
     * lets the server stay independent of the set of providers.
     */
    private static final String VALUE_KEY = "value";

    private static final java.util.Set<String> SHARED_RULE_KEYS = java.util.Set.of(
            "logicId", "sourceType", "sourceNodeId", "sourceFieldKey", "sourceFieldName",
            "sourceFieldType", "valueKind", "operator", VALUE_KEY, "values");

    /**
     * Whether this rule designates another form field, the only source the server can
     * evaluate: everything the submission carries is a field value. Any other source type
     * is a client-side provider (a JS variable, a URL parameter, a cookie…) and is handled
     * uniformly, so adding one needs no change here — hence the question is "is this a
     * field rule?" rather than an allowlist of the non-field types we know about.
     * Rules stored before source types existed carry none and are field rules.
     */
    public boolean isFieldRule() {
        return isFieldSourceType(sourceType);
    }

    static boolean isFieldSourceType(String sourceType) {
        return sourceType == null || sourceType.isBlank() || SOURCE_TYPE_FIELD.equals(sourceType);
    }

    /**
     * Whether this rule compares against the submission day. Gated on the value kind,
     * not on an operator list: only the date kind gives the sentinel its meaning (a
     * text rule keeps comparing the literal string, a number rule can never carry it),
     * and the editor stamps the kind on every rule that can carry the sentinel — so a
     * future date operator or value kind opts in or out by its kind alone, identically
     * here and in the browser evaluator.
     */
    public boolean referencesToday() {
        if (!isFieldRule() || !VALUE_KIND_DATE.equals(valueKind)) {
            return false;
        }

        return TODAY_SENTINEL.equals(value) || values.contains(TODAY_SENTINEL);
    }

    /**
     * A copy of this rule with the today sentinel substituted by the given ISO day, so
     * the ordinary operator evaluation (plain string comparison) applies unchanged.
     */
    public ConditionalLogicRule withTodayResolved(String day) {
        return new ConditionalLogicRule(
                logicId,
                sourceType,
                sourceFieldName,
                sourceFieldType,
                valueKind,
                providerRef,
                operator,
                TODAY_SENTINEL.equals(value) ? day : value,
                values.stream().map(v -> TODAY_SENTINEL.equals(v) ? day : v).toList()
        );
    }

    public static List<ConditionalLogicRule> parse(Value[] jcrValues) {
        List<ConditionalLogicRule> rules = new ArrayList<>();
        for (Value v : jcrValues) {
            try {
                ConditionalLogicRule rule = parseRuleValue(v);
                if (rule != null) {
                    rules.add(rule);
                }
            } catch (RepositoryException | RuntimeException e) {
                log.debug("[ConditionalLogicRule] Failed to parse rule: {}", e.getMessage());
            }
        }
        return rules;
    }

    private static ConditionalLogicRule parseRuleValue(Value value) throws RepositoryException {
        String json = value.getString();
        if (json == null || json.isBlank()) {
            return null;
        }

        JSONObject obj = new JSONObject(json);
        String sourceType = obj.optString("sourceType", "");
        String sourceFieldName = obj.optString("sourceFieldName", "");
        String operator = obj.optString("operator", "");
        if (operator.isEmpty()) {
            return null;
        }

        // A field rule is unusable without its source field name. A provider rule's config
        // is not validated here — a missing or ambiguous one only degrades evaluation to
        // the fail-safe — which keeps this parser independent of the set of providers.
        if (isFieldSourceType(sourceType) && sourceFieldName.isEmpty()) {
            return null;
        }

        return new ConditionalLogicRule(
                obj.optString("logicId", ""),
                sourceType,
                sourceFieldName,
                obj.optString("sourceFieldType", ""),
                obj.optString("valueKind", ""),
                isFieldSourceType(sourceType) ? null : extractProviderRef(obj),
                operator,
                obj.has(VALUE_KEY) ? obj.optString(VALUE_KEY, null) : null,
                parseValues(obj)
        );
    }

    /**
     * The single non-shared string entry of a provider rule, or null when there is none
     * or more than one — an ambiguity this server version cannot interpret.
     */
    private static String extractProviderRef(JSONObject obj) {
        String ref = null;
        for (String key : obj.keySet()) {
            if (SHARED_RULE_KEYS.contains(key) || !(obj.opt(key) instanceof String candidateRef) || candidateRef.isBlank()) {
                continue;
            }
            if (ref != null) {
                return null;
            }
            ref = candidateRef.trim();
        }
        return ref;
    }

    private static List<String> parseValues(JSONObject obj) {
        JSONArray valuesArray = obj.optJSONArray("values");
        List<String> values = new ArrayList<>();
        if (valuesArray == null) {
            return values;
        }
        for (int i = 0; i < valuesArray.length(); i++) {
            values.add(valuesArray.optString(i, ""));
        }
        return values;
    }
}
