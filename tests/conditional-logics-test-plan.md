# Conditional Logics Test Plan

This document captures the runtime and editor behaviors to cover once conditional logics are implemented.

## Scope

V1 conditional logics apply to nodes carrying `fmdbmix:formLogicElement` and use:

- `logics (string) multiple indexed=no`
- `AND` semantics between all rules

Supported source field types in V1:

- `fmdb:select`
- `fmdb:radio`
- `fmdb:checkbox`
- `fmdb:inputDate`

## Content Editor behaviors to test

1. A target field with `fmdbmix:formLogicElement` exposes a `Logics` tab.
2. The `Logics` tab exposes the `logics` field with selector type `ConditionalLogic`.
3. The source field selector only lists supported source field types.
4. The source field selector only lists fields located before the current target in the form flow.
5. The current target field is never available as a source.
6. When the source field is a select/radio/checkbox, the comparison value UI reflects the source field choices.
7. When the source field is a date, the comparison UI switches to date operators and date value inputs.
8. Each multivalue entry in `logics` stores one valid JSON rule.
9. Multiple logic rules can be added on the same target field.

## Runtime behaviors to test

1. A field without `logics` remains visible.
2. A field with one logic rule is hidden when the source condition is not met.
3. A field with one logic rule becomes visible when the source condition is met.
4. A field with multiple logic rules becomes visible only when all rules are satisfied.
5. A field hidden by logic has its descendant inputs disabled.
6. A field shown again by logic restores its descendant inputs.
7. A hidden required field does not block form navigation or submission.
8. A hidden field value is not submitted.

## Type-specific scenarios

### Select

1. `in` matches one selected option.
2. `notIn` hides when the selected option is part of the forbidden set.
3. Multi-select sources are evaluated consistently against stored rule values.

### Radio

1. `in` matches the selected radio value.
2. `notIn` behaves correctly when another radio value is selected.

### Checkbox

1. Single checkbox supports `isChecked`.
2. Single checkbox supports `isUnchecked`.
3. Checkbox group supports `containsAny`.
4. Checkbox group supports `containsAll`.

### Date

1. `before` works with ISO date values.
2. `after` works with ISO date values.
3. `on` works with exact date equality.
4. `between` includes the expected lower and upper bounds.

## Form structure scenarios

1. Logic targeting a field directly under `fmdb:form`.
2. Logic targeting a field inside `fmdb:fieldset`.
3. Logic targeting a field inside a later `fmdb:step`.
4. A source field in a previous step controls a target field in a later step.
5. A field in a later step is not offered as a source for a field in an earlier step.

## Regression checks

1. Existing non-conditional fields still render with their usual default view.
2. Existing form submission flow still works without conditional logic configured.
3. Step navigation still works when hidden fields are present in the current step.
4. Server-side rendering still produces stable markup for fields with and without `fmdbmix:formLogicElement`.
