# Validation E2E Coverage

The `validation` test suite verifies how the rendered form blocks invalid user
input before submission. It focuses on constraint enforcement, error messages,
focus management, and multi-step validation flow.

This area is about validation behavior around the fields, not the general field
catalog itself: required states, textual and range constraints, reset behavior,
and progression rules when a form has several steps.

## Covered areas

- `30-required-validation.cy.ts` - custom required messages for select,
  checkbox, checkbox group, file input, and radio group, plus error clearing
  after correction.
- `31-textual-validation.cy.ts` - custom messages for text constraints,
  `minLength`, `pattern`, native email fallback, and custom email mismatch
  messaging.
- `32-range-validation.cy.ts` - date and datetime-local required/min/max/step
  validation messages.
- `33-validation-flow.cy.ts` - reset behavior, invalid styling cleanup, and
  focus progression across invalid fields.
- `34-multistep-validation.cy.ts` - step blocking, focus on first invalid field,
  and final-step submission blocking in multi-step forms.

## Not covered here

- Field rendering and successful submission paths are documented in
  [fields.md](./fields.md).
- Server-side fail-closed and submit-endpoint security behavior are outside the
  scope of this document.
