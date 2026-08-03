package org.jahia.modules.formidable.engine.logic;

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

    private final Map<String, List<ConditionalLogicRule>> fieldLogicRules;
    private final Map<String, String> logicIdToFieldName;
    private final Map<String, Set<String>> fieldParentContainers;
    private final Map<String, List<String>> submittedValues;

    public ConditionalLogicEvaluator(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, Set<String>> fieldParentContainers,
            Map<String, List<String>> submittedValues
    ) {
        this.fieldLogicRules = fieldLogicRules;
        this.logicIdToFieldName = logicIdToFieldName;
        this.fieldParentContainers = fieldParentContainers;
        this.submittedValues = submittedValues;
    }

    public boolean isHidden(String fieldName) {
        return isHidden(fieldName, new HashSet<>());
    }

    private boolean isHidden(String fieldName, Set<String> visiting) {
        Set<String> parentNames = fieldParentContainers.get(fieldName);
        if (parentNames != null && !parentNames.isEmpty()) {
            // A field with multiple parent containers (duplicate name across conditional
            // fieldsets) is hidden only when ALL its parents are hidden. If any parent
            // is visible, the field is reachable and its submitted value is valid.
            boolean allParentsHidden = parentNames.stream().allMatch(p -> isHidden(p, visiting));
            if (allParentsHidden) return true;
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
        if (rule.isJsVariable()) {
            // JS variable rules (e.g. datalayer entries) depend on browser-only state
            // (window.* variables) that cannot be verified server-side. Treat them as
            // not satisfied so the field counts as hidden and required validation is
            // skipped, which avoids rejecting legitimate submissions where the field
            // was hidden client-side.
            return false;
        }

        String sourceFieldName = resolveSourceFieldName(rule);
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
            case "between" -> evaluateBetween(rule, values);
            case "eq" -> compareNumbers(rule, values, comparison -> comparison == 0);
            case "neq" -> compareNumbers(rule, values, comparison -> comparison != 0);
            case "lt" -> compareNumbers(rule, values, comparison -> comparison < 0);
            case "lte" -> compareNumbers(rule, values, comparison -> comparison <= 0);
            case "gt" -> compareNumbers(rule, values, comparison -> comparison > 0);
            case "gte" -> compareNumbers(rule, values, comparison -> comparison >= 0);
            // Boolean sources submit their value when on and nothing when off,
            // exactly like a lone checkbox: mirror isChecked / isUnchecked.
            case "isTrue" -> !values.isEmpty() && values.stream().anyMatch(v -> !v.isBlank());
            case "isFalse" -> values.isEmpty() || values.stream().allMatch(String::isBlank);
            default -> false;
        };
    }

    /**
     * 'between' is shared by the date and number value kinds: dates compare as ISO
     * strings, numbers numerically ("9" < "10"). Older rules carry no valueKind and
     * can only be date rules, so string comparison stays their behavior.
     */
    private static boolean evaluateBetween(ConditionalLogicRule rule, List<String> values) {
        if (values.isEmpty() || rule.values().size() < 2
                || rule.values().get(0).isEmpty() || rule.values().get(1).isEmpty()) {
            return false;
        }

        if (ConditionalLogicRule.VALUE_KIND_NUMBER.equals(rule.valueKind())) {
            Integer fromComparison = compareAsNumbers(values.get(0), rule.values().get(0));
            Integer toComparison = compareAsNumbers(values.get(0), rule.values().get(1));
            return fromComparison != null && toComparison != null
                    && fromComparison >= 0 && toComparison <= 0;
        }

        return values.get(0).compareTo(rule.values().get(0)) >= 0
                && values.get(0).compareTo(rule.values().get(1)) <= 0;
    }

    private static boolean compareNumbers(
            ConditionalLogicRule rule, List<String> values, java.util.function.IntPredicate predicate) {
        if (values.isEmpty() || rule.value() == null) {
            return false;
        }

        Integer comparison = compareAsNumbers(values.get(0), rule.value());
        return comparison != null && predicate.test(comparison);
    }

    /** Numeric comparison; null (operator fails safe) when either side is not a number. */
    private static Integer compareAsNumbers(String left, String right) {
        try {
            return Double.compare(Double.parseDouble(left), Double.parseDouble(right));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves the source field name from a rule.
     * Primary: logicId → resolved field name via logicsSrc weakref.
     * Fallback: sourceFieldName from JSON metadata.
     */
    private String resolveSourceFieldName(ConditionalLogicRule rule) {
        String logicId = rule.logicId();
        if (logicId != null && !logicId.isEmpty()) {
            String resolved = logicIdToFieldName.get(logicId);
            if (resolved != null) {
                return resolved;
            }
        }

        String name = rule.sourceFieldName();
        if (name != null && !name.isEmpty() && submittedValues.containsKey(name)) {
            return name;
        }

        return null;
    }
}
