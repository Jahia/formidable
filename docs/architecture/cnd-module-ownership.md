# CND Module Ownership

This document explains how Formidable splits JCR node types and mixins between
`formidable-elements`, `formidable-engine` and `formidable-extended-inputs` (the optional
extra field types — Consent, Rating, Scale, Switch — which own their `fmdbext:*` node
types and follow the same rules as any third-party field module: concrete types in the
field module, runtime contracts in the engine).

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

Examples (non-exhaustive — the CND files are the source of truth):

- `fmdb:form`
- `fmdb:fieldList`
- `fmdbmix:component`
- `fmdbmix:element` (the public contract for embedding third-party field types in a form)
- `fmdbmix:formContent`
- `fmdbmix:buttons`
- `fmdbmix:responses`
- `fmdbmix:multiStep`
- `fmdbmix:style`
- `fmdbmix:validationMessages` / `fmdbmix:textValidationMessages` / `fmdbmix:rangeValidationMessages`
- `fmdbmix:advancedTextareaSettings`

These definitions shape what authors can create and how the rendered form tree is organized.

### 2. Server-interpreted structural and semantic mixins

These definitions belong in `formidable-engine` when the engine reads them to decide how to validate, classify, or execute server-side behavior.

Examples (non-exhaustive — the CND files are the source of truth):

- `fmdbmix:formLogicElement`
- `fmdbmix:formContainer`
- `fmdbmix:formStep`
- `fmdbmix:formElement`
- `fmdbmix:formAction`
- `fmdbmix:readOnlyCompatibleAction` (declares an action that keeps working while the platform is in read-only mode)
- `fmdbmix:nonSubmittable`
- `fmdbmix:choiceField` and its options modes: `fmdbmix:optionsSource`, `fmdbmix:manualOptions`, `fmdbmix:sourcedOptions`, `fmdbmix:categoryOptions`, `fmdbmix:contentOptions`
- `fmdbmix:textField`
- `fmdbmix:fileField`
- `fmdbmix:emailField`
- `fmdbmix:dateField` and its bound modes: `fmdbmix:dateBounds`, `fmdbmix:fixedMinDate`, `fmdbmix:fixedMaxDate`, `fmdbmix:relativeMinDate`, `fmdbmix:relativeMaxDate`
- `fmdbmix:datetimeLocalField` and its bound modes: `fmdbmix:datetimeBounds`, `fmdbmix:fixedMinDatetime`, `fmdbmix:fixedMaxDatetime`, `fmdbmix:relativeMinDatetime`, `fmdbmix:relativeMaxDatetime`
- `fmdbmix:colorField`
- `fmdbmix:numberField`
- `fmdbmix:booleanField`
- `fmdbmix:profileMappableField` (the marker a field type claims to take part in a jExperience profile mapping; the jExperience module's property mixin extends this single marker, never a list of field types — see `jexperience-integration.md`)

These are not presentation hints. They are runtime contracts interpreted by Java code in the submission pipeline (the options-mode mixins additionally drive the server-side resolution of choice options).

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

### Naming a property

A property is never namespaced, whichever type or mixin declares it: `fieldKey`, `logics`,
`msg*`, `min`, `max`, `optionsMode`, `minBoundMode`… A field type names its properties after
the HTML attribute they render when there is one. The `fmdb:` prefix belongs to node type and
mixin names only — 0.4.0 shipped thirteen prefixed properties, twelve on the options-source and
date-bounds mixins and one on the select type (`fmdb:optionsEmptyLabel`), all renamed in 0.5.0
with a startup migration (#310), so that a view, a query or a label key never has to check the
definition to know which spelling to use.

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

## Naming these types from Java

A node type or mixin name is written **once** in Java, in `formidable-engine`'s exported API
package, and every reader imports it from there — the engine itself, `formidable-jexperience-engine`,
and a module of your own:

| Class in `org.jahia.modules.formidable.engine.api` | Holds |
|---|---|
| `FormidableNodeTypes` | the primary types the engine's CND declares — the logic storage, the built-in actions, the submission storage |
| `FormidableMixins` | every mixin it declares — the extension surface |
| `FormidableProperties` | the item names (properties and child nodes) another module reads on that content |

```java
import static org.jahia.modules.formidable.engine.api.FormidableMixins.FILE_FIELD_MIXIN;

if (field.isNodeType(FILE_FIELD_MIXIN)) { … }
```

### What the API carries, and what it does not

The two type classes carry the engine's **whole** CND vocabulary, and are checked both ways: a
mixin added to the CND without a constant fails the build. A mixin is how a module opts into
engine behaviour, so all of them are contract — and they are frozen already, by the content
stored in every repository that runs Formidable.

`FormidableProperties` is checked one way only. A property is local to the type that declares it
until something outside reads it, so the class holds the names that crossed and grows when
another does. The same asymmetry explains the two mixins that are absent: the one-shot markers of
the 0.4 content migrations. Each records that a migration has already healed a node — the engine
talking to itself — and although the CND keeps the declarations after the migrations leave, so that
marked content stays valid, no other module has a reason to read one. They live in
`migration/MigrationMarkers`.

These are compile-time constants, so a consumer's bytecode carries the value, not a reference to
the class: they buy one spelling and a compiler error on a typo, not the ability to change a name
later. That is the right trade for a node type — it is a persistence contract, and changing one is
a content migration, never a silent update.

### The two places a literal is still correct

**A migration.** `formidable-engine/…/migration/` is exempt. A migration is a frozen script: it
must keep naming the vocabulary of the release it heals, not follow the live one, exactly as a
database migration does not import the current model. Give it its own private constant and leave
it alone.

**A name another module declares.** The engine publishes only what its own CND declares, so
`fmdb:form`, `fmdb:checkbox` and the other concrete field types — `formidable-elements`' — have no
constant, and the modules that read them still spell them out. That residue is the measure of a
missing marker, not of sloppiness: under the rule above, server-side code reads a mixin. Two are
known and open:

- **the form.** The engine, the jExperience integration and the sample integrity checks each name
  `fmdb:form`. An engine-owned marker applied to `fmdb:form` would close it and let a third-party
  form type exist, the way `fmdbmix:captcha` already wraps `fmdbmix:captchaProtectedForm`.
- **the checkbox.** `FieldShapes` reads `fmdb:checkbox` to tell one choice from a group, the one
  distinction no mixin carries.

### The guard

`node scripts/check-nodetype-names.mjs` runs in the static-analysis job and enforces both halves:
the parity above, and that no **main** source outside those classes spells an engine-declared name
out. It prints how many literals name another module's types, so the residue stays visible.

Test sources are deliberately outside it. A test that writes `"fmdbmix:choiceField"` where the
constant would do is how a wrong constant *value* gets caught — the parity check proves the name is
declared somewhere, not that the right one was picked — so the literal there is an asset. Use the
constants in a test when the name is plumbing for a fixture; keep the literal when the name is the
thing under test.

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
- export its name as `FormidableMixins.PHONE_FIELD_MIXIN` and let the parser or validator react to
  `node.isNodeType(PHONE_FIELD_MIXIN)`

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

- `docs/extension/how-to-extend-views-and-elements-from-third-party-module.md` for the rendering contract to follow when a third-party module adds a custom container view or a new form element.
