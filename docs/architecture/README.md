# How Formidable works

For the maintainer: how the modules work, and why they are built this way.

## How it works

- [Form submission flow](form-submission-flow.md) — the request lifecycle, the twelve-step pipeline, the trust model and the server-side safeguards
- [Save to JCR](save-to-jcr.md) — how submissions and uploaded files are stored, the results tree, personal data
- [Export](export.md) — the multi-format export of the results (CSV, JSON) and how to add a format
- [Custom validation](custom-validation.md) — inline validation messages replacing the native browser tooltips, with per-field contributor overrides
- [Conditional logic field resolution](conditional-logic-field-resolution.md) — the weakref-based model of the conditional-logic dependencies, and how rules are resolved
- [Choice field options sources](choice-field-options-sources.md) — options from categories or from content: storage model, resolution, cache, writing a source initializer
- [jExperience integration](jexperience-integration.md) — design specification (revised 2026-09-10, browser-side): the auto-generated form mapping, the submission event sent by the tracker with the values the pipeline accepted, prefill from the tracker's context, the send condition and consent gates, the `formidable-jxp-<uuid>` identifier; not implemented yet

## Why it is built this way

- [CND module ownership](cnd-module-ownership.md) — where JCR types and mixins belong, and how to choose between `formidable-elements` and `formidable-engine`
- [Dependency decisions](dependency-decisions.md) — embedded or provided dependencies in the Java modules, and why
- [Why `querySelector` in React](why-queryselector-in-react.md) — why the client components read the DOM directly
