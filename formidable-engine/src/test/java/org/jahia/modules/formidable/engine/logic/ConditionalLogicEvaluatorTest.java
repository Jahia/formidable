package org.jahia.modules.formidable.engine.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                Map.of("child", Set.of("container"))
        );

        // Expected outcome: the child inherits the hidden state from its parent container.
        assertTrue(evaluator.isHidden("child"));
    }

    @Test
    void logicIdResolutionUsesResolvedSourceFieldName() {
        // Verifies source resolution: the evaluator must resolve logicId through the pre-built logicId->field map.
        ConditionalLogicEvaluator evaluator = new ConditionalLogicEvaluator(
                Map.of("target", List.of(new ConditionalLogicRule("logic-1", "", "", "fmdb:select", "", "", "in", null, List.of("pro")))),
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
            Map<String, Set<String>> fieldParentContainers
    ) {
        return new ConditionalLogicEvaluator(fieldLogicRules, Map.of(), fieldParentContainers, submittedValues);
    }

    private static ConditionalLogicRule rule(
            String sourceFieldName,
            String operator,
            String value,
            List<String> values
    ) {
        return rule(sourceFieldName, "", operator, value, values);
    }

    private static ConditionalLogicRule rule(
            String sourceFieldName,
            String valueKind,
            String operator,
            String value,
            List<String> values
    ) {
        return new ConditionalLogicRule("", "", sourceFieldName, "fmdb:select", valueKind, "", operator, value, values);
    }

    @Test
    void numberOperatorsCompareNumerically() {
        // Verifies the number value kind: "9" is less than "10" numerically even
        // though it is greater lexicographically.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of(
                        "ltTarget", List.of(rule("score", "number", "lt", "10", List.of())),
                        "gteTarget", List.of(rule("score", "number", "gte", "9", List.of())),
                        "eqTarget", List.of(rule("score", "number", "eq", "9.0", List.of())),
                        "neqTarget", List.of(rule("score", "number", "neq", "9", List.of()))
                ),
                Map.of("score", List.of("9"))
        );

        assertFalse(evaluator.isHidden("ltTarget"));
        assertFalse(evaluator.isHidden("gteTarget"));
        assertFalse(evaluator.isHidden("eqTarget"));
        assertTrue(evaluator.isHidden("neqTarget"));
    }

    @Test
    void numberBetweenComparesNumericallyWhileDateBetweenStaysLexicographic() {
        // Verifies the shared 'between' operator: the number kind compares numerically
        // ("9" is between "5" and "10"), while rules without a valueKind keep the
        // historical string comparison used by date rules.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of(
                        "numberTarget", List.of(rule("score", "number", "between", null, List.of("5", "10"))),
                        "legacyTarget", List.of(rule("score", "", "between", null, List.of("5", "10")))
                ),
                Map.of("score", List.of("9"))
        );

        assertFalse(evaluator.isHidden("numberTarget"));
        // Lexicographically "9" > "10", so the legacy comparison fails the rule.
        assertTrue(evaluator.isHidden("legacyTarget"));
    }

    @Test
    void nonNumericValuesFailNumberOperatorsSafely() {
        // Verifies fail-safe behavior: a non-numeric submitted value satisfies no
        // number operator, so the target counts as hidden.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(rule("score", "number", "lt", "10", List.of()))),
                Map.of("score", List.of("abc"))
        );

        assertTrue(evaluator.isHidden("target"));
    }

    @Test
    void booleanOperatorsMirrorSubmittedPresence() {
        // Verifies the boolean value kind: like a lone checkbox, an on switch submits
        // its value and an off switch submits nothing.
        ConditionalLogicEvaluator onEvaluator = evaluator(
                Map.of(
                        "shownWhenOn", List.of(rule("switch", "boolean", "isTrue", null, List.of())),
                        "shownWhenOff", List.of(rule("switch", "boolean", "isFalse", null, List.of()))
                ),
                Map.of("switch", List.of("true"))
        );

        assertFalse(onEvaluator.isHidden("shownWhenOn"));
        assertTrue(onEvaluator.isHidden("shownWhenOff"));

        ConditionalLogicEvaluator offEvaluator = evaluator(
                Map.of(
                        "shownWhenOn", List.of(rule("switch", "boolean", "isTrue", null, List.of())),
                        "shownWhenOff", List.of(rule("switch", "boolean", "isFalse", null, List.of()))
                ),
                Map.of("switch", List.of())
        );

        assertTrue(offEvaluator.isHidden("shownWhenOn"));
        assertFalse(offEvaluator.isHidden("shownWhenOff"));
    }

    @Test
    void booleanOperatorsTreatExplicitFalseAsOff() {
        // A yes/no button pair (switch buttons mode) submits an explicit "false" for the
        // off state: an answered "no" must not satisfy isTrue.
        ConditionalLogicEvaluator explicitNoEvaluator = evaluator(
                Map.of(
                        "shownWhenOn", List.of(rule("switch", "boolean", "isTrue", null, List.of())),
                        "shownWhenOff", List.of(rule("switch", "boolean", "isFalse", null, List.of()))
                ),
                Map.of("switch", List.of("FALSE"))
        );

        assertTrue(explicitNoEvaluator.isHidden("shownWhenOn"));
        assertFalse(explicitNoEvaluator.isHidden("shownWhenOff"));

        // A plain checkbox without a value attribute submits the browser default "on".
        ConditionalLogicEvaluator defaultValueEvaluator = evaluator(
                Map.of("shownWhenOn", List.of(rule("switch", "boolean", "isTrue", null, List.of()))),
                Map.of("switch", List.of("on"))
        );

        assertFalse(defaultValueEvaluator.isHidden("shownWhenOn"));
    }

    @Test
    void textEmptinessOperatorsTreatBlankSubmittedValueAsEmpty() {
        // Verifies the text value kind: an empty text input still submits "" (unlike
        // an off checkbox which submits nothing), so emptiness is whitespace-blankness.
        ConditionalLogicEvaluator blankEvaluator = evaluator(
                Map.of(
                        "shownWhenFilled", List.of(rule("comment", "text", "isNotEmpty", null, List.of())),
                        "shownWhenEmpty", List.of(rule("comment", "text", "isEmpty", null, List.of()))
                ),
                Map.of("comment", List.of("   "))
        );

        assertTrue(blankEvaluator.isHidden("shownWhenFilled"));
        assertFalse(blankEvaluator.isHidden("shownWhenEmpty"));

        ConditionalLogicEvaluator filledEvaluator = evaluator(
                Map.of(
                        "shownWhenFilled", List.of(rule("comment", "text", "isNotEmpty", null, List.of())),
                        "shownWhenEmpty", List.of(rule("comment", "text", "isEmpty", null, List.of()))
                ),
                Map.of("comment", List.of("hello"))
        );

        assertFalse(filledEvaluator.isHidden("shownWhenFilled"));
        assertTrue(filledEvaluator.isHidden("shownWhenEmpty"));
    }

    @Test
    void textEqualsAndContainsCompareSubmittedText() {
        // Verifies the text comparison operators: equals is an exact match on the
        // raw submitted value, contains is a substring match.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of(
                        "equalsTarget", List.of(rule("comment", "text", "equals", "hello world", List.of())),
                        "equalsMismatch", List.of(rule("comment", "text", "equals", "hello", List.of())),
                        "containsTarget", List.of(rule("comment", "text", "contains", "lo wo", List.of())),
                        "containsMismatch", List.of(rule("comment", "text", "contains", "goodbye", List.of()))
                ),
                Map.of("comment", List.of("hello world"))
        );

        assertFalse(evaluator.isHidden("equalsTarget"));
        assertTrue(evaluator.isHidden("equalsMismatch"));
        assertFalse(evaluator.isHidden("containsTarget"));
        assertTrue(evaluator.isHidden("containsMismatch"));
    }

    @Test
    void textComparisonsRequireANonEmptyExpectedValue() {
        // An empty expected value never satisfies equals/contains: the browser
        // evaluator sees no value at all for an empty input while the server sees "",
        // so allowing "" would make the two sides disagree (isEmpty covers that case).
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of(
                        "equalsTarget", List.of(rule("comment", "text", "equals", "", List.of())),
                        "containsTarget", List.of(rule("comment", "text", "contains", "", List.of()))
                ),
                Map.of("comment", List.of(""))
        );

        assertTrue(evaluator.isHidden("equalsTarget"));
        assertTrue(evaluator.isHidden("containsTarget"));
    }

    @Test
    void jsVariableRulesAreTreatedAsHiddenServerSide() {
        // Verifies fail-safe behavior: jsVariable rules depend on browser-only state,
        // so the field must count as hidden and skip required validation.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(new ConditionalLogicRule(
                        "logic-1", "jsVariable", "", "", "", "window.cxs.profileProperties.firstName",
                        "equals", "John", List.of()))),
                Map.of()
        );

        assertTrue(evaluator.isHidden("target"));
    }

    // --- Declared provider state: submit-time coherence declaration ---

    private static ConditionalLogicRule providerRule(String sourceType, String ref, String operator, String value) {
        return new ConditionalLogicRule("logic-p", sourceType, "", "", "", ref, operator, value, List.of());
    }

    private static ConditionalLogicEvaluator evaluatorWithDeclaration(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, List<String>> submittedValues,
            String declarationJson
    ) {
        return new ConditionalLogicEvaluator(
                fieldLogicRules, Map.of(), Map.of(), submittedValues,
                LogicStateDeclaration.parse(declarationJson));
    }

    @Test
    void undeclaredProviderRuleIsFailsafeHiddenNotMeasured() {
        // Without a declaration the verdict stays a fail-safe: required validation is
        // skipped, but nothing may act on the verdict (the field may well have been
        // legitimately visible and filled in the browser).
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(providerRule("cookie", "consent", "exists", null))),
                Map.of()
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE, evaluator.visibility("target"));
    }

    @Test
    void declaredProviderStateSatisfyingTheRuleMakesTheFieldVisible() {
        // A declared value satisfying the rule turns the historical "always hidden" into
        // visible — which also re-arms required validation for provider-gated fields.
        ConditionalLogicEvaluator evaluator = evaluatorWithDeclaration(
                Map.of("target", List.of(providerRule("cookie", "consent", "equals", "yes"))),
                Map.of(),
                "{\"v\":1,\"providers\":{\"cookie\":{\"consent\":\"yes\"}}}"
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.VISIBLE, evaluator.visibility("target"));
    }

    @Test
    void declaredProviderStateFailingTheRuleIsMeasuredHidden() {
        ConditionalLogicEvaluator evaluator = evaluatorWithDeclaration(
                Map.of("target", List.of(providerRule("urlParam", "promo", "equals", "spring"))),
                Map.of(),
                "{\"v\":1,\"providers\":{\"urlParam\":{\"promo\":\"winter\"}}}"
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, evaluator.visibility("target"));
    }

    @Test
    void declaredAbsenceIsAMeasurementToo() {
        // null in the declaration means "read and absent" — distinct from undeclared.
        ConditionalLogicEvaluator evaluator = evaluatorWithDeclaration(
                Map.of(
                        "shownWhenAbsent", List.of(providerRule("cookie", "consent", "notExists", null)),
                        "shownWhenPresent", List.of(providerRule("cookie", "consent", "exists", null))
                ),
                Map.of(),
                "{\"v\":1,\"providers\":{\"cookie\":{\"consent\":null}}}"
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.VISIBLE, evaluator.visibility("shownWhenAbsent"));
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, evaluator.visibility("shownWhenPresent"));
    }

    @Test
    void oneDeclarationBacksEveryRuleReadingTheSameReference() {
        // The point of a single declared state: complementary conditions can no longer
        // both fail. Whatever is declared, exactly one of these two fields is visible.
        Map<String, List<ConditionalLogicRule>> rules = Map.of(
                "whenGold", List.of(providerRule("jsVariable", "window.cxs.tier", "equals", "gold")),
                "whenNotGold", List.of(providerRule("jsVariable", "window.cxs.tier", "notEquals", "gold"))
        );

        ConditionalLogicEvaluator evaluator = evaluatorWithDeclaration(rules, Map.of(),
                "{\"v\":1,\"providers\":{\"jsVariable\":{\"window.cxs.tier\":\"gold\"}}}");

        assertEquals(ConditionalLogicEvaluator.Visibility.VISIBLE, evaluator.visibility("whenGold"));
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, evaluator.visibility("whenNotGold"));
    }

    @Test
    void unsupportedOrMalformedDeclarationDegradesToFailsafe() {
        Map<String, List<ConditionalLogicRule>> rules =
                Map.of("target", List.of(providerRule("cookie", "consent", "exists", null)));

        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE,
                evaluatorWithDeclaration(rules, Map.of(), "{\"v\":2,\"providers\":{\"cookie\":{\"consent\":\"yes\"}}}")
                        .visibility("target"));
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE,
                evaluatorWithDeclaration(rules, Map.of(), "not json at all").visibility("target"));
    }

    @Test
    void unknownOperatorOnADeclaredProviderRuleStaysFailsafe() {
        // A declared state does not make an unknown operator evaluable.
        ConditionalLogicEvaluator evaluator = evaluatorWithDeclaration(
                Map.of("target", List.of(providerRule("cookie", "consent", "matches", "y.*"))),
                Map.of(),
                "{\"v\":1,\"providers\":{\"cookie\":{\"consent\":\"yes\"}}}"
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE, evaluator.visibility("target"));
    }

    @Test
    void oneMeasuredFailureProvesTheVerdictWhateverTheOtherRules() {
        // ANDed rules: a failing measured field rule alone justifies the hidden verdict,
        // even when a provider rule on the same field fell back to the fail-safe.
        ConditionalLogicEvaluator evaluator = evaluator(
                Map.of("target", List.of(
                        rule("plan", "in", null, List.of("pro")),
                        providerRule("cookie", "consent", "exists", null))),
                Map.of("plan", List.of("starter"))
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, evaluator.visibility("target"));
    }

    @Test
    void parentContainerProvenancePropagatesToChildren() {
        // A child is only as provably hidden as its parent: a measured parent verdict
        // stays measured, a fail-safe parent verdict degrades the child's.
        ConditionalLogicEvaluator measuredParent = evaluator(
                Map.of("container", List.of(rule("gate", "in", null, List.of("open")))),
                Map.of("gate", List.of("closed")),
                Map.of("child", Set.of("container"))
        );
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, measuredParent.visibility("child"));

        ConditionalLogicEvaluator failsafeParent = evaluator(
                Map.of("container", List.of(providerRule("cookie", "consent", "exists", null))),
                Map.of(),
                Map.of("child", Set.of("container"))
        );
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE, failsafeParent.visibility("child"));
    }

    @Test
    void providerRuleWithoutReferenceIsFailsafeEvenWhenDeclared() {
        // No providerRef (missing or ambiguous config): nothing to look up, fail-safe.
        ConditionalLogicEvaluator evaluator = evaluatorWithDeclaration(
                Map.of("target", List.of(providerRule("cookie", null, "exists", null))),
                Map.of(),
                "{\"v\":1,\"providers\":{\"cookie\":{\"consent\":\"yes\"}}}"
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE, evaluator.visibility("target"));
    }

    // --- The today sentinel: date rules relative to the submission day ---

    /**
     * Pinned so the plausible-day window [now(UTC-12), now(UTC+14)] is exactly
     * 2026-06-09 → 2026-06-11, with 2026-06-10 as the middle day.
     */
    private static final java.time.Clock FIXED_CLOCK = java.time.Clock.fixed(
            java.time.Instant.parse("2026-06-10T11:00:00Z"), java.time.ZoneOffset.UTC);

    private static ConditionalLogicEvaluator evaluatorWithToday(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, List<String>> submittedValues,
            String declarationJson
    ) {
        return evaluatorWithTodayAt(fieldLogicRules, submittedValues, declarationJson, FIXED_CLOCK);
    }

    private static ConditionalLogicEvaluator evaluatorWithTodayAt(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, List<String>> submittedValues,
            String declarationJson,
            java.time.Clock clock
    ) {
        return new ConditionalLogicEvaluator(
                fieldLogicRules, Map.of(), Map.of(), submittedValues,
                LogicStateDeclaration.parse(declarationJson), clock);
    }

    @Test
    void todayRuleResolvesToTheDeclaredVisitorDayWhenPlausible() {
        // The visitor declared a day one off the server's (a real timezone offset):
        // "today" resolves to the declared day, and the verdict stays a measurement —
        // both evaluators agreed on one calendar day.
        Map<String, List<ConditionalLogicRule>> rules =
                Map.of("target", List.of(rule("start", "date", "on", "today", List.of())));

        ConditionalLogicEvaluator visitorAhead = evaluatorWithToday(rules,
                Map.of("start", List.of("2026-06-11")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-11\"}");
        assertEquals(ConditionalLogicEvaluator.Visibility.VISIBLE, visitorAhead.visibility("target"));

        ConditionalLogicEvaluator serverDayValue = evaluatorWithToday(rules,
                Map.of("start", List.of("2026-06-10")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-11\"}");
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, serverDayValue.visibility("target"));
    }

    @Test
    void todayRuleWithoutDeclarationIsMeasuredOnlyWhenDayIndependent() {
        // No declared day: the server cannot know the visitor's calendar day, only the
        // window of days it currently is somewhere on Earth. A verdict identical across
        // that whole window is a measurement; one that flips inside it is a fail-safe.
        Map<String, List<ConditionalLogicRule>> rules =
                Map.of("target", List.of(rule("start", "date", "before", "today", List.of())));

        assertEquals(ConditionalLogicEvaluator.Visibility.VISIBLE,
                evaluatorWithToday(rules, Map.of("start", List.of("2026-06-01")), null).visibility("target"));
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED,
                evaluatorWithToday(rules, Map.of("start", List.of("2026-06-20")), null).visibility("target"));
        // "2026-06-10" is before the visitor's day only if that day is "2026-06-11".
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE,
                evaluatorWithToday(rules, Map.of("start", List.of("2026-06-10")), null).visibility("target"));
    }

    @Test
    void declaredDayItIsNowhereOnEarthIsRejectedEvenNextToTheWindow() {
        // The plausibility window is derived from the instant, not from the server's
        // own day: a day just outside it is a day it currently is NOWHERE on Earth,
        // however close to the server's calendar day, and must not be trusted.
        Map<String, List<ConditionalLogicRule>> rules =
                Map.of("target", List.of(rule("start", "date", "on", "today", List.of())));

        // Late UTC evening: the window is {2026-06-10, 2026-06-11} — 06-09 is over
        // everywhere. A declaration of 06-09 (one day from the server's own 06-10,
        // which the old serverDay±1 clamp would have trusted) is ignored, and the
        // matching value fails the rule on every window day: a measurement.
        java.time.Clock lateEvening = java.time.Clock.fixed(
                java.time.Instant.parse("2026-06-10T22:00:00Z"), java.time.ZoneOffset.UTC);
        ConditionalLogicEvaluator dayOver = evaluatorWithTodayAt(rules,
                Map.of("start", List.of("2026-06-09")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-09\"}", lateEvening);
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, dayOver.visibility("target"));

        // Early UTC morning: the window is {2026-06-09, 2026-06-10} — 06-11 has not
        // started anywhere. The mirrored declaration is ignored the same way.
        java.time.Clock earlyMorning = java.time.Clock.fixed(
                java.time.Instant.parse("2026-06-10T02:00:00Z"), java.time.ZoneOffset.UTC);
        ConditionalLogicEvaluator dayNotStarted = evaluatorWithTodayAt(rules,
                Map.of("start", List.of("2026-06-11")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-11\"}", earlyMorning);
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, dayNotStarted.visibility("target"));
    }

    @Test
    void implausibleDeclaredDayFallsBackToTheWindow() {
        // A declared day it currently is nowhere on Earth is no timezone offset: it
        // is ignored, and the ambiguity window applies as if nothing were declared.
        Map<String, List<ConditionalLogicRule>> rules =
                Map.of("target", List.of(rule("start", "date", "on", "today", List.of())));

        // Were the declared day trusted, this value would make the field visible.
        ConditionalLogicEvaluator evaluator = evaluatorWithToday(rules,
                Map.of("start", List.of("2026-06-05")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-05\"}");
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, evaluator.visibility("target"));

        // On the ambiguity window's boundary the verdict flips with the day: fail-safe.
        ConditionalLogicEvaluator boundary = evaluatorWithToday(rules,
                Map.of("start", List.of("2026-06-10")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-05\"}");
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE, boundary.visibility("target"));
    }

    @Test
    void betweenRuleMayMixAFixedBoundWithToday() {
        // Only the sentinel entries resolve; fixed bounds stay exact.
        Map<String, List<ConditionalLogicRule>> rules = Map.of("target",
                List.of(rule("start", "date", "between", null, List.of("2026-06-01", "today"))));

        ConditionalLogicEvaluator inside = evaluatorWithToday(rules,
                Map.of("start", List.of("2026-06-08")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-10\"}");
        assertEquals(ConditionalLogicEvaluator.Visibility.VISIBLE, inside.visibility("target"));

        ConditionalLogicEvaluator afterToday = evaluatorWithToday(rules,
                Map.of("start", List.of("2026-06-12")),
                "{\"v\":1,\"providers\":{},\"today\":\"2026-06-10\"}");
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_MEASURED, afterToday.visibility("target"));
    }

    @Test
    void todaySentinelOnlyAppliesToDateComparisons() {
        // A text rule comparing against the literal string "today" keeps comparing the
        // literal: the sentinel exists for date operators only.
        ConditionalLogicEvaluator evaluator = evaluatorWithToday(
                Map.of("target", List.of(rule("comment", "text", "equals", "today", List.of()))),
                Map.of("comment", List.of("today")),
                null
        );

        assertEquals(ConditionalLogicEvaluator.Visibility.VISIBLE, evaluator.visibility("target"));
    }
}
