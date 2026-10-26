# Quick confirmation

`fmdbsample:quickConfirm` is a field type of this module: one checkbox confirming a statement, the
field's title. Checked, it submits `true`; unchecked, nothing, as a single built-in checkbox does.

It shows two things:

- **A field type of your own** (`settings/definitions.cnd`): it extends `fmdbmix:element`, which makes it
  a Formidable field, and `fmdbmix:submittableField`, which tells the engine that it submits a value.
  `fmdbmix:booleanField` makes it a source of the conditional logic (is true, is false), and
  `fmdbmix:profileMappableField` lets an author map it to a jCustomer profile property. Its
  view (`src/components/Input/QuickConfirm/default.server.tsx`) follows the conventions of a built-in
  field: the node name is the input's `name`, `input-<nodeId>` its `id`, and it keeps the Formidable class
  hooks.
- **Moving on with a pick in a One-at-a-time form**
  ([One-at-a-time](../../../docs/architecture/one-at-a-time.md)): the view puts
  `data-fmdb-advance-on-change="true"` on the element that holds the checkbox, so checking it moves to the
  next screen. Unchecking it withdraws the answer, and the form stays on the screen. A field that
  takes several answers does not set the attribute.

The Cypress spec `tests/cypress/e2e/validation/52-one-at-a-time-pick-fields.cy.ts` exercises it.
