# Form CSS Variables

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

Every element carrying at least one visibility rule is wrapped in `.fmdb-logic-target`
(server-rendered, so present in live, preview and edit mode; the current state stays in
`data-fmdb-logic-hidden`). Nothing is drawn in live. In edit mode
(`form[data-fmdb-edit-mode="true"]`) the core lifts the element's form group on a light grey
card with a soft shadow, shrunk to its content, so contributors spot conditional fields.

| Variable | Default | Description |
|---|---|---|
| `--fmdb-logic-target-bg` | `transparent` | Background of the whole conditional element, in every mode |
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
