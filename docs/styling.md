# Styling a form

How a template set — or, exceptionally, a form's own custom CSS — styles the forms rendered
by `formidable-elements`: the stable class hooks, the CSS variables, and the edit-mode cues.

## Where styles belong

A form's look normally belongs to the **site template set**, like any other content: ship the
rules in its stylesheet, targeting the class hooks below. The **Custom CSS** field of a form
(`Style` section in Content Editor) is for the exceptional case where the site stylesheet cannot
be changed. Its content is injected in a `<style>` element next to the form, in live, preview
and edit mode alike, and it is **not scoped to the form**: a broad rule affects the whole page.

The base stylesheet of `formidable-elements` (`dist/assets/style.css`) only carries functional
rules — validation messages, the multi-step navigation, the spinner, the edit-mode cues — and
every value it hard-codes is exposed as a CSS variable, so a template set overrides values
without fighting selectors.

## Class hooks

Stable class names, rendered server-side and kept across releases:

| Class | Element |
|---|---|
| `fmdb-form` | The `<form>`; carries `data-fmdb-edit-mode="true"` in the Page Builder. In jContent's inspection previews it is a `div` carrying `data-fmdb-cm-view="true"` (see below) — avoid qualifying rules with the tag (`form.fmdb-form`) |
| `fmdb-form-intro` | The introduction rich text |
| `fmdb-form-group` | The wrapper of one field (label + control + help); `fmdb-radio-group`, `fmdb-checkbox-group`, `fmdb-captcha` refine it |
| `fmdb-form-label`, `fmdb-file-label`, `fmdb-radio-label`, `fmdb-checkbox-label` | Field labels |
| `fmdb-group-legend` | Legend of a radio or checkbox group |
| `fmdb-group-items`, `fmdb-group-item` | Options of a radio or checkbox group |
| `fmdb-required-indicator` | The `*` of a required field |
| `fmdb-form-control` | Inputs, selects and textareas |
| `fmdb-fieldset`, `fmdb-fieldset-legend` | A fieldset and its legend |
| `fmdb-step`, `fmdb-step-title`, `fmdb-step-intro`, `fmdb-steps-nav`, `fmdb-step-indicator`, `fmdb-step-label` | Multi-step structure and navigation |
| `fmdb-form-actions`, `fmdb-btn`, `fmdb-btn-primary`, `fmdb-btn-secondary`, `fmdb-new-form-btn`, `fmdb-next-btn`, `fmdb-prev-btn` | Buttons, including the multi-step navigation pair |
| `fmdb-form-help` | Help text under a field (present on nearly every field) |
| `fmdb-range`, `fmdb-range-row`, `fmdb-range-output`, `fmdb-range-end-label` | Range slider structure (variables: `--fmdb-range-gap`, `--fmdb-range-output-min-width`, `--fmdb-range-end-label-size`) |
| `fmdb-message`, `fmdb-message-content`, `fmdb-message-success`, `fmdb-message-error`, `fmdb-message-maintenance` | Submission feedback |
| `fmdb-file-*` (`-input-container`, `-list`, `-item`, `-name`, `-size`, `-remove`, `-selection-note`) | File field and its selected files |
| `fmdb-validation-error`, `fmdb-invalid` | Inline validation (see [Custom validation](custom-validation.md)) |
| `fmdb-logic-target` | Wrapper of an element driven by conditional logic (see below) |
| `fmdb-spinner` | The submission overlay |

Every element wrapper also exposes `data-fmdb-node-name`, `data-fmdb-node-id` and
`data-fmdb-node-type`, for rules that target one field by name:
`.fmdb-form [data-fmdb-node-name="email"] { … }`.

## Validation variables

