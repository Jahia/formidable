# CND Module Ownership

This document explains how Formidable splits JCR node types and mixins between `formidable-elements` and `formidable-engine`.

The goal is to keep the model readable, extensible, and aligned with runtime dependencies:

- `formidable-elements` owns the form rendering model and editor-facing content structure
- `formidable-engine` owns the server-side contracts that the engine interprets at runtime

## Why this split exists

Formidable is not a single runtime:

- `formidable-elements` provides the rendered form tree, front-end views, and most editor-facing form content types
- `formidable-engine` provides the submission pipeline, action framework, conditional logic admin tooling, and server-side validation

If a definition lives in the wrong module, one of two problems usually appears:

1. the engine becomes coupled to concrete field type names declared elsewhere
2. the content model stops reflecting the runtime contract that actually drives behavior

## Definition categories

### 1. Editorial and rendering model

These definitions belong in `formidable-elements` when they primarily describe:

- content structure used to render a form
- author-facing presentation options
- container relationships used by the page editor
- reusable field definitions that are mostly a UI concern

Examples:

- `fmdb:form`
- `fmdb:fieldList`
- `fmdbmix:component`
- `fmdbmix:formContent`
- `fmdbmix:buttons`
- `fmdbmix:responses`
- `fmdbmix:multiStep`
- `fmdbmix:style`

These definitions shape what authors can create and how the rendered form tree is organized.

### 2. Server-interpreted structural and semantic mixins

These definitions belong in `formidable-engine` when the engine reads them to decide how to validate, classify, or execute server-side behavior.

Examples:

- `fmdbmix:formLogicElement`
- `fmdbmix:formContainer`
- `fmdbmix:formStep`
- `fmdbmix:formElement`
- `fmdbmix:formAction`
- `fmdbmix:nonSubmittable`
- `fmdbmix:choiceField`
- `fmdbmix:fileField`
- `fmdbmix:emailField`
- `fmdbmix:dateField`
- `fmdbmix:datetimeLocalField`
- `fmdbmix:colorField`

These are not presentation hints. They are runtime contracts interpreted by Java code in the submission pipeline.

For example, `fmdbmix:fileField` tells the engine that a field is backed by multipart file data. The engine should not have to know whether the concrete node type is `fmdb:inputFile` or a third-party type defined in another module.

### 3. Engine-owned content types

Concrete content types belong in `formidable-engine` when they are created, resolved, or consumed by engine services rather than by the rendered form tree itself.

Examples:

- `fmdb:logicSrc`
- `fmdb:logicList`
- `fmdb:emailNotificationAction`
- `fmdb:emailContentAction`
- `fmdb:forwardAction`
- `fmdb:save2jcrAction`
- `fmdb:resultsFolder`
- `fmdb:formResults`
- `fmdb:formSubmission`

These types are part of the engine contract. They support submission handling, persistence, exports, and action execution.

## Decision rules

When adding a new CND definition, use these questions:

### Put it in `formidable-elements` if...

- the type exists mainly to render the form or structure authorable content
- the definition is part of the visible form tree
- the properties mostly drive labels, layout, buttons, styles, or client rendering
- the type is a concrete field or content node used directly by front-end views

### Put it in `formidable-engine` if...

- the engine reads the definition to decide server-side behavior
- the type or mixin is part of the action SPI or submission pipeline contract
- a third-party module should be able to opt into engine behavior by applying the mixin
- the meaning of the definition is operational rather than presentational

## Content Editor form ownership

The same ownership rules should also be applied to `jahia-content-editor-forms` JSON files, but with one extra distinction:

- the CND owner defines the runtime contract
- the editor-form owner defines how that contract is edited in Jahia

Use these rules:

### Keep the editor form in `formidable-elements` if...

- it edits an elements-owned node type or mixin
- it configures rendering-oriented form properties
- it customizes an element type such as `fmdb:checkbox`, `fmdb:radio`, or `fmdb:select`

This remains true even if the selector widget itself is implemented in `formidable-engine`.

### Move the editor form to `formidable-engine` if...

- it edits an engine-owned node type or mixin
- it exists only to expose an engine-side runtime contract
- it depends on an engine-owned selector and targets an engine-owned mixin

Example:

- `fmdbmix:formLogicElement` is now owned by `formidable-engine`
- its Content Editor form and fieldset therefore belong in `formidable-engine` as well
- `fmdb:checkbox`, `fmdb:radio`, and `fmdb:select` stay in `formidable-elements` because those edited node types still belong to the rendering module

## Current exceptions

The main remaining mixed-responsibility case is at the form root.

### Form-level feature mixins use a wrapper pattern

These author-facing mixins still live in `formidable-elements/src/components/Form/definition.cnd`:

- `fmdbmix:captcha`
- `fmdbmix:requireAuthentication`

The reason is practical: they extend `fmdb:form`, which is itself defined in `formidable-elements`.

The engine-facing semantics now live in `formidable-engine`:

- `fmdbmix:captchaProtectedForm`
- `fmdbmix:authenticatedOnlyForm`

The `formidable-elements` mixins act as wrappers:

- `fmdbmix:captcha` extends `fmdbmix:captchaProtectedForm`
- `fmdbmix:requireAuthentication` extends `fmdbmix:authenticatedOnlyForm`

This keeps the authoring anchor on `fmdb:form`, while the runtime Java code reads
`fmdbmix:captchaProtectedForm` and `fmdbmix:authenticatedOnlyForm`.

## Practical examples

### Example: adding a new field validation semantic

If a new field type needs server-side validation that other modules may also reuse, define a mixin in `formidable-engine`.

Example:

- add `fmdbmix:phoneField` in `formidable-engine`
- make `fmdb:inputPhone` in `formidable-elements` extend that mixin
- let the parser or validator react to `node.isNodeType("fmdbmix:phoneField")`

This keeps the engine coupled to semantics, not to one concrete node type name.

### Example: adding a presentational form option

If a new property only changes how the form renders, keep it in `formidable-elements`.

Example:

- a new `showProgressBar` boolean on the form
- a new visual variant for button placement

These are rendering concerns, not engine contracts.

## Rule of thumb

If renaming a concrete field type would break the behavior, the engine is probably reading the wrong thing.

Prefer:

- semantic mixins in `formidable-engine`
- rendered structure and presentation in `formidable-elements`

That split gives external modules a stable contract: they can define their own field types in their own CNDs and still participate in Formidable behavior by applying the right engine-owned mixins.

See also:

- `docs/how-to-extend-views-and-elements-from-third-party-module.md` for the rendering contract to follow when a third-party module adds a custom container view or a new form element.
