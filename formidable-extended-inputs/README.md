# formidable-extended-inputs

Optional add-on field types for Formidable (see [issue #158](https://github.com/Jahia/formidable/issues/158)).

Keeps the core element set lean and HTML5-native while richer widgets live in this optional module.
It follows the third-party extension contract documented in
[`docs/extension/how-to-extend-views-and-elements-from-third-party-module.md`](../docs/extension/how-to-extend-views-and-elements-from-third-party-module.md)
and serves as a real-world validation of it.

## Field types

| Type                         | Description                                                                                              | Submitted value        |
| ---------------------------- | -------------------------------------------------------------------------------------------------------- | ---------------------- |
| `fmdbext:card`               | Single choice shown as a grid of clickable cards; options as on the radio (manual, source, categories, contents) | the picked option's value |
| `fmdbext:rating`             | Icon rating (stars / hearts / thumbs / number chips), configurable max (2–10), optional end labels       | number `1..max`        |
| `fmdbext:scale`              | Linear scale as number chips, configurable min/max/step, optional end labels                             | number                 |
| `fmdbext:scale` (view `nps`) | Standard Net Promoter Score presentation — forces 0–10, translated default end labels                    | number `0..10`         |
| `fmdbext:switch`             | Boolean yes/no; `toggle` or `buttons` display mode, presentation-only state labels                       | boolean `true`/`false` |
| `fmdbext:consent`            | Explicit terms/GDPR consent checkbox with rich-text statement and optional link to a terms page/document | `true`                 |

All types extend `fmdbmix:element` + `fmdbmix:validationMessages`, use the standard `fmdb-` markup
conventions (`name` = node name, `id` = `<type>-<uuid>`, `fmdb-form-group` wrapper), and are pure
SSR — no client Islands, all interactivity is native HTML + CSS.

## Design decisions (from issue #158)

- **Rating**: no half-steps (accessible radio-group model). Value stored as plain number; aggregation out of scope.
- **NPS**: implemented as a _view_ on the scale type, not a separate type.
- **Switch**: value is boolean; in `toggle` mode unchecked submits nothing (standard checkbox semantics) — use `buttons` mode when an explicit `false` answer is required.
- **Consent**: stored value is simply `true`; consent metadata (timestamp/version) is out of scope. The linked terms target **must be guest-readable**.

## Card field (issue [#421](https://github.com/Jahia/formidable/issues/421))

- **Same mixins as `fmdb:radio`**, `fmdbmix:choiceField` among them: the options editor and every options mode, the
  server-side check of the submitted value, conditional logic and the profile mapping work with no code of their own.
- **A card shows its option's label only.** An option holds a value, a label and a default selection; a description
  or an image would need a change to the options model and to its editor.
- **Native radios, the label drawn as the card**: the keyboard, the validation messages and the jExperience read-only
  lock (which takes the pointer off radios and labels) work as on the radio group.
- **The options come from `@jahia/formidable-library`**, as for the built-in choice fields: `resolveFieldOptions`
  from its `/server` entry point, `OptionsSourceError` with this module's message when a source cannot deliver.
- **`data-fmdb-advance-on-change`** on the group says one answer is enough: a form showing one field at a time
  ([#423](https://github.com/Jahia/formidable/issues/423)) may move on once a card is picked. Nothing reads it yet.

## Known limitations

- Content-type icons are placeholders (copies of the generic component icon).

(Two earlier limitations were lifted before release: the fields now carry the engine's
semantic mixins — `fmdbmix:numberField` / `fmdbmix:booleanField`, validated server-side
with their bounds — and they can be used as conditional-logic sources.)

## Build & deploy

Same workflow as `formidable-elements`:

```bash
yarn install          # from repo root
cd formidable-extended-inputs
yarn build            # tsc + vite build + yarn pack -> dist/package.tgz
yarn deploy           # jahia-deploy to the local Jahia
```

Requires `formidable-elements` and `formidable-engine` to be deployed first.