`--fmdb-validation-error-*` and `--fmdb-invalid-*` are documented with the feature, in
[Custom validation](custom-validation.md#styling).

## Form variables

### Multi-step navigation

#### Navigation bar

| Variable | Default | Description |
|---|---|---|
| `--fmdb-steps-nav-gap` | `0` | Gap between step indicators |
| `--fmdb-steps-nav-mb` | `1.5rem` | Margin below the navigation bar |

#### Step indicator

| Variable | Default | Description |
|---|---|---|
| `--fmdb-step-gap` | `0.5rem` | Gap between number and label |
| `--fmdb-step-padding` | `0.5rem 0.75rem` | Padding of each indicator |
| `--fmdb-step-font-size` | `0.875rem` | Font size of the label |
| `--fmdb-step-color` | `#6b7280` | Text color (default state) |
| `--fmdb-step-border` | `#e5e7eb` | Border color (default state) |
| `--fmdb-step-border-width` | `2px` | Bottom border width |
| `--fmdb-step-transition` | `color 0.2s, border-color 0.2s` | Transition on state change |

#### Active step

| Variable | Default | Description |
|---|---|---|
| `--fmdb-step-active-color` | `#2563eb` | Text color |
| `--fmdb-step-active-border` | `#2563eb` | Border color |
| `--fmdb-step-active-font-weight` | `600` | Font weight |

#### Done step

| Variable | Default | Description |
|---|---|---|
| `--fmdb-step-done-color` | `#16a34a` | Text color |
| `--fmdb-step-done-border` | `#16a34a` | Border color |
| `--fmdb-step-done-font-weight` | `normal` | Font weight |

#### Step number badge

| Variable | Default | Description |
|---|---|---|
| `--fmdb-step-number-size` | `1.5rem` | Width and height |
| `--fmdb-step-number-radius` | `50%` | Border radius |
| `--fmdb-step-number-font-size` | `0.75rem` | Font size |
| `--fmdb-step-number-font-weight` | `700` | Font weight |
| `--fmdb-step-number-bg` | `#e5e7eb` | Background (default state) |
| `--fmdb-step-number-color` | `#374151` | Text color (default state) |
| `--fmdb-step-active-number-bg` | `#2563eb` | Background (active state) |
| `--fmdb-step-active-number-color` | `#fff` | Text color (active state) |
| `--fmdb-step-done-number-bg` | `#16a34a` | Background (done state) |
| `--fmdb-step-done-number-color` | `#fff` | Text color (done state) |

### Conditional logic

Every element carrying at least one visibility rule is wrapped in `.fmdb-logic-target`
(server-rendered, so present in live, preview and edit mode; the current state stays in
`data-fmdb-logic-hidden`). Nothing is drawn in live. In edit mode
(`form[data-fmdb-edit-mode="true"]`) and in jContent's inspection previews
(`[data-fmdb-cm-view="true"]`, the other surface that shows logic-hidden fields) the core
lifts the element's form group on a light grey card with a soft shadow, shrunk to its
content, so contributors spot conditional fields.

| Variable | Default | Description |
|---|---|---|
| `--fmdb-logic-target-card-display` | `inline-block` | Display of the group in edit mode; `block` keeps it full width |
| `--fmdb-logic-target-card-bg` | `#f3f4f6` | Background of the card |
| `--fmdb-logic-target-card-shadow` | `0 0 0 4px #f3f4f6, 0 2px 4px rgba(0, 0, 0, 0.58)` | Shadow of the card |
| `--fmdb-logic-target-card-radius` | `2px` | Border radius of the card |

#### Turning the edit-mode cue off

To show nothing special for conditional fields, a template set or the form's own custom
CSS resets the three visual variables **and** the display — the card is an `inline-block`,
so leaving that one out would keep the group shrunk to its content in edit mode:

```css
.fmdb-form {
	--fmdb-logic-target-card-display: block;
	--fmdb-logic-target-card-bg: transparent;
	--fmdb-logic-target-card-shadow: none;
}
```

To draw a cue of your own instead, keep that reset and style the wrapper, which stays in
place in every mode:

```css
.fmdb-form[data-fmdb-edit-mode="true"] .fmdb-logic-target {
	outline: 1px dotted currentColor;
	outline-offset: 4px;
}
```

### Inspection previews (cm view)

jContent's scriptless preview surfaces (the preview drawer, the Content Editor preview)
render the `cm` view: an inspection of the form's content — every step stacked under its
title, logic-driven fields visible on their card, no buttons. There the `fmdb-form` hook is
a `div` (nothing submits), and it carries `data-fmdb-cm-view="true"` instead of the edit-mode
attribute: a stylesheet targets that surface with
`.fmdb-form[data-fmdb-cm-view="true"] { … }`. The form's own CSS and this module's
stylesheet apply; a template set's stylesheet only reaches previews opened from a page
(jContent injects the hosting page's CSS there).

### Authoring spacing

A multi-step form is authored flat, so three levels stack on one page — the field list, its
steps (or fieldsets), their fields — each with its own Page Builder **New content** button.
The Page Builder boxes colour the grouping levels: steps and fieldsets share one gold (two
ways of grouping fields, one colour), while the field list keeps jContent's default box —
it is the neutral frame, not a level to spot. The palette rides on the boxes and on the
mixin icons of the create buttons (blue for fields, green for contents, gold for steps);
the type icons stay neutral monochrome. In edit mode
(`form[data-fmdb-edit-mode="true"]`) the core only adds
room to reach each level: a padding on the field list (`.fmdb-form-fields`, a wrapper
present in edit mode only) so it can be clicked around its steps, a padding and a margin on
each step, and a margin on each field so the insertion buttons between two fields do not cover
the previous one. No colour is drawn, so a business stylesheet keeps its look while authoring;
nothing changes in live.

| Variable | Default | Description |
|---|---|---|
| `--fmdb-zone-list-padding` | `0.75rem` | Inner spacing of the field list |
| `--fmdb-zone-step-padding` | `0.75rem 1rem` | Inner spacing of a step |
| `--fmdb-zone-step-margin` | `0.75rem 0` | Outer spacing of a step |
| `--fmdb-zone-field-margin` | `0.75rem` | Vertical spacing of a field (form group) or fieldset |

To remove the spacing, set the four variables to `0` on `.fmdb-form`.

### Form actions zone

Actions run after the submission and have no place in the visitor's form, so on a page they
were invisible while authoring. In edit mode the form renders its action list as a zone of its
own, under the buttons: a header, one compact card per action — its rank in the execution
order (the list is orderable: dragging a card in the Page Builder reorders the pipeline), the
type icon, the contributor's title with the action's key parameter — the first small text or choice property its type declares after the title (recipient, forward target), a choice shown by its label —
and smaller, the type description its module declares for the Content Editor (the
`<type>.ui.tooltip` key of the module's resource bundle) — then the list's own **New Form
Action** button (the placeholder's accepted type is the `fmdbmix:formAction` mixin, so jContent
shows one button and then the type chooser). A form without any action gets a warning instead
of the cards: its submissions are neither stored nor sent.

Unlike the spacing above, the zone IS drawn (`aside.fmdb-authoring-actions`, light grey, dashed):
it is authoring chrome, not the visitor's form, and must read as such; a business stylesheet
does not style it. To order it after the buttons, the form is a flex column while authoring
(`form[data-fmdb-edit-mode="true"]`); its children were stacked blocks already. Nothing of the
zone exists in live, preview or the `cm` view.

| Variable | Default | Description |
|---|---|---|
| `--fmdb-zone-actions-margin` | `1rem` | Space between the buttons row and the zone (set on the row, so the zone's Page Builder frame hugs the zone) |
| `--fmdb-zone-actions-padding` | `0.75rem` | Inner spacing of the zone |
| `--fmdb-zone-actions-border` | `1px dashed #b8bcc4` | Border of the zone |
| `--fmdb-zone-actions-radius` | `4px` | Corner radius of the zone |
| `--fmdb-zone-actions-bg` | `#f4f5f7` | Background of the zone |
| `--fmdb-zone-actions-color` | `#374151` | Text colour |
| `--fmdb-zone-actions-muted` | `#6b7280` | Hint, telling parameter and description colour |
| `--fmdb-zone-actions-font` | `400 13px/1.35 system-ui, …` | Font of the zone (the cards' second line is 11px) |
| `--fmdb-zone-actions-card-bg` | `#ffffff` | Background of an action card |
| `--fmdb-zone-actions-card-border` | `#e5e7eb` | Border of an action card |
| `--fmdb-zone-actions-rank-bg` | `#e5e7eb` | Background of the rank badge |
| `--fmdb-zone-actions-rank-color` | `#374151` | Text colour of the rank badge |
| `--fmdb-zone-actions-warning-bg` | `#fff7ed` | Background of the "no action" warning |
| `--fmdb-zone-actions-warning-border` | `#f59e0b` | Left border of the warning |
| `--fmdb-zone-actions-warning-color` | `#9a3412` | Text colour of the warning |

The Page Builder box colours are a jContent UI extension registered by the engine
(`pageBuilderBoxConfig`), not CSS, so a template set cannot override them; purple is left to
jExperience and orange to jContent's warnings.

## Spinner variables

### Overlay

| Variable | Default | Description |
|---|---|---|
| `--fmdb-spinner-overlay-min-height` | `120px` | Minimum height of the overlay container |
| `--fmdb-spinner-overlay-bg` | `rgba(255, 255, 255, 0.95)` | Background color |
| `--fmdb-spinner-overlay-backdrop` | `blur(2px)` | Backdrop filter |
| `--fmdb-spinner-overlay-radius` | `8px` | Border radius |
| `--fmdb-spinner-overlay-margin` | `1rem 0` | Margin |

### Spinner

| Variable | Default | Description |
|---|---|---|
| `--fmdb-spinner-gap` | `0.75rem` | Gap between circle and text |

### Circle

| Variable | Default | Description |
|---|---|---|
| `--fmdb-spinner-color` | `#007bff` | Active arc color |
| `--fmdb-spinner-track-color` | `#f3f3f3` | Track (background arc) color |
| `--fmdb-spinner-border-width` | `3px` | Default border width (overridden by size) |
| `--fmdb-spinner-duration` | `1s` | Rotation animation duration |

#### Sizes

| Variable | Default | Description |
|---|---|---|
| `--fmdb-spinner-size-small` | `24px` | Width & height (small) |
| `--fmdb-spinner-border-width-small` | `2px` | Border width (small) |
| `--fmdb-spinner-size-medium` | `40px` | Width & height (medium) |
| `--fmdb-spinner-border-width-medium` | `3px` | Border width (medium) |
| `--fmdb-spinner-size-large` | `56px` | Width & height (large) |
| `--fmdb-spinner-border-width-large` | `4px` | Border width (large) |

### Text

| Variable | Default | Description |
|---|---|---|
| `--fmdb-spinner-text-color` | `#666` | Text color |
| `--fmdb-spinner-text-weight` | `500` | Font weight |
| `--fmdb-spinner-text-size-small` | `0.75rem` | Font size (small) |
| `--fmdb-spinner-text-size-medium` | `0.9rem` | Font size (medium) |
| `--fmdb-spinner-text-size-large` | `1rem` | Font size (large) |

### Responsive (≤ 768px)

| Variable | Default | Description |
|---|---|---|
| `--fmdb-spinner-overlay-min-height-mobile` | `100px` | Overlay minimum height |
| `--fmdb-spinner-overlay-margin-mobile` | `0.5rem 0` | Overlay margin |
| `--fmdb-spinner-size-medium-mobile` | `32px` | Circle width & height (medium) |
| `--fmdb-spinner-border-width-medium-mobile` | `2px` | Border width (medium) |
| `--fmdb-spinner-size-large-mobile` | `48px` | Circle width & height (large) |
| `--fmdb-spinner-border-width-large-mobile` | `3px` | Border width (large) |
| `--fmdb-spinner-text-size-medium-mobile` | `0.8rem` | Font size (medium) |
| `--fmdb-spinner-text-size-large-mobile` | `0.9rem` | Font size (large) |
