package org.jahia.modules.formidable.engine.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionalLogicEvaluatorTest {

    @Test
    void inOperatorKeepsFieldVisibleWhenAnyConfiguredValueMatches() {
        // Verifies the "in" operator: the field must stay visible when one submitted value matches.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "in", null, List.of("pro", "enterprise")))),
                Map.of("source", List.of("pro"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void inOperatorHidesFieldWhenNoConfiguredValueMatches() {
        // Verifies the "in" operator negative path: the field must be hidden when nothing matches.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "in", null, List.of("pro", "enterprise")))),
                Map.of("source", List.of("starter"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void notInOperatorKeepsFieldVisibleWhenSubmittedValueIsNotForbidden() {
        // Verifies the "notIn" operator: the field must stay visible when the value is outside the forbidden set.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "notIn", null, List.of("blocked")))),
                Map.of("source", List.of("allowed"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void notInOperatorHidesFieldWhenSubmittedValueIsForbidden() {
        // Verifies the "notIn" operator negative path: the field must be hidden when a forbidden value is submitted.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "notIn", null, List.of("blocked")))),
                Map.of("source", List.of("blocked"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void isCheckedOperatorKeepsFieldVisibleWhenCheckboxHasNonBlankValue() {
        // Verifies the "isChecked" operator: any non-blank submitted value means the source is checked.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "isChecked", null, List.of()))),
                Map.of("source", List.of("yes"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void isCheckedOperatorHidesFieldWhenCheckboxHasNoSubmittedValue() {
        // Verifies the "isChecked" negative path: absence of submitted values means the source is unchecked.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "isChecked", null, List.of()))),
                Map.of("source", List.of())
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void isUncheckedOperatorKeepsFieldVisibleWhenCheckboxHasNoSubmittedValue() {
        // Verifies the "isUnchecked" operator: no submitted value means the source is unchecked.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "isUnchecked", null, List.of()))),
                Map.of("source", List.of())
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void isUncheckedOperatorHidesFieldWhenCheckboxHasSubmittedValue() {
        // Verifies the "isUnchecked" negative path: a submitted value means the source is checked.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "isUnchecked", null, List.of()))),
                Map.of("source", List.of("yes"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void containsAnyOperatorKeepsFieldVisibleWhenAtLeastOneConfiguredValueMatches() {
        // Verifies the "containsAny" operator: the field stays visible when at least one configured value is present.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "containsAny", null, List.of("b", "c")))),
                Map.of("source", List.of("a", "b"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void containsAnyOperatorHidesFieldWhenNoConfiguredValueMatches() {
        // Verifies the "containsAny" negative path: the field must be hidden when none of the expected values are present.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "containsAny", null, List.of("b", "c")))),
                Map.of("source", List.of("a"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void containsAllOperatorKeepsFieldVisibleWhenAllConfiguredValuesMatch() {
        // Verifies the "containsAll" operator: the field stays visible when the submitted values contain the full expected set.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "containsAll", null, List.of("a", "b")))),
                Map.of("source", List.of("a", "b", "c"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void containsAllOperatorHidesFieldWhenOneConfiguredValueIsMissing() {
        // Verifies the "containsAll" negative path: the field must be hidden when one expected value is missing.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "containsAll", null, List.of("a", "b", "c")))),
                Map.of("source", List.of("a", "b"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void beforeOperatorKeepsFieldVisibleWhenSubmittedDateIsLowerThanBound() {
        // Verifies the "before" operator: the field stays visible when the submitted date is strictly before the bound.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "before", "2026-06-10", List.of()))),
                Map.of("source", List.of("2026-06-01"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void beforeOperatorHidesFieldWhenSubmittedDateIsNotLowerThanBound() {
        // Verifies the "before" negative path: equality with the bound must not satisfy the rule.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "before", "2026-06-10", List.of()))),
                Map.of("source", List.of("2026-06-10"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void afterOperatorKeepsFieldVisibleWhenSubmittedDateIsGreaterThanBound() {
        // Verifies the "after" operator: the field stays visible when the submitted date is strictly after the bound.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "after", "2026-06-10", List.of()))),
                Map.of("source", List.of("2026-06-20"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void afterOperatorHidesFieldWhenSubmittedDateIsNotGreaterThanBound() {
        // Verifies the "after" negative path: equality with the bound must not satisfy the rule.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "after", "2026-06-10", List.of()))),
                Map.of("source", List.of("2026-06-10"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void onOperatorKeepsFieldVisibleWhenSubmittedDateMatchesExactly() {
        // Verifies the "on" operator: the field stays visible when the submitted date matches exactly.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "on", "2026-06-10", List.of()))),
                Map.of("source", List.of("2026-06-10"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void onOperatorHidesFieldWhenSubmittedDateDoesNotMatchExactly() {
        // Verifies the "on" negative path: a different date must hide the field.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "on", "2026-06-10", List.of()))),
                Map.of("source", List.of("2026-06-11"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void betweenOperatorKeepsFieldVisibleWhenSubmittedDateIsInsideRange() {
        // Verifies the "between" operator: the field stays visible when the submitted date is inside the inclusive range.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "between", null, List.of("2026-06-01", "2026-06-30")))),
                Map.of("source", List.of("2026-06-15"))
        );

        // Expected outcome: the rule is satisfied, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void betweenOperatorHidesFieldWhenSubmittedDateIsOutsideRange() {
        // Verifies the "between" negative path: a value outside the inclusive range must hide the field.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("source", "between", null, List.of("2026-06-01", "2026-06-30")))),
                Map.of("source", List.of("2026-07-01"))
        );

        // Expected outcome: the rule fails, so the field is hidden.
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void fieldBecomesHiddenWhenItsSourceFieldIsTransitivelyHidden() {
        // Verifies transitive visibility: if the source field is itself hidden, downstream rules must fail closed.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of(
                        "source", List.of(rule("gate", "in", null, List.of("open"))),
                        "target", List.of(rule("source", "in", null, List.of("visible")))
                ),
                Map.of(
                        "gate", List.of("closed"),
                        "source", List.of("visible")
                )
        );

        // Expected outcome: both the source and the dependent field are hidden.
        assertTrue(evaluator.isHidden("source"));
        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void fieldBecomesHiddenWhenParentContainerIsHidden() {
        // Verifies container inheritance: a child field must be hidden when its parent container is hidden.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("container", List.of(rule("gate", "in", null, List.of("open")))),
                Map.of("gate", List.of("closed")),
                Map.of("child", "container")
        );

        // Expected outcome: the child inherits the hidden state from its parent container.
        assertTrue(evaluator.isHidden("child"));
    }

    @Test
    void logicIdResolutionUsesResolvedSourceFieldName() {
        // Verifies source resolution: the evaluator must resolve logicId through the pre-built logicId->field map.
        ConditionalLogicEvaluator evaluator = new ConditionalLogicEvaluator(
                Map.of("target", List.of(new ConditionalLogicRule("logic-1", "", "fmdb:select", "in", null, List.of("pro")))),
                Map.of("logic-1", "source"),
                Map.of(),
                Map.of("source", List.of("pro"))
        );

        // Expected outcome: the resolved source field satisfies the rule, so the field is not hidden.
        assertFalse(evaluator.isHidden("target"));
    }

    @Test
    void cyclicDependenciesDoNotCauseInfiniteRecursion() {
        // Verifies recursion safety: mutually dependent rules must not trigger infinite recursion.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of(
                        "fieldA", List.of(rule("fieldB", "in", null, List.of("on"))),
                        "fieldB", List.of(rule("fieldA", "in", null, List.of("on")))
                ),
                Map.of(
                        "fieldA", List.of("on"),
                        "fieldB", List.of("on")
                )
        );

        // Expected outcome: evaluation terminates and the cycle guard prevents the fields from being treated as hidden.
        assertDoesNotThrow(() -> evaluator.isHidden("fieldA"));
        assertFalse(evaluator.isHidden("fieldA"));
    }

    private static ConditionalLogicEvaluator evaluator(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, List<String>> submittedValues
    ) {
        return evaluator(fieldLogicRules, submittedValues, Map.of());
    }

    private static ConditionalLogicEvaluator evaluator(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, List<String>> submittedValues,
            Map<String, String> fieldParentContainer
    ) {
        return new ConditionalLogicEvaluator(fieldLogicRules, Map.of(), fieldParentContainer, submittedValues);
    }

    private static ConditionalLogicRule rule(
            String sourceFieldName,
            String operator,
            String value,
            List<String> values
    ) {
        return new ConditionalLogicRule("", sourceFieldName, "fmdb:select", operator, value, values);
    }
}
