# CSS variables

Every value the base stylesheets hard-code is exposed as a variable: `--fmdb-*` for the core
(`formidable-elements`), `--fmdbext-*` for the extended inputs. Set them on `.fmdb-form` (or on
`:root`) in the template set's stylesheet or in a form's custom CSS, rather than restyling the
selectors. The defaults below are the modules' own; the elements they apply to are named in
[Class hooks](class-hooks.md) and drawn in [Styling a form](README.md#what-the-markup-looks-like).

## Validation

Inline validation messages (`fmdb-validation-error`, injected under an invalid control) and the
invalid state of the control itself (`fmdb-invalid`) — the feature is described in
[Custom validation](../architecture/custom-validation.md).

| Variable | Default | Description |
|---|---|---|
| `--fmdb-validation-error-color` | `#dc2626` | Text colour of the message |
| `--fmdb-validation-error-font-size` | `0.875rem` | Font size of the message |
| `--fmdb-validation-error-mt` | `0.25rem` | Margin above the message |
| `--fmdb-validation-error-padding` | `0` | Padding of the message |
| `--fmdb-validation-error-line-height` | `1.25` | Line height of the message |
| `--fmdb-invalid-border-color` | `#dc2626` | Border of an invalid control |
| `--fmdb-invalid-outline-color` | `#dc2626` | Focus outline of an invalid control |

A softer look, for instance:

```css
.fmdb-form {
	--fmdb-validation-error-color: #b45309;
	--fmdb-validation-error-font-size: 0.8rem;
	--fmdb-invalid-border-color: #b45309;
}
```

## Range slider

| Variable | Default | Description |
|---|---|---|
| `--fmdb-range-gap` | `0.75rem` | Gap between the slider, its end labels and its output |
| `--fmdb-range-end-label-size` | `0.875em` | Font size of the minimum and maximum labels |
| `--fmdb-range-output-min-width` | `3ch` | Minimum width of the current-value output |

## Multi-step navigation

### Navigation bar

| Variable | Default | Description |
|---|---|---|
| `--fmdb-steps-nav-gap` | `0` | Gap between step indicators |
| `--fmdb-steps-nav-mb` | `1.5rem` | Margin below the navigation bar |

### Step indicator

| Variable | Default | Description |
|---|---|---|
| `--fmdb-step-gap` | `0.5rem` | Gap between number and label |
| `--fmdb-step-padding` | `0.5rem 0.75rem` | Padding of each indicator |
| `--fmdb-step-font-size` | `0.875rem` | Font size of the label |
| `--fmdb-step-color` | `#6b7280` | Text color (default state) |
| `--fmdb-step-border` | `#e5e7eb` | Border color (default state) |
| `--fmdb-step-border-width` | `2px` | Bottom border width |
| `--fmdb-step-transition` | `color 0.2s, border-color 0.2s` | Transition on state change |

### Active step

| Variable | Default | Description |
|---|---|---|
| `--fmdb-step-active-color` | `#2563eb` | Text color |
| `--fmdb-step-active-border` | `#2563eb` | Border color |
| `--fmdb-step-active-font-weight` | `600` | Font weight |

### Done step

| Variable | Default | Description |
|---|---|---|
| `--fmdb-step-done-color` | `#16a34a` | Text color |
| `--fmdb-step-done-border` | `#16a34a` | Border color |
| `--fmdb-step-done-font-weight` | `normal` | Font weight |

### Step number badge

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

## Conditional logic

Every element carrying at least one visibility rule is wrapped in `.fmdb-logic-target`; in edit
mode and in jContent's inspection previews the core lifts its form group on a light grey card
(see [Class hooks](class-hooks.md#conditional-logic)). Nothing is drawn in live.

| Variable | Default | Description |
|---|---|---|
| `--fmdb-logic-target-card-display` | `inline-block` | Display of the group in edit mode; `block` keeps it full width |
| `--fmdb-logic-target-card-bg` | `#f3f4f6` | Background of the card |
| `--fmdb-logic-target-card-shadow` | `0 0 0 4px #f3f4f6, 0 2px 4px rgba(0, 0, 0, 0.58)` | Shadow of the card |
| `--fmdb-logic-target-card-radius` | `2px` | Border radius of the card |

### Turning the edit-mode cue off

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

## Authoring spacing

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

## Form actions zone

The zone the form renders under its buttons in edit mode (its structure is in
[Class hooks](class-hooks.md#form-actions-zone-edit-mode)) is authoring chrome, drawn light grey
and dashed; these variables are the intended surface for a site that wants it to look otherwise.

| Variable | Default | Description |
|---|---|---|
| `--fmdb-zone-actions-margin` | `1rem` | Space between the buttons row and the zone (set on the row, so the zone's Page Builder frame hugs the zone) |
| `--fmdb-zone-actions-padding` | `0.75rem` | Inner spacing of the zone |
| `--fmdb-zone-actions-border` | `1px dashed #b8bcc4` | Border of the zone |
| `--fmdb-zone-actions-radius` | `4px` | Corner radius of the zone |
| `--fmdb-zone-actions-bg` | `#f4f5f7` | Background of the zone |
| `--fmdb-zone-actions-color` | `#374151` | Text colour |
| `--fmdb-zone-actions-muted` | `#626977` | Hint, telling parameter and description colour |
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

## Spinner

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

## Extended inputs

Chips are the number-mode rating items, the scale items and the switch buttons; the rating's
default chip colour is the star colour, the others' the focus blue.

| Variable | Default | Description |
|---|---|---|
| `--fmdbext-chip-bg` | `transparent` | Background of a chip |
| `--fmdbext-chip-border` | `#c7c7c7` | Border of a chip |
| `--fmdbext-chip-radius` | `4px` | Corner radius of a chip |
| `--fmdbext-chip-on-bg` | `#4c9aff` (rating: `#f5a623`) | Background and border of a selected chip |
| `--fmdbext-chip-on-fg` | `#fff` | Text colour of a selected chip |
| `--fmdbext-chip-min-width` | `2.25rem` | Minimum width of a scale chip |
| `--fmdbext-focus` | `#4c9aff` | Focus outline of every item |
| `--fmdbext-rating-on` | `#f5a623` | Colour of a selected (and lower) rating icon |
| `--fmdbext-rating-off` | `#c7c7c7` | Colour of an unselected rating icon |
| `--fmdbext-rating-size` | `1.75rem` | Size of a rating icon |
| `--fmdbext-rating-gap` | `0.25rem` | Gap between rating items |
| `--fmdbext-rating-icon` | an SVG `url()` per icon kind | The icon's shape, a mask; the module sets it for `star`, `heart` and `thumb` on `[data-fmdbext-icon]`, so a template set that overrides it does so per kind |
| `--fmdbext-scale-gap` | `0.25rem` | Gap between scale chips |
| `--fmdbext-scale-max-width` | `100%` | Maximum width of the row of chips |
| `--fmdbext-switch-on-bg` | `#36b37e` | Track colour of a switch that is on |
| `--fmdbext-switch-off-bg` | `#c7c7c7` | Track colour of a switch that is off |
| `--fmdbext-switch-knob` | `#fff` | Colour of the switch knob |
| `--fmdbext-end-labels-color` | `#666` | Colour of the minimum and maximum labels |
| `--fmdbext-warning-fg` | `#7a4d05` | Text colour of an edit-mode warning |
| `--fmdbext-warning-bg` | `#fff3d6` | Background of an edit-mode warning |
| `--fmdbext-warning-border` | `#f0c36d` | Border of an edit-mode warning |
