# @jahia/formidable-library

The markup contract and the input-mask behaviour of [Formidable](https://github.com/Jahia/formidable)
forms, for Jahia JavaScript modules that render form fields of their own — a new field type, or a
view taking the place of a built-in one.

Formidable's client (inline validation, help texts, styling) recognises a field by its markup: the
`fmdb-*` class hooks, the help block the control references, the `data-fmdb-msg-*` attributes
carrying custom validation messages. This package is the code Formidable's own views are built on.
Import it rather than copying the markup: a change of contract then surfaces as a type error when
your module builds, not as a silent drift.

## Installation

```sh
yarn add @jahia/formidable-library
```

React 19 is a peer dependency, as in every Jahia JavaScript module. The package follows
Formidable's version numbers: take the version matching the Formidable release your module
targets.

## What it exports

### Help text

`HelpText` renders the help block — `div.fmdb-form-help` with the id `helpTextId(nodeId)`, that is
`help-<nodeId>` — and nothing without a text. The control references the block through
`aria-describedby`. The text is contributor-authored rich text, rendered as HTML. A rendering that
shows the help twice (above and below the field) renders the repeat with `decorative`: no id, hidden
from assistive technology, so a screen reader hears the help once.

```tsx
const helpId = helpText ? helpTextId(currentNode.getIdentifier()) : undefined;

<HelpText id={helpId} text={helpText} />
<input id={`input-${currentNode.getIdentifier()}`} aria-describedby={helpId} className="fmdb-form-control" />
<HelpText text={helpText} decorative />
```

### Validation messages

`validationDataAttributes(props)` turns the custom-message props of the validation mixins
(`fmdbmix:validationMessages`, `fmdbmix:textValidationMessages`,
`fmdbmix:rangeValidationMessages`) into the `data-fmdb-msg-*` attributes the validation client
reads. Type your props with `BaseValidationMessageProps`, `TextValidationMessageProps` or
`RangeValidationMessageProps`, and spread the result on the control:

```tsx
interface MyFieldProps extends TextValidationMessageProps {
  "jcr:title"?: string;
  // …
}

<input {...validationDataAttributes(validationMsgs)} />
```

### Input mask

A mask — `9` a digit, `A`/`a` a letter upper- or lower-cased, `X`/`x` an alphanumeric upper- or
lower-cased, anything else a fixed literal — stands for three things:

- `maskToPattern(mask)` — the HTML `pattern` the browser and the server validate against;
- `applyMask(value, mask)` — a value formatted by the mask, for a prefilled default;
- `useMask({mask})` — the formatting while typing, in a client island: the hook returns an
  `inputRef`, a `handleInput` handler (for the input's `onInput`) that formats the value and keeps
  the caret where the user is editing, and `formatValue`.

```tsx
// MyField.client.tsx
import { useMask } from "@jahia/formidable-library";

export default function MaskedInput({ mask, defaultValue, inputAttributes }) {
  const { inputRef, handleInput } = useMask({ mask });
  return <input {...inputAttributes} ref={inputRef} defaultValue={defaultValue} onInput={handleInput} />;
}
```

These three, with the help text and validation exports above, are the whole public API: the mask
tokens and the caret arithmetic the hook is built on stay inside the package.

## Documentation

- [How to extend views and elements from a third-party module](https://github.com/Jahia/formidable/blob/main/docs/extension/how-to-extend-views-and-elements-from-third-party-module.md) — the rendering contracts, the HTML conventions of a custom field
- [Custom validation](https://github.com/Jahia/formidable/blob/main/docs/architecture/custom-validation.md) — the `data-fmdb-msg-*` attributes and how the client uses them
- [Styling a form](https://github.com/Jahia/formidable/blob/main/docs/styling/README.md) — the class hooks and CSS variables
- [The samples module](https://github.com/Jahia/formidable/tree/main/jahia-test-module/formidable-test-module-samples-tsx) — a module of this kind, built on the package

## Versioning

Every export is public API. The package is released with Formidable under the same version
number, from this repository, and a change of contract is called out in the release notes.
