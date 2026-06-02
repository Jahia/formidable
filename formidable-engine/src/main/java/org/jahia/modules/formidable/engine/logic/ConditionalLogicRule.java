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
        String sourceFieldName,
        String sourceFieldType,
        String operator,
        String value,
        List<String> values
) {
    private static final Logger log = LoggerFactory.getLogger(ConditionalLogicRule.class);

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
        String sourceFieldName = obj.optString("sourceFieldName", "");
        String operator = obj.optString("operator", "");
        if (sourceFieldName.isEmpty() || operator.isEmpty()) {
            return null;
        }

        return new ConditionalLogicRule(
                obj.optString("logicId", ""),
                sourceFieldName,
                obj.optString("sourceFieldType", ""),
                operator,
                obj.has("value") ? obj.optString("value", null) : null,
                parseValues(obj)
        );
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
