# Fields E2E Coverage

The `fields` test suite verifies the form elements themselves: what each field
renders in live mode, how users interact with it, and whether the form can be
submitted with realistic field combinations.

This area is about field behavior and field integration in the rendered form:
input widgets, grouping, file selection, rich text rendering, and successful
submission flows involving the supported field types.

## Covered areas

- `20-all-field-types.cy.ts` - end-to-end submission of a live form using the
  supported field set, in single-step and multi-step variants.
- `21-file-multiple-restrictions.cy.ts` - multiple file selection, client-side
  filtering of invalid files, and merge behavior across selections.
- `22-checkbox.cy.ts` - standalone checkbox and checkbox-group rendering and
  interaction.
- `23-radio.cy.ts` - standalone radio and radio-group rendering and selection.
- `24-select-options.cy.ts` - visible options, single-select, multi-select, and
  disabled-state rendering.
- `25-richtext.cy.ts` - rich-text rendering, including embedded links and
  images in live mode.
- `26-email-pattern-suggestions-length.cy.ts` - email-field constraints,
  browser validation stability, and successful submission after correction.
- `27-fieldset.cy.ts` - field grouping inside a fieldset in live mode.
- `28-file-save-all-fixtures.cy.ts` - upload persistence through the save-to-JCR
  action, including saved metadata for the fixture set.
- `29-file-fake-txt-rejected.cy.ts` - backend MIME detection rejects disguised
  uploads even when the browser-side accept filter allows them.

## Not covered here

- Inline validation behavior and validation flow are documented in
  [validation.md](./validation.md).
- Server-side fail-closed and submit-endpoint security behavior are outside the
  scope of this document.
