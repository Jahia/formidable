# Editing a form in the jContent Page Builder

A form can be authored where it renders: on a page holding a form reference, or on its
own, opened from the **Content Folders** in Page Builder view mode. This document lists the
parts that make the second path work and the authoring model both paths share.

## Opening a form from the Content Folders

### `jmix:mainResource` on `fmdb:form`

jContent offers the Page Builder view mode to `jnt:page` nodes and to nodes carrying
`jmix:mainResource` only (`ViewModeSelector.jsx` — having a template is not enough). The
mixin is a pure marker, added to the `fmdb:form` supertypes in
`formidable-elements/src/components/Form/definition.cnd`.

### A template for `fmdb:form`

The Page Builder renders the node as a page of its own, which needs a template:

- `formidable-elements/src/templates/Form/default.server.tsx` — `componentType: "template"`,
  `priority: 1`, so it wins over a template set's generic `jmix:mainResource` template (the
  sample template set ships one at priority -1);
- `formidable-elements/src/templates/Layout.tsx` — head and body; the page look (a white page,
  a Moonstone-sized padding around the form) comes from `src/design/page.css`, shipped
  in the form's own stylesheet, every value a variable (see `docs/styling.md`);
- `formidable-elements/src/components/Form/hidden.pageBuilder.server.tsx` — the view the template
  renders, hidden so it is never offered as a view choice, so the standalone page can evolve
  without touching the default view. Same shape as the `cm` view: it delegates to the default
  view through a nested `Render` (the framework idiom — a view component cannot be imported
  and called, it needs the server context the framework passes), **read-only**, which gives
  the Page Builder one module for the form node instead of two nested boxes for the same path.
  Editing the form itself (title, intro, buttons, responses) goes through jContent's own
  **Edit** button, the form being the current node.


The template renders the form itself (`Render node={currentNode}`), not an `Area`:
`fmdb:form` only allows its `fields` and `actions` children, and the goal is to edit the
form, not to compose content around it.

A side effect worth knowing: a form also becomes reachable in live at its own URL
(`/sites/<site>/contents/<form>.html`), rendered through the same template.

The page's top padding is also the room the Page Builder needs: it draws a box bar above its
element when there is room and over its first pixels otherwise, and nothing else sits above
the form on its own page.

## The authoring model (shared with forms on a page)

- **Flat rendering.** Every step is rendered, stacked under its title, with no step
  navigation: clicks in the Page Builder select modules, and a contributor must reach step
  2 without answering step 1. Elements hidden by conditional logic stay visible. The island
  is told (`isEditMode`) so hydration does not hide anything back. Live and preview are
  unchanged.
- **One box per node.** The step and fieldset views render their children read-only
  (`<Render readOnly>`), so the Page Builder gets one module per node — not one for the
  step and one for its children list.
- **New content buttons.** The shared container view (`FormContainer/hidden.logic`) ends
  with `<AddContentButtons/>` in edit mode. jContent renders one button per type the
  container accepts, named after the type (*Form field*, *Form content*, *Form step*), with
  the type's icon. The placeholder must always be there, even on a filled container: jContent
  reads the container's accepted types from it to build the insertion points between the
  children — without it, nothing can be inserted between two steps or two fields.
- **Levels told apart by colour.** One grey base declined per level: blue for fields,
  green for contents, gold for steps, Moonstone light grey for the field list. The mixin
  icons carry it (`content-types-icons` of the module that defines the mixin), and the
  engine's jContent extension registers a `pageBuilderBoxConfig` per container type so the
  box outline and bar carry it too (`formidable-engine/src/javascript/PageBuilder/boxConfigs.ts`).
  In edit mode the form only adds spacing (`src/design/authoring.css`), never colour, so a
  business stylesheet keeps its look while authoring. See `docs/styling.md`.
- **Titled lists.** The `fields` and `actions` lists carry a translatable title shown on
  their box and read-only system names, so a contributor cannot break the names the code
  relies on.

## Tests

- `tests/cypress/e2e/pagebuilder/80-pagebuilder-form-editing.cy.ts`: opening a form from
  the Content Folders through the template, the create buttons of the field list, stacked
  steps.
- `tests/cypress/e2e/validation/38-multistep-flat-in-edit-mode.cy.ts`: the flat authoring
  model on a page (one module per step, placeholders, live unchanged).

`cypress-iframe` is imported in `tests/cypress/support/e2e.js`: the jcontent-cypress Page
Builder page objects depend on it.
