# Custom Validation

## Overview

Formidable replaces native browser validation tooltips with inline error messages displayed
directly in the DOM. The `<form>` element always renders with `noValidate`, which suppresses
the browser's default tooltip UI while keeping the Constraint Validation API fully functional.

When a field is invalid, a styled `<div>` is injected below it (or appended to its
`.fmdb-form-group` wrapper) with the resolved error message.

---

## Message resolution

When a field fails validation, the message is resolved using a two-level cascade:

| Priority | Source | How it works |
|---|---|---|
| 1 | Contributor-defined message | Read from `data-fmdb-msg-*` attributes on the `<input>` / `<select>` / `<textarea>` element |
| 2 | Browser default | `input.validationMessage` — the browser's built-in message, already localized to the user's language |

The first non-empty value wins. If the contributor has not set a custom message for the
specific validity failure, the browser's native message is used as a fallback.

### Resolution logic

```
for each ValidityState flag (valueMissing, typeMismatch, ...):
    if flag is true:
        if data-fmdb-msg-{flag} attribute exists and is non-empty → use it
        else → use input.validationMessage
```

This is implemented in `validationUtils.ts → resolveValidationMessage()`.

---

## Validation message mixins (CND)

Not all validity constraints apply to every field type. To avoid exposing irrelevant
properties in the Content Editor, validation messages are split into a hierarchy of three
mixins:

### `fmdbmix:validationMessages` (base)

Applies to: checkbox, radio, select, file, color — elements where only `required` can fail.

| Property | ValidityState flag | Example trigger |
|---|---|---|
| `msgValueMissing` | `valueMissing` | Required field left empty |

### `fmdbmix:textValidationMessages` (extends base)

Applies to: text, email, textarea — elements with text-specific constraints.

| Property | ValidityState flag | Example trigger |
|---|---|---|
| `msgValueMissing` | `valueMissing` | Required field left empty |
| `msgTypeMismatch` | `typeMismatch` | Invalid email format |
| `msgPatternMismatch` | `patternMismatch` | Value does not match `pattern` regex |
| `msgTooShort` | `tooShort` | Value shorter than `minLength` |
| `msgTooLong` | `tooLong` | Value longer than `maxLength` |

### `fmdbmix:rangeValidationMessages` (extends base)

Applies to: date, datetime-local — elements with range and step constraints.

| Property | ValidityState flag | Example trigger |
|---|---|---|
| `msgValueMissing` | `valueMissing` | Required field left empty |
| `msgRangeUnderflow` | `rangeUnderflow` | Value before `min` date |
| `msgRangeOverflow` | `rangeOverflow` | Value after `max` date |
| `msgStepMismatch` | `stepMismatch` | Value does not match `step` increment |
| `msgBadInput` | `badInput` | Unparseable input (e.g. letters in a date field) |

### Mixin assignment

Each element type extends the appropriate mixin in its `definition.cnd`:

```cnd
// Text input — has text constraints
[fmdb:inputText] > ... fmdbmix:textValidationMessages

// Date input — has range constraints
[fmdb:inputDate] > ... fmdbmix:rangeValidationMessages

// Checkbox — only required can fail
[fmdb:checkbox] > ... fmdbmix:validationMessages
```

The content editor form definition (`fmdbmix_validationMessages.json`,
`fmdbmix_textValidationMessages.json`, `fmdbmix_rangeValidationMessages.json`) surfaces
only the relevant properties for each mixin level.

---

## Data flow: server → client

Validation messages follow the standard Formidable server/client split. No Island is needed
for individual inputs — the messages are passed as HTML data attributes.

