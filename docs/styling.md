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
| `fmdb-form` | The `<form>`; carries `data-fmdb-edit-mode="true"` in the Page Builder |
| `fmdb-form-intro` | The introduction rich text |
| `fmdb-form-group` | The wrapper of one field (label + control + help); `fmdb-radio-group`, `fmdb-checkbox-group`, `fmdb-captcha` refine it |
| `fmdb-form-label`, `fmdb-file-label`, `fmdb-radio-label`, `fmdb-checkbox-label` | Field labels |
| `fmdb-group-legend` | Legend of a radio or checkbox group |
| `fmdb-group-items`, `fmdb-group-item` | Options of a radio or checkbox group |
| `fmdb-required-indicator` | The `*` of a required field |
| `fmdb-form-control` | Inputs, selects and textareas |
| `fmdb-fieldset`, `fmdb-fieldset-legend` | A fieldset and its legend |
| `fmdb-step`, `fmdb-step-title`, `fmdb-step-intro`, `fmdb-steps-nav`, `fmdb-step-indicator`, `fmdb-step-label` | Multi-step structure and navigation |
| `fmdb-form-actions`, `fmdb-btn`, `fmdb-btn-primary`, `fmdb-btn-secondary`, `fmdb-new-form-btn` | Buttons |
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
(`form[data-fmdb-edit-mode="true"]`) the core lifts the element's form group on a light grey
card with a soft shadow, shrunk to its content, so contributors spot conditional fields.

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

### Authoring spacing

A multi-step form is authored flat, so three levels stack on one page — the field list, its
steps (or fieldsets), their fields — each with its own Page Builder **New content** button.
The Page Builder boxes carry the level's colour (blue for fields, gold for steps, Moonstone
light grey for the field list; the mixin icons on the create buttons use the same palette,
green for contents). In edit mode (`form[data-fmdb-edit-mode="true"]`) the core only adds
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

The Page Builder box colours are a jContent UI extension registered by the engine
(`pageBuilderBoxConfig`), not CSS, so a template set cannot override them; purple is left to
jExperience and orange to jContent's warnings.

### Form page

A form opened as a page of its own (from the Content Folders in the Page Builder, or at its
own URL) renders on a white page, the form centred at 80% of the width (no title: jContent shows it
above the Page Builder, and the form has its own intro). A template set that wants another page declares its own template for `fmdb:form` (the
Formidable one renders the hidden `hidden.page` view; `fullPage` is the plain full-page view).

| Variable | Default | Description |
|---|---|---|
| `--fmdb-page-bg` | `#fff` | Page background |
| `--fmdb-page-color` | `#1f2933` | Page text colour |
| `--fmdb-page-font` | `system-ui, …, sans-serif` | Page font stack |
| `--fmdb-page-sheet-width` | `80%` | Width of the content column |
| `--fmdb-page-sheet-max-width` | `64rem` | Maximum width of the column |
| `--fmdb-page-sheet-margin` | `0 auto` | Column margin (centred) |
| `--fmdb-page-sheet-padding` | `2.5rem 0 3rem` | Column inner spacing |
| `--fmdb-page-sheet-bg` | `transparent` | Column background (set it for a card look) |
| `--fmdb-page-sheet-radius` | `0` | Column corner radius |
| `--fmdb-page-sheet-shadow` | `none` | Column shadow |
| `--fmdb-page-content-padding` | `1.5rem 0` | Spacing around the form inside the column (the form's own clickable strip in the Page Builder) |

Below 768px the column takes the full width.

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
