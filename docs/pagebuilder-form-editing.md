# Page Builder Form Editing

This document explains how `fmdb:form` nodes can be opened and edited directly in jContent Page Builder, and why each moving part exists.

## What it enables

- Opening a form from the Content Folders accordion in **Page Builder** view mode (WYSIWYG).
- Adding form elements in place through a **single "New content" button** at every insertion point (form root, inside steps, inside fieldsets). The button opens the standard content type picker, narrowed to what the container allows.
- Editing elements that would be hidden on the live site: all steps are rendered stacked, and conditional-logic-hidden fields stay visible.

## Moving parts

### 1. `jmix:mainResource` on `fmdb:form`

jContent enables the Page Builder view mode only for `jnt:page` nodes and nodes carrying `jmix:mainResource` (see `ViewModeSelector.jsx` in jContent — it does **not** check whether the node has a template). `jmix:mainResource` is a pure marker mixin with no properties, added to the `fmdb:form` supertypes in `formidable-elements/src/components/Form/definition.cnd`.

### 2. A template for `fmdb:form`

Page Builder renders the node as a standalone page, which requires a template:

- `formidable-elements/src/templates/Form/default.server.tsx` — `componentType: "template"`, `priority: 1` (wins over any generic `jmix:mainResource` template from another module)
- `formidable-elements/src/templates/Layout.tsx` — minimal head/body layout
- `formidable-elements/src/components/Form/fullPage.server.tsx` — thin `fullPage` view delegating to the default view

This is the standard javascript-modules pattern for main-resource content (see the Hydrogen sample: `Area` is for page templates, `Render node={currentNode}` is for content templates). An `Area` would be wrong here: `fmdb:form` only allows the `fields` and `actions` children, and the goal is to edit the form itself, not to compose free content around it.

### 3. In-place create buttons

`FormContainer/hidden.logic.server.tsx` (the shared container view used by the field list, steps, and fieldsets) appends `<AddContentButtons/>` after its children when `renderContext.isEditMode()` is true.

### 4. Placement-scope mixins: one "+" button per insertion point

jContent renders two kinds of create buttons in Page Builder:

- the **placeholder** button at the end of a container (our `AddContentButtons`)
- **insertion points** between existing children, generated automatically by jContent

Both fall back to the container's **CND child constraints** to decide which buttons to show, and below the `createChildrenDirectButtons.limit` threshold jContent renders **one button per allowed type**, labeled "New {typeName}". With the historical constraints (2–3 mixins per container, no i18n labels) this produced rows of "New formContent" / "New formElement" / "New formStep" buttons.

Since the three container scopes nest (fieldset ⊂ step ⊂ form root), each container now declares a **single** child constraint from a chain of engine-owned marker mixins:

```cnd
[fmdbmix:formItem] mixin                          # allowed at form root
[fmdbmix:stepItem] > fmdbmix:formItem mixin       # allowed inside a step
[fmdbmix:fieldsetItem] > fmdbmix:stepItem mixin   # allowed inside a fieldset
```

| Type | Scope mixin | Effective placement |
|---|---|---|
| `fmdbmix:formStep` | `fmdbmix:formItem` | form root only (steps cannot nest) |
| `fmdbmix:formElement` (fields, fieldsets) | `fmdbmix:stepItem` | form root + steps |
| `fmdbmix:element` (plain leaf fields) | `fmdbmix:fieldsetItem` | form root + steps + fieldsets |
| `fmdbmix:formContent` (editorial content) | `fmdbmix:fieldsetItem` | everywhere |

Container constraints: `fmdb:fieldList` → `+ * (fmdbmix:formItem)`, `fmdb:step` → `+ * (fmdbmix:stepItem)`, `fmdb:fieldset` → `+ * (fmdbmix:fieldsetItem)`.

Result: every insertion point shows exactly one "+" button. Its label comes from the mixin display name in the engine resource bundles (`fmdbmix_formItem=content`, etc.), so it reads "New content" / "Nouveau : contenu". Clicking it opens the content type picker with the concrete types allowed at that spot (the picker intersects the constraint with its subtypes).

The previous placement rules are preserved exactly: no nested steps, no nested fieldsets, fieldsets allowed at root and in steps, editorial content allowed everywhere. Existing JCR content stays valid without migration because every previously-allowed child type inherits the new scope mixin through its existing supertypes. Third-party extensions keep working unchanged: extending `fmdbmix:element` or `fmdbmix:formContent` (see `how-to-extend-views-and-elements-from-third-party-module.md`) inherits the right scope automatically.

### 5. Edit-mode rendering behavior

In Page Builder, hidden elements would be unselectable and uneditable, so edit mode disables all interactive hiding:

- `Form/default.server.tsx` computes `isEditMode` and stops passing `hideStepsAfterFirst` / `preferCompactStepView` to the container view → steps render stacked, with their titles, all visible.
- `LogicAwareRender.tsx` skips the `display: none` / `aria-hidden` treatment of logic-hidden elements in edit mode (it reads the context through `useServerContext()`).
- The `isEditMode` prop is passed through the Island to `Form.client.tsx`, which disables `useMultiStep` DOM side effects (step switching, `applyConditionalLogicVisibility`) so client hydration does not re-hide anything. Submission was already disabled in edit/preview modes via `isSubmitDisabled`.

Live and preview rendering are unaffected: all of the above is gated on `renderContext.isEditMode()`.

## Tests

`tests/cypress/e2e/pagebuilder/70-pagebuilder-form-editing.cy.ts` covers:

1. opening a form in Page Builder through the template,
2. the single create button on the field list (`data-sel-role="fmdbmix:formItem"`),
3. steps and logic-hidden fields staying visible in edit mode.

Note: `cypress-iframe` is imported in `tests/cypress/support/e2e.js`; the jcontent-cypress Page Builder page objects depend on it.

## Deployment notes

- Deploy `formidable-engine` **before** `formidable-elements`: the elements CND references the scope mixins defined in the engine.
- The `/modules/<module>/dist/...` URL is not served for javascript modules; to verify a deploy took effect, check the Jahia logs for `Registered Jahia component: formidable-elements_template_fmdb:form_default_1`.
