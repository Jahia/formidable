# SelectOptions usability — make row columns self-explanatory

Status: **Option B implemented** — branch `feat/select-options-usability`, commit 2bf15ee, PR #168 (2026-07-29).

Open point (resume here): the double `<br/>` paragraph separation in the property tooltips did not show on the last manual check — the rendered `<span>` still had single `<br>` and the *bold* change (earlier deploy) was visible, so either the instance served a stale formidable-elements bundle or the tooltip renderer collapses consecutive `<br/>`. Local server crashed before `docker exec` could inspect the deployed properties. To verify: redeploy, then check the deployed bundle content and the rendered tooltip; if the renderer collapses `<br/><br/>`, switch to a spacer like `<p>` blocks or `<br/>&nbsp;<br/>`.

## Problem

In Content Editor, each option row of the `SelectOptions` selector type shows a bare Switch + two inputs. Nothing on screen says what each column means; the explanation only lives in the property tooltip. Users don't know that the switch = "selected by default", first input = value sent to server, second input = label displayed to user.

Scope: the selector is used by **three** fieldsets (all get any fix for free, since the component and its i18n are shared):
- `fmdb_select.options` (fieldset `fmdb_select.json`)
- `fmdb_radio.choices` (fieldset `fmdb_radio.json`)
- `fmdb_checkbox.choices` (fieldset `fmdb_checkbox.json`)

Component: `formidable-engine/src/javascript/SelectOptions/SelectOptionsCmp.tsx` (registered `supportMultiple: false` in `init.tsx`). Leftover debug `console.log` in `handleChange` (line ~36) to remove whichever option is chosen.

## Option A — header row above the first option (original idea)

Display column names once at the top, after the first element is added:
`Is selected by default | Value | Label`.

Technical analysis (done 2026-07-29):
- jContent's `MultipleField.jsx` instantiates the cmp **once per row** with `id = "options[0]"`, `"options[1]"`, … (`jcontent/src/javascript/ContentEditor/editorTabs/EditPanelContent/FormBuilder/Field/MultipleField.jsx:49`). No container/header hook exists in the `selectorType` registry.
- Feasible approach: detect the first row via `/\[0\]$/.test(id)` and render the header inside that instance. Header appears exactly once, only when ≥1 row exists, survives remove/reorder (indices recomputed each render). Only cosmetic quirk: header may move during an active drag, snaps back on drop.
- Rejected alternatives: `supportMultiple: true` (would require reimplementing CE's add/remove/drag UI), CSS `::before` (no i18n), DOM portal (fragile).
- Main cost: column alignment — "Is selected by default" is much wider than the Switch, needs a shared fixed-width first column (SCSS) applied to both header cell and switch cell.
- i18n: new keys in `formidable-engine` locales (en/fr), e.g. `selectOptions.header.selected`.

## Option B — lighter: placeholders + switch tooltip + tooltip restyle (user idea, 2026-07-29)

Maybe sufficient, and almost a pure i18n/properties change:

1. **Placeholders** reuse the wording already in the property tooltips:
   - value input → `Value sent to server`
   - label input → `Label displayed to user`
   Just change `selectOptions.value` / `selectOptions.label` in `formidable-engine/src/main/resources/javascript/locales/{en,fr}.json` (the component already uses them as placeholders). Guidance shows exactly on empty rows — the moment users need it.
2. **Switch tooltip**: the Switch already receives `title={t('selectOptions.selected')}` — change the translation from "Selected" to `If enabled, the option is selected by default`.
2bis. **Input tooltips** (user idea, 2026-07-29): also pass `title` to both `Input`s with the same content as their placeholder, so the info stays available on hover **after** the fields are filled — this neutralizes the main placeholder weakness. Verified feasible: Moonstone's `ControlledBaseInput` spreads unknown props (`{...props}`) directly onto the native `<input>` (moonstone `src/components/Input/BaseInput/ControlledBaseInput.tsx`), and the component already passes `name` through the same path, so `title` reaches the DOM and TS accepts it. Reuse the same i18n keys: `title={t('selectOptions.value')}` / `title={t('selectOptions.label')}` — one string per column drives placeholder + title.
3. **Property tooltip restyle** (`formidable-elements/settings/resources/formidable-elements*.properties`, 4 locales: default/fr/de/es): make the info stand out — put the element names in `<b>` (currently the descriptions are `<i>` and the names plain), possibly reorder so the row description comes first and the "empty option" Tip (select only) stays last. Keys: `fmdb_select.options.ui.tooltip`, `fmdb_radio.choices.ui.tooltip`, `fmdb_checkbox.choices.ui.tooltip`.

Trade-offs vs Option A:
- (+) No component/layout change, no first-row detection, no alignment problem; covers select/radio/checkbox uniformly.
- (+) Placeholder wording is generic enough for the radio/checkbox "choices" vocabulary.
- (−) Placeholders disappear once the fields are filled — mitigated by the input `title` tooltips (2bis); all hover guidance relies on native `title` (no delay-free styled tooltip, not reachable by keyboard/touch).
- (−) Longer placeholders may truncate in narrow panels — acceptable, tooltip still has the full story.

## Current lean

Start with **Option B** (cheap, reversible, mostly locale files); keep Option A as a follow-up if feedback says the columns are still unclear. The two are compatible — A can be added later on top of B.
