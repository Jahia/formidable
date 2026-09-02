package org.jahia.modules.formidable.engine.logic;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates conditional logic rules server-side to determine field visibility.
 * A field is visible when ALL its rules are satisfied (AND logic).
 * Handles transitive visibility (hidden source → rule fails) and parent container inheritance.
 */
public class ConditionalLogicEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionalLogicEvaluator.class);

    /**
     * Operators already reported as unknown, so a form submitted repeatedly logs once
     * instead of once per submission. Bounded: a corrupted or hostile rule set cannot
     * grow this into a leak.
     */
    private static final Set<String> REPORTED_UNKNOWN_OPERATORS = ConcurrentHashMap.newKeySet();
    private static final int REPORTED_UNKNOWN_OPERATORS_CAP = 100;

    /** How a field's visibility verdict was reached. */
    public enum Visibility {
        VISIBLE,
        /**
         * Hidden, and provable: every deciding rule was evaluated from submitted values
         * or from the declared provider state. A submitted value for such a field cannot
         * come from an honest browser (a hidden field's controls are disabled and not
         * submitted), so it is actionable.
         */
        HIDDEN_MEASURED,
        /**
         * Hidden as a fail-safe: at least one deciding rule could not be evaluated (no
         * declaration for its provider reference, unknown operator, broken binding).
         * Required validation is skipped but nothing else may act on it — the field may
         * well have been legitimately visible and filled in the browser.
         */
        HIDDEN_FAILSAFE
    }

    /** A rule either holds, or fails with the provenance of that failure. */
    private enum RuleResult { SATISFIED, FAILED_MEASURED, FAILED_FAILSAFE }

    private final Map<String, List<ConditionalLogicRule>> fieldLogicRules;
    private final Map<String, String> logicIdToFieldName;
    private final Map<String, Set<String>> fieldParentContainers;
    private final Map<String, List<String>> submittedValues;
    private final LogicStateDeclaration declaration;
    private final List<String> todayCandidates;

    public ConditionalLogicEvaluator(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, Set<String>> fieldParentContainers,
            Map<String, List<String>> submittedValues
    ) {
        this(fieldLogicRules, logicIdToFieldName, fieldParentContainers, submittedValues,
                LogicStateDeclaration.EMPTY);
    }

    public ConditionalLogicEvaluator(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, Set<String>> fieldParentContainers,
            Map<String, List<String>> submittedValues,
            LogicStateDeclaration declaration
    ) {
        this(fieldLogicRules, logicIdToFieldName, fieldParentContainers, submittedValues,
                declaration, Clock.systemUTC());
    }

    /** Visible for tests: lets them pin the evaluation instant. */
    ConditionalLogicEvaluator(
            Map<String, List<ConditionalLogicRule>> fieldLogicRules,
            Map<String, String> logicIdToFieldName,
            Map<String, Set<String>> fieldParentContainers,
            Map<String, List<String>> submittedValues,
            LogicStateDeclaration declaration,
            Clock clock
    ) {
        this.fieldLogicRules = fieldLogicRules;
        this.logicIdToFieldName = logicIdToFieldName;
        this.fieldParentContainers = fieldParentContainers;
        this.submittedValues = submittedValues;
        this.declaration = declaration;
        this.todayCandidates = computeTodayCandidates(declaration, clock);
    }

    /**
     * The ISO day(s) the today sentinel may resolve to. The plausible days are the
     * calendar days it currently IS somewhere on Earth, from the westmost inhabited
     * offset to the eastmost (the same UTC-12/UTC+14 widening the date bounds use):
     * that window is derived from the evaluation instant, never from the server's own
     * calendar day, whose position inside the window depends on the server's zone.
     *
     * When the browser declared the visitor's local day and it lies in that window,
     * that single agreed day is used: both evaluators then share one "today" and the
     * verdict stays a measurement. Otherwise (no declaration, or a day it is nowhere
     * on Earth) the server cannot know the visitor's day, so the sentinel resolves to
     * every day the window allows — two or three — and only a verdict on which they
     * all agree counts as measured.
     */
    private static List<String> computeTodayCandidates(LogicStateDeclaration declaration, Clock clock) {
        LocalDate earliest = LocalDate.now(clock.withZone(ZoneOffset.ofHours(-12)));
        LocalDate latest = LocalDate.now(clock.withZone(ZoneOffset.ofHours(14)));

        LocalDate declared = declaration.declaredToday();
        if (declared != null && !declared.isBefore(earliest) && !declared.isAfter(latest)) {
            return List.of(declared.toString());
        }

        List<String> candidates = new ArrayList<>();
        for (LocalDate day = earliest; !day.isAfter(latest); day = day.plusDays(1)) {
            candidates.add(day.toString());
        }

        return List.copyOf(candidates);
    }

    public boolean isHidden(String fieldName) {
        return visibility(fieldName) != Visibility.VISIBLE;
    }

    /**
     * A field with multiple parent containers (duplicate name across conditional
     * fieldsets) is hidden only when ALL its parents are hidden. If any parent is
     * visible, the field is reachable and its own rules decide (VISIBLE here means
     * "not hidden by the parents"). The combined verdict is only as strong as its
     * weakest link: one fail-safe parent degrades it.
     */
    private Visibility parentContainersVerdict(String fieldName, Set<String> visiting) {
        Set<String> parentNames = fieldParentContainers.get(fieldName);
        if (parentNames == null || parentNames.isEmpty()) {
            return Visibility.VISIBLE;
        }

        boolean allParentsMeasured = true;
        for (String parentName : parentNames) {
            Visibility parentVisibility = visibility(parentName, visiting);
            if (parentVisibility == Visibility.VISIBLE) {
                return Visibility.VISIBLE;
            }
            if (parentVisibility == Visibility.HIDDEN_FAILSAFE) {
                allParentsMeasured = false;
            }
        }
        return allParentsMeasured ? Visibility.HIDDEN_MEASURED : Visibility.HIDDEN_FAILSAFE;
    }

    public Visibility visibility(String fieldName) {
        return visibility(fieldName, new HashSet<>());
    }

    private Visibility visibility(String fieldName, Set<String> visiting) {
        // The cycle guard must cover the parent walk too: JCR names are only unique
        // among siblings, so a conditional container and a field inside it may share
        // a name — the parent graph then carries a self-loop, and an unguarded
        // recursion would overflow the stack (an Error no servlet catch stops).
        if (!visiting.add(fieldName)) return Visibility.VISIBLE;

        try {
            Visibility parentVerdict = parentContainersVerdict(fieldName, visiting);
            if (parentVerdict != Visibility.VISIBLE) {
                return parentVerdict;
            }

            List<ConditionalLogicRule> rules = fieldLogicRules.get(fieldName);
            if (rules == null || rules.isEmpty()) return Visibility.VISIBLE;

            // Rules are ANDed: any failing rule hides the field. One measured failure is
            // enough to prove the verdict, however many other rules fell back to the
            // fail-safe — the field was hidden for that measurable reason alone.
            boolean anyFailsafeFailure = false;
            for (ConditionalLogicRule rule : rules) {
                switch (evaluateRule(rule, visiting)) {
                    case FAILED_MEASURED -> {
                        return Visibility.HIDDEN_MEASURED;
                    }
                    case FAILED_FAILSAFE -> anyFailsafeFailure = true;
                    case SATISFIED -> { /* keep looking */ }
                }
            }
            return anyFailsafeFailure ? Visibility.HIDDEN_FAILSAFE : Visibility.VISIBLE;
        } finally {
            visiting.remove(fieldName);
        }
    }

    private RuleResult evaluateRule(ConditionalLogicRule rule, Set<String> visiting) {
        if (!rule.isFieldRule()) {
            return evaluateProviderRule(rule);
        }

        String sourceFieldName = resolveSourceFieldName(rule);
        if (sourceFieldName == null) {
            // Broken binding: the rule references a field this submission does not know.
            // Not measurable, so the target field counts as hidden without consequences
            // beyond skipping its required validation.
            return RuleResult.FAILED_FAILSAFE;
        }

        Visibility sourceVisibility = visibility(sourceFieldName, visiting);
        if (sourceVisibility != Visibility.VISIBLE) {
            // A hidden source cannot satisfy a rule; the failure is only as provable as
            // the source's own verdict.
            return sourceVisibility == Visibility.HIDDEN_MEASURED
                    ? RuleResult.FAILED_MEASURED
                    : RuleResult.FAILED_FAILSAFE;
        }

        List<String> values = submittedValues.getOrDefault(sourceFieldName, List.of());

        if (rule.referencesToday()) {
            return evaluateTodayRule(rule, values);
        }

        Boolean satisfied = evaluateFieldOperator(rule, values);
        if (satisfied == null) {
            reportUnknownOperator(rule);
            return RuleResult.FAILED_FAILSAFE;
        }

        return satisfied ? RuleResult.SATISFIED : RuleResult.FAILED_MEASURED;
    }

    /**
     * Evaluates a rule comparing against the submission day, once per candidate
     * resolution of the sentinel. All candidates agreeing means the verdict does not
     * depend on which day the visitor's really was, so it keeps its normal provenance;
     * a disagreement means the visibility genuinely hinges on a day the server cannot
     * know, and only the fail-safe is honest — hidden, but never acted upon.
     */
    private RuleResult evaluateTodayRule(ConditionalLogicRule rule, List<String> values) {
        Boolean agreed = null;
        for (String day : todayCandidates) {
            ConditionalLogicRule resolved = rule.withTodayResolved(day);
            // A 'between' interval emptied by the submission day (its fixed bound
            // now past "today", or not yet reached) matches nothing by construction:
            // the rule is ignored — counts as satisfied — rather than hiding its
            // field forever. The rule editor warns about it. Mirrors the browser
            // evaluator; near the flip the candidates disagree and the ordinary
            // fail-safe below applies.
            Boolean satisfied = isEmptyInterval(resolved)
                    ? Boolean.TRUE
                    : evaluateFieldOperator(resolved, values);
            if (satisfied == null) {
                reportUnknownOperator(rule);
                return RuleResult.FAILED_FAILSAFE;
            }

            if (agreed == null) {
                agreed = satisfied;
            } else if (!agreed.equals(satisfied)) {
                return RuleResult.FAILED_FAILSAFE;
            }
        }

        return Boolean.TRUE.equals(agreed) ? RuleResult.SATISFIED : RuleResult.FAILED_MEASURED;
    }

    /** Whether a resolved 'between' rule's interval contains no date at all. */
    private static boolean isEmptyInterval(ConditionalLogicRule resolved) {
        if (!"between".equals(resolved.operator()) || resolved.values().size() < 2) {
            return false;
        }

        String from = resolved.values().get(0);
        String to = resolved.values().get(1);
        return !from.isEmpty() && !to.isEmpty() && from.compareTo(to) > 0;
    }

    /** The field-rule operator table; null for an operator this engine does not know. */
    private static Boolean evaluateFieldOperator(ConditionalLogicRule rule, List<String> values) {
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
            // Boolean sources submit a truthy value when on (a checkbox posts "true" or
            // the browser default "on") and either nothing (checkbox off) or an explicit
            // "false" (yes/no button pair): an answered "no" must NOT satisfy isTrue.
            case "isTrue" -> values.stream().anyMatch(ConditionalLogicEvaluator::isTruthy);
            case "isFalse" -> values.stream().noneMatch(ConditionalLogicEvaluator::isTruthy);
            // Text sources — an empty text input still submits an empty string, so
            // emptiness means whitespace-blankness, and equals/contains require a
            // non-empty expected value: the browser evaluator sees no value at all for
            // an empty input, and an empty expected value would make the sides disagree.
            case "isEmpty" -> values.stream().allMatch(String::isBlank);
            case "isNotEmpty" -> values.stream().anyMatch(v -> !v.isBlank());
            case "equals" -> !values.isEmpty() && rule.value() != null && !rule.value().isEmpty()
                    && values.get(0).equals(rule.value());
            case "contains" -> !values.isEmpty() && rule.value() != null && !rule.value().isEmpty()
                    && values.get(0).contains(rule.value());
            default -> null;
        };
    }

    /**
     * Provider rules (a JS variable such as a datalayer entry, a URL parameter, a
     * cookie…) read browser state the submission itself does not carry. When the browser
     * declared the referenced state at submit time, the rule is evaluated against that
     * declaration — the same semantics as the client evaluator, and one single declared
     * state for every rule reading it. Without a declaration this is the historical
     * fail-safe: not satisfied, so the field counts as hidden and required validation is
     * skipped, which avoids rejecting legitimate submissions where the field was hidden
     * client-side. Expected by design, hence debug and not warn.
     */
    private RuleResult evaluateProviderRule(ConditionalLogicRule rule) {
        String ref = rule.providerRef();
        if (ref == null || !declaration.isDeclared(rule.sourceType(), ref)) {
            log.debug("Conditional logic rule {} has no declared state (source type '{}'): "
                    + "the target field counts as hidden and its required validation is skipped.",
                    rule.logicId(), rule.sourceType());
            return RuleResult.FAILED_FAILSAFE;
        }

        String actual = declaration.declaredValue(rule.sourceType(), ref);
        boolean defined = actual != null;
        String expected = rule.value() == null ? "" : rule.value();

        Boolean satisfied = switch (rule.operator()) {
            case "equals" -> defined && actual.equals(expected);
            case "notEquals" -> defined && !actual.equals(expected);
            case "contains" -> defined && !expected.isEmpty() && actual.contains(expected);
            case "exists" -> defined;
            case "notExists" -> !defined;
            default -> null;
        };

        if (satisfied == null) {
            reportUnknownOperator(rule);
            return RuleResult.FAILED_FAILSAFE;
        }

        return satisfied ? RuleResult.SATISFIED : RuleResult.FAILED_MEASURED;
    }

    /**
     * An operator this engine does not implement makes its target field count as hidden,
     * which also skips the field's required validation — silently, if we let it. That is
     * indistinguishable from a legitimately hidden field, so it must be reported: it means
     * either corrupted rule JSON or a rule authored against a newer engine.
     */
    private static void reportUnknownOperator(ConditionalLogicRule rule) {
        String operator = rule.operator();
        boolean firstTime;
        // the size check and the add must happen atomically for the cap to be a hard
        // bound; only unknown operators reach this lock, so contention is not a concern
        synchronized (REPORTED_UNKNOWN_OPERATORS) {
            firstTime = REPORTED_UNKNOWN_OPERATORS.size() < REPORTED_UNKNOWN_OPERATORS_CAP
                    && REPORTED_UNKNOWN_OPERATORS.add(operator);
        }
        if (firstTime) {
            log.warn("Unknown conditional logic operator '{}' (rule {}): the rule cannot be "
                    + "evaluated, so its target field counts as hidden and its required "
                    + "validation is skipped. Check the stored rule or the module version.",
                    operator, rule.logicId());
        }
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

    /** A submitted boolean value is truthy unless blank or the explicit "false". */
    private static boolean isTruthy(String value) {
        return value != null && !value.isBlank() && !"false".equalsIgnoreCase(value.trim());
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
