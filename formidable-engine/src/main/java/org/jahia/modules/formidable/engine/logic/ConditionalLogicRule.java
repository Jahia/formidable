package org.jahia.modules.formidable.engine.logic;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                String json = v.getString();
                if (json == null || json.isBlank()) continue;

                JSONObject obj = new JSONObject(json);
                String sourceFieldName = obj.optString("sourceFieldName", "");
                String operator = obj.optString("operator", "");
                if (sourceFieldName.isEmpty() || operator.isEmpty()) continue;

                String logicId = obj.optString("logicId", "");
                String singleValue = obj.has("value") ? obj.optString("value", null) : null;
                List<String> valuesList = new ArrayList<>();
                if (obj.has("values")) {
                    JSONArray arr = obj.optJSONArray("values");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            valuesList.add(arr.optString(i, ""));
                        }
                    }
                }

                rules.add(new ConditionalLogicRule(
                        logicId,
                        sourceFieldName,
                        obj.optString("sourceFieldType", ""),
                        operator,
                        singleValue,
                        valuesList
                ));
            } catch (Exception e) {
                log.debug("[ConditionalLogicRule] Failed to parse rule: {}", e.getMessage());
            }
        }
        return rules;
    }
}
