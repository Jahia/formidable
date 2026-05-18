package org.jahia.modules.formidable.engine.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates conditional logic rules server-side to determine field visibility.
 * A field is visible when ALL its rules are satisfied (AND logic).
 * Handles transitive visibility (hidden source → rule fails) and parent container inheritance.
 */
public class ConditionalLogicEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionalLogicEvaluator.class);

    private final Map<String, List<ConditionalLogicRule>> fieldLogicRules;
    private final Map<String, String> fieldNameToNodeId;
    private final Map<String, String> fieldParentContainer;
    private final Map<String, List<String>> submittedValues;

    public ConditionalLogicEvaluator(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> fieldNameToNodeId,
            Map<String, String> fieldParentContainer,
            Map<String, List<String>> submittedValues
    ) {
        this.fieldLogicRules = fieldLogicRules;
        this.fieldNameToNodeId = fieldNameToNodeId;
        this.fieldParentContainer = fieldParentContainer;
        this.submittedValues = submittedValues;
    }

    public boolean isHidden(String fieldName) {
        return isHidden(fieldName, new HashSet<>());
    }

    private boolean isHidden(String fieldName, Set<String> visiting) {
        String parentName = fieldParentContainer.get(fieldName);
        if (parentName != null && isHidden(parentName, visiting)) {
            return true;
        }

        List<ConditionalLogicRule> rules = fieldLogicRules.get(fieldName);
        if (rules == null || rules.isEmpty()) return false;

        if (!visiting.add(fieldName)) return false;

        try {
            for (ConditionalLogicRule rule : rules) {
                if (!evaluateRule(rule, visiting)) {
                    return true;
                }
            }
            return false;
        } finally {
            visiting.remove(fieldName);
        }
    }

    private boolean evaluateRule(ConditionalLogicRule rule, Set<String> visiting) {
        String sourceFieldName = null;
        for (Map.Entry<String, String> entry : fieldNameToNodeId.entrySet()) {
            if (entry.getValue().equals(rule.sourceFieldId())) {
                sourceFieldName = entry.getKey();
                break;
            }
        }
        if (sourceFieldName == null) return false;

        if (isHidden(sourceFieldName, visiting)) return false;

        List<String> values = submittedValues.getOrDefault(sourceFieldName, List.of());

        return switch (rule.operator()) {
            case "in" -> rule.values().stream().anyMatch(values::contains);
            case "notIn" -> !values.isEmpty() && rule.values().stream().noneMatch(values::contains);
            case "isChecked" -> !values.isEmpty() && values.stream().anyMatch(v -> !v.isBlank());
            case "isUnchecked" -> values.isEmpty() || values.stream().allMatch(String::isBlank);
            case "containsAny" -> rule.values().stream().anyMatch(values::contains);
            case "containsAll" -> values.containsAll(rule.values());
            case "before" -> !values.isEmpty() && rule.value() != null
                    && values.get(0).compareTo(rule.value()) < 0;
            case "after" -> !values.isEmpty() && rule.value() != null
                    && values.get(0).compareTo(rule.value()) > 0;
            case "on" -> !values.isEmpty() && rule.value() != null
                    && values.get(0).equals(rule.value());
            case "between" -> !values.isEmpty() && rule.values().size() >= 2
                    && !rule.values().get(0).isEmpty() && !rule.values().get(1).isEmpty()
                    && values.get(0).compareTo(rule.values().get(0)) >= 0
                    && values.get(0).compareTo(rule.values().get(1)) <= 0;
            default -> false;
        };
    }
}