```
Server (default.server.tsx)
  │
  │  1. Read msg* properties from JCR node (via the mixin)
  │  2. Call validationDataAttributes(validationMsgs)
  │  3. Spread result onto <input> / <select> / <textarea>
  │
  ▼
HTML output
  │
  │  <input type="text" ... data-fmdb-msg-value-missing="Please fill this in"
  │                          data-fmdb-msg-pattern-mismatch="Use format AB-1234" />
  │
  ▼
Client (Form.client.tsx)
  │
  │  useCustomFormValidation hook:
  │    - listens for 'invalid' events (capture phase)
  │    - calls resolveValidationMessage(input) → reads data-fmdb-msg-* attributes
  │    - calls showFieldError(input, message) → injects error <div> into DOM
  │    - listens for 'input'/'change' events → clears error when field becomes valid
  │    - listens for 'reset' → clears all errors
  │
  │  validateInputs(container):
  │    - called at form submit and step navigation
  │    - iterates all inputs, validates each, shows/clears errors
  │    - focuses the first invalid field
  │
  ▼
DOM
DOM
  <div class="fmdb-form-group">
    <label>Employee code</label>
    <input class="fmdb-form-control fmdb-invalid" ... />
    <div class="fmdb-validation-error" role="status">Use format AB-1234</div>
```

### Server-side helper

`validationProps.ts` exports `validationDataAttributes()` which converts mixin properties
to `data-fmdb-msg-*` HTML attributes. Each server component uses it:

```tsx
// In default.server.tsx
const { "jcr:title": label, required, ...validationMsgs } = props;

<input
    type="text"
    required={required}
    {...validationDataAttributes(validationMsgs)}
/>
```

Only non-empty messages are rendered as attributes. Empty strings are converted to
`undefined` so they do not appear in the HTML output.

---

## Styling

Error messages and invalid fields use CSS classes with custom properties for full control:

| CSS class | Applied to | Purpose |
|---|---|---|
| `fmdb-validation-error` | Injected `<div>` | Error message text |
| `fmdb-invalid` | `<input>` / `<select>` / `<textarea>` | Visual invalid state (border highlight) |

### CSS custom properties

Override these in your theme or in the form's `fmdbmix:style` CSS field:

```css
:root {
    --fmdb-validation-error-color: #dc2626;
    --fmdb-validation-error-font-size: 0.875rem;
    --fmdb-validation-error-mt: 0.25rem;
    --fmdb-validation-error-padding: 0;
    --fmdb-validation-error-line-height: 1.25;
    --fmdb-invalid-border-color: #dc2626;
    --fmdb-invalid-outline-color: #dc2626;
}
```

### Example: softer styling

```css
:root {
    --fmdb-validation-error-color: #b45309;
    --fmdb-validation-error-font-size: 0.8rem;
    --fmdb-invalid-border-color: #b45309;
}
```

---

## Multi-step forms

Validation is enforced per-step during navigation. When the user clicks "Next":

1. `validateInputs()` is called on the current step container
2. If any field is invalid, errors are shown and navigation is blocked
3. The first invalid field receives focus

At final submission, `validateInputs()` runs on the entire `<form>` element to catch any
field that might have been missed.

---

## Checkbox groups (special case)

Checkbox groups use an Island (`Checkbox.client.tsx`) because validating "at least one
checked" requires client-side JavaScript across multiple `<input>` elements.

The group validation uses `setCustomValidity()` on all checkboxes in the group:

- If the contributor set `msgValueMissing` via the mixin → that message is used
- Otherwise → the i18n key `fmdb_inputCheckbox.error` is used as fallback

The `data-fmdb-msg-value-missing` attribute is set on the first checkbox of the group so
that `resolveValidationMessage()` can pick it up during form-level validation.

---

## Key files

| File | Role |
|---|---|
| `src/utils/validationUtils.ts` | `resolveValidationMessage`, `showFieldError`, `clearFieldError`, `clearAllFieldErrors` |
| `src/utils/validationProps.ts` | `validationDataAttributes` — server-side helper to convert mixin props to data attributes |
| `src/hooks/useCustomFormValidation.ts` | `useCustomFormValidation` hook + `validateInputs` function |
| `src/design/validation.css` | CSS classes and custom properties |
| `settings/definitions.cnd` | `fmdbmix:validationMessages`, `fmdbmix:textValidationMessages`, `fmdbmix:rangeValidationMessages` |
| `settings/jahia-content-editor-forms/forms/fmdbmix_validationMessages.json` | Content Editor form for base mixin |
| `settings/jahia-content-editor-forms/forms/fmdbmix_textValidationMessages.json` | Content Editor form for text mixin |
| `settings/jahia-content-editor-forms/forms/fmdbmix_rangeValidationMessages.json` | Content Editor form for range mixin |

