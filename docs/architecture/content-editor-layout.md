# Content Editor layout: what a field's editor shows where

The editor of a form field used to show every property of the field type in the **Content** section, in
the order of the CND, with the rarely used HTML attributes behind an "Advanced settings" switch at the
bottom and the validation messages in a section of their own. A contributor filling a text field scrolled
past ten settings to reach the one they wanted. Since 2026-10-01 the field editors follow one layout, laid
out by Content Editor form overrides and not by the CND: **Content keeps what a contributor almost always
fills**, and **every other setting sits in the Field settings section**, in fieldsets that are the same
from one type to the next. Nothing changes in the repository: no property moves, no migration, no upgrade
step — a form saved yesterday opens in the new layout today.

This page is the map of that layout (which setting sits where, and why), the table of the section ranks
every override of this repository uses (the question of [formidable#311](https://github.com/Jahia/formidable/issues/311)),
the rules of the Content Editor the overrides rely on, and the CI check that keeps every property placed.
How a third-party module joins the layout with a setting of its own is in the
[extension guide](../extension/how-to-extend-views-and-elements-from-third-party-module.md) (the
`helpTextPosition` sample).

## The layout

```
Content                 Title · System name · Required                  the field's own fieldset (<main>)
                        Field actions                                   the switch (docs/architecture/field-actions.md)
(Content list & ordering: the editor's own block, shown once the switch created the actions list — hidden)
Field settings  1.05    1 Help & presentation                           help text, title attribute, slider end labels
                        2 Value & input                                 placeholder, default value, mask, pattern,
                                                                        autocomplete, spell check, step, suggested values…
                        3 Constraints                                   lengths, numeric bounds, date bound modes…
                        3.1–3.4   the date bound fieldsets              fixed / relative minimum and maximum, shown
                                                                        when their mode is chosen
                        4 Behaviour                                     read-only, disabled, autofocus
                        5 Validation messages                           the messages replacing the browser's
Logic           1.10    unchanged
jExperience     1.15    unchanged (formidable-jexperience-engine)
Metadata, Layout, List ordering, Visibility: the platform's own sections, unchanged
```

The Content Editor has two levels, the section (a collapsible block) and the fieldset (a titled group
inside it). There is no third level: the "sub-sections" of the design are titled fieldsets. A fieldset
with no `labelKey` renders its fields without a title.

**What Content keeps** is the same for every type: the title (the label of the field), the system name
(the platform puts it there), **Required**, and the **Field actions** switch at the end — a capability of
the field rather than a setting (it opens a zone under the field in the Page Builder), kept there because a
lone switch between two titled groups of settings reads as lost, and because the editor shows the list it
creates right under Content anyway (below). A property that *defines* the field keeps its place there
too — the options and their mode on a choice field, the minimum and maximum of a slider (`fmdb:inputRange`
keeps `minValue` and `maxValue` in Content: they draw the control), the declaration of a consent field —
which is decided type by type as each family moves (table below).

**Field settings** gathers the rest, in five fieldsets whose names, label keys and ranks are the same in
every override, so that a type's own file, its mixins' files and a third-party module's file all land in
the same groups:

| Rank | Fieldset              | Name                  | Label key                           | Holds |
| ---- | --------------------- | --------------------- | ----------------------------------- | ----- |
| 1    | Help & presentation   | `helpAndPresentation` | `fmdb.fieldset.helpAndPresentation` | `helpText` (rank 1), the slider's `minLabel` / `maxLabel` (1.5, 1.6), the HTML `title` attribute (2), a third-party setting about the help text (the sample puts `helpTextPosition` at 1.5) |
| 2    | Value & input         | `valueAndInput`       | `fmdb.fieldset.valueAndInput`       | by frequency of use: `placeholder` (1), `defaultValue` (2), `mask` or `step` (3), `pattern` (4), `autocomplete` (5), `spellcheck` (6), `wrap` (7), `list` (7), `rows` (8), `resize` (9) |
| 3    | Constraints           | `constraints`         | `fmdb.fieldset.constraints`         | `minLength`, `maxLength`; `minValue`, `maxValue` (number); `minBoundMode`, `maxBoundMode` (date, datetime) |
| 3.1–3.4 | the date bound fieldsets | `fmdbmix:fixedMin<Kind>`, `fmdbmix:relativeMin<Kind>`, `fmdbmix:fixedMax<Kind>`, `fmdbmix:relativeMax<Kind>` | the mixins' labels (engine) | the fixed date or the relative offset of each bound; dynamic fieldsets without a switch, shown when the mode above selects them |
| 4    | Behaviour             | `behaviour`           | `fmdb.fieldset.behaviour`           | `readonly`, `disabled`, `autofocus` |
| 5    | Validation messages   | `validationMessages`  | `fmdb.fieldset.validationMessages`  | `msgValueMissing` (1), the text messages (2–5), the range messages (6–9) |

The label keys live in the bundle of `formidable-elements`; a module joining the layout resolves them
through its dependency on it.

**The children block.** Switching Field actions on autocreates the `actions` list under the field, and a
node with children makes the editor show its own **Content list & ordering** block, which jContent places
**right after Content whatever its rank** (`FormBuilder.jsx` takes the `listOrdering` section out of the
rank order and reinserts it in second position) and opens by default (`nt_base.json`, `"expanded": true`).
Its position cannot be changed from a module; whether it shows can: the engine's override on
`fmdbmix:fieldActions` declares the section with `"hide": true`, so the block never shows on a field — one
node, nothing to order; the actions are managed in the Page Builder zone under the field. **The scope is the
mixin's**: the override reaches exactly the elements that can carry field actions — the field types with a
value, switch on or off — and nothing else. A form, a step, a fieldset or a button keeps its Content list &
ordering block, their children (steps, fields) being worth ordering. Folding the block instead
(`"expanded": false` on the section) was the first cut and is the other flag at hand.

**Hidden for good.** Three HTML attributes of the text input and three of the textarea have no effect, or
an effect nothing reads, in a Formidable form, and are no longer offered: `form` (associates an input placed
outside its `<form>` — ours are always inside), `dirname` (makes the browser post `<name>.dir=ltr|rtl`, which
the engine never reads), `size` and `cols` (presentational, superseded by any stylesheet sizing the controls —
the module ships none, the sample theme sets `width: 100%`). They stay in the CND, marked `hidden`, and a
value already set still renders; a `hidden` property is no field of the editor at all, which is how the CI
check tells "hidden on purpose" from "forgotten".

**Mask and pattern.** The text input derives its HTML `pattern` from the mask (`maskToPattern`) unless the
contributor types a pattern of their own; both tooltips say so, and the two sit side by side in Value &
input, the mask first.

### Where each family stands

| Family | Types | Status |
| ------ | ----- | ------ |
| Text | `fmdb:inputText`, `fmdb:inputEmail`, `fmdb:textarea` | **done** (2026-10-01): Content = title, system name, required; the "advanced settings" mixins dissolved into the fieldsets as supertypes of their types (no switch, no mixin to add on save; `AdvancedSettingsMixinMigration` drops the redundant one from older fields)|
| Numbers and dates | `fmdb:inputNumber`, `fmdb:inputRange`, `fmdb:inputDate`, `fmdb:inputDatetimeLocal` | **done** (2026-10-01): the slider keeps `minValue` and `maxValue` in Content; the number's bounds go to Constraints; the date bound modes go to Constraints with their dynamic fieldsets right after (3.1–3.4); `step` in Value & input; the number and slider "advanced settings" mixins dissolved (hidden storage keeps `form`) |
| Choices and files | `fmdb:select`, `fmdb:radio`, `fmdb:checkbox`, `fmdb:inputFile`, `fmdb:inputColor`, `fmdb:inputHidden` | to do — the options mode and its dynamic fieldsets stay in Content next to the options |
| Extended inputs | `fmdbext:consent`, `fmdbext:rating`, `fmdbext:scale`, `fmdbext:switch` | to do |

Until a family moves, its types keep their properties in Content and show the Field settings section with
what applies to every field already: the validation messages.

### Numbers and dates

The slider (`fmdb:inputRange`) is the one type so far keeping settings beside Required in Content: `minValue`
and `maxValue` draw the control, a slider without them is not a slider. Its end labels (`minLabel`,
`maxLabel`) are presentation, next to the help text. The number input keeps nothing but Required: its
`minValue` / `maxValue` are constraints.

The date and datetime bounds are **modes** (`minBoundMode`, `maxBoundMode` on the `fmdbmix:dateBounds` /
`fmdbmix:datetimeBounds` contracts of the engine: none, a fixed date, today, a relative offset), each mode
adding a `jmix:dynamicFieldset` mixin that carries its value (`docs/architecture/custom-validation.md`). The
modes sit in Constraints; the four dynamic fieldsets of each contract follow at ranks 3.1 to 3.4, each moved
by its own override listing its fields (rule 2 below applies to a dynamic fieldset exactly as to a switch:
the fields move, the generated copy in Content empties and falls). They have no switch (`jmix:dynamicFieldset`
extends `jmix:templateMixin`), so the editor shows one only once its mode is chosen — as before, in another
section. The `fieldsets/` overrides that make the relative offsets mandatory keep working: they merge at
priority 1, before the `forms/` overrides move the fields, and the moved field keeps what they set.

## The section ranks

Sections order by rank, and a rank is global to the form: two overrides naming the same section merge into
one, two sections with the same rank sort by name. The ranks in use, read from the overrides of this
repository (an override declares its section's rank in every file that names the section, so the merge
cannot depend on which file is read first):

| Rank | Section             | Declared by                                                                 | Scope                 |
| ---- | ------------------- | --------------------------------------------------------------------------- | --------------------- |
| 1.05 | Field settings      | the field types and their mixins (elements), the validation-message mixins (elements), `fmdbsamplemix:helpTextPosition` (sample) | fields |
| 1.06 | Configuration       | `fmdbmix:fieldActionFeedback` (engine)                                      | a field action, not a field |
| 1.10 | Logic               | `fmdbmix:formLogicElement` (engine)                                         | fields and containers |
| 1.12 | Responses           | `fmdbmix:responses` (elements)                                              | form                  |
| 1.15 | jExperience         | `fmdbmix:jExperienceProfileMapping`, `fmdbmix:jExperienceSensitiveField` (jexperience-engine) | fields |
| 1.20 | Buttons             | `fmdbmix:buttons` (elements)                                                | form                  |
| 1.30 | Multi-step          | `fmdbmix:multistep` (elements)                                              | form                  |
| 1.40 | Style               | `fmdbmix:style` (elements, joined by the sample's `fmdbsamplemix:customStyle`) | form              |

1.50, the former Validation messages section, is free: its three overrides now feed the Validation
messages fieldset of Field settings. The platform's own sections have their ranks in jContent's
`nt_base.json`: `content` 1.0, `classification` 2.0, `metadata` 3.0, `layout` 4.0, `options` 5.0, `seo` 6.0,
`listOrdering` 7.0, `visibility` 8.0 — which is why Formidable's sections sit between 1.0 and 2.0. Two of
them ignore their rank in the editor: `listOrdering` is always drawn second, right after Content (above),
and `visibility` is drawn apart, as the advanced options. A module can set a core section's `expanded`
or `hide` flag by naming it; it cannot move those two.

**Adding a section.** One rank per section across the product, whatever its scope: a new section takes a
rank **no other section uses**, even one it would never meet on the same node — a module picking 1.05 for a
field-level section of its own would tie with Field settings, and two sections of equal rank sort by name.
Pick a free value between the neighbours it should sit among, declare it with a `labelKey` resolvable from
your module's bundle (or the site's template set) and list it here. A module that wants to *join* an
existing section repeats its name and its rank instead — which is how a third-party module adds a fieldset
to Field settings (the sample does), or the sample's custom style its fieldset to Style.

## The rules of the editor the overrides rely on

Read in jContent 3.7.1 (`org.jahia.modules.contenteditor.api.forms`: `EditorFormServiceImpl`, `Form`,
`Section`, `FieldSet`), checked on the instance through `forms.editForm` and `forms.createForm`. The
editor **generates** a form from the node type and its mixins — one fieldset per type, named after the
type, every property in it, in the `content` section unless the CND says another `itemtype` — then
**merges** the static overrides it finds under `jahia-content-editor-forms/forms/` (any bundle, keyed by
`nodeType`, ordered by `priority`), then **prunes**.

1. **Sections merge by name, fieldsets merge by name within a section.** An override's fieldset merges
   with the fieldset of the same name *in the same section*; named in another section, it creates a
   second fieldset of that name there. `<main>` stands for the edited type's own fieldset.
2. **A field goes where it is listed.** A field listed in an override's fieldset is taken out of wherever
   the form has it (`Form.findAndRemoveField`) and placed there, with the rank the override gives it.
   This is the whole mechanism: moving a property is listing it in the target fieldset; it holds for the
   type's own properties and for a mixin's alike, whichever file lists them. One guard: a field a mixin
   with `extends` *generated* is not taken by another such mixin's generated fieldset (sibling mixins
   inheriting one property keep their own copy); an override takes it all the same.
3. **A fieldset survives pruning if** it is `alwaysPresent`, **or** it is dynamic (its type is an
   `extends` mixin the edited type is not itself of) and the only fieldset of its name in the form,
   **or** it is visible and holds a field. Visible means not hidden and either carrying an enable switch
   (every dynamic fieldset but a `jmix:templateMixin`) or holding a visible field.

What follows from the three rules, and shaped the overrides:

- **Moving a mixin's fields** is done from the mixin's own file, section `fieldSettings`, listing the
  fields in the shared fieldsets by name. Listing them from the *type's* file would put the override's
  blank field in the form before the mixin's generated field exists; the editor then shows a twin
  carrying nothing, which has taken the editor down before (jContent's `findAndRemoveField` javadoc).
- **A switch fieldset moves only with all its fields**: listing every field empties the generated copy in
  Content, which is dropped (dynamic, not unique, empty), while the moved copy is kept (it holds the
  fields). A switch **without a property** — `fmdbmix:fieldActions` autocreates a child and declares no
  property — would need `"alwaysPresent": true` on the moved copy; without it both copies are dropped and
  the switch disappears, which the spike proved before the switch was left in Content.
- **Dissolving an "advanced settings" mixin** into the shared fieldsets makes the mixin a **supertype** of
  its field type — the mixin loses `extends` and `itemtype`, the type lists it among its supertypes, the
  way `fmdbmix:textValidationMessages` carries the messages. Every field has the properties, no switch, no
  mixin to add on save (which a translator's role cannot do), and their CND defaults show in the editor like
  any property's. The mixin keeps its name and its own override file: the editor applies a supertype's
  override to the node, so the fields it declares are placed from there, and the attributes meant to stay
  hidden are `hidden` in the CND. The properties cannot move to the field type itself under their names: a
  node still listing the mixin — every field saved before — would have no effective node type for Jackrabbit
  ("ambiguous property definition") and refuse every write, the removal of the mixin included (verified on
  8.2.4; a type removed from the CND stays registered anyway). `AdvancedSettingsMixinMigration` drops the
  redundant mixin from the fields saved before; a field keeps working either way. Once that migration
  leaves (0.6), the declarations may be inlined into their types and dropped — optional, cosmetic
  (upgrade-notes.md, "Startup migrations", removal checklist).
- **A relabel** is a `labelKey` on the fieldset entry; it applies to a type-named fieldset too.
- **`priority`** orders the overrides among themselves; the generated form merges first. Every override of
  this repository uses 2.0 — their fields never collide, so the order among them does not matter.
- **The field-level flags** that belong to the property definition (`mandatory`, `valueConstraints`,
  `selectorType`…) go in a `jahia-content-editor-forms/fieldsets/<type>.json` file, not in `forms/`
  (jexperience-integration.md, "required by the editor only"). A `forms/` file places; a `fieldsets/` file
  describes.

## Who ships which file

| File (`jahia-content-editor-forms/forms/`) | Module | Places |
| ----- | ------ | ------ |
| `fmdb_<type>.json` | the type's module (elements, extended-inputs) | the type's own properties: `<main>` keeps `required`, the rest in Field settings |
| `fmdbmix_advanced<Type>Settings.json` | the type's module | the properties of the type's advanced-settings supertype in the shared fieldsets (its `hidden` ones are no fields) |
| `fmdbmix_dateBounds.json`, `fmdbmix_datetimeBounds.json`, `fmdbmix_<fixed|relative><Min|Max><Date|Datetime>.json` | elements — the mixins are the engine's, their editor overrides live next to the date types that use them, as their `fieldsets/` overrides already did | the bound modes in Constraints, each dynamic bound fieldset at its rank 3.1–3.4 |
| `fmdbmix_validationMessages.json`, `…textValidationMessages.json`, `…rangeValidationMessages.json` | elements | the messages in the Validation messages fieldset, ranks 1, 2–5, 6–9 |
| `fmdbmix_fieldActions.json` | engine | nothing of the switch, which stays where the editor generates it, at the end of Content; it hides the editor's Content list & ordering block on the elements that can carry field actions, and on them only (`listOrdering`, `"hide": true`) |
| `fmdbsamplemix_helpTextPosition.json` | the sample module | a third-party setting in Help & presentation, rank 1.5 |

A module adding a setting to a built-in field declares the section and the fieldset with the same name,
label key and rank as above (the keys resolve through its dependency on `formidable-elements`), lists its
field with a rank that places it, and keeps its mixin's own fieldset hidden and always activated.

## The CI check

A property an override forgets stays where the editor generated it — in Content, or in the hidden storage
fieldset — in silence. `scripts/check-editor-forms.mjs`, run by CI (`on-code-change.yml`), reads every
`forms/*.json` of the repository and, for each one declaring the `fieldSettings` section, compares the
fields it lists with the properties its `nodeType` declares in the CND: every property placed exactly
once, nothing the CND does not declare. A property marked `hidden` in the CND is not a field and is not
expected — which is how a deliberate hide is told from an omission; a type's inherited properties
(`jcr:title`, `ce:systemName`, a supertype's settings) are not its own and are not expected, the
supertype's own override placing them. An override that does not declare the section — the files of the
families still to move — is not checked, so the check tightens as the layout spreads.

The layout itself is asserted by the Cypress spec `fields/225` for the text family (through
`forms.editForm`, on a field with its settings set and on a plain one: Content, the switch after the
type's fieldset, no children block, the five fieldsets, the hidden attributes nowhere), `fields/222` for
the sample's setting and `fields/223` for the switch.

## Decision log

| Date | Decision | Why |
| ---- | -------- | --- |
| 2026-10-01 | **Content keeps the essentials, one section for the rest, via overrides** (HDU: « conserver pour chaque type de contenu input les props essentielles dans content et créer une nouvelle section pour le reste… via des json override en évitant le plus possible une modification des définitions ») | Fewer settings in front of the contributor, nothing moved in the repository, no migration |
| 2026-10-01 | **Two levels, not three** — the planned "sub-sections" are titled fieldsets (HDU: « tu es sûr que tu peux faire des sous-sections ? ») | The editor has sections and fieldsets, nothing nests below a fieldset |
| 2026-10-01 | **helpText in Help & presentation, placeholder in Value & input, validation messages and the field actions switch in the section, named "Field settings" for now** (HDU's five arbitrations; the name « à l'usage ») | Help and placeholder are filled often, not almost always; one section rather than three for a field |
| 2026-10-01 | **The "advanced settings" mixins are dissolved into the fieldsets, kept as hidden storage** (HDU: « est-ce que la mixin advance a encore un sens ? ») | A switch to hide settings inside a section that already gathers the rarely used ones is a door inside a door; the storage mixin keeps the CND untouched |
| 2026-10-01 | **`form`, `dirname`, `size`, `cols` hidden** (HDU) | No effect, or an effect nothing reads, in a Formidable form; listed in the hidden fieldset so the CI check knows they are meant to be |
| 2026-10-01 | **"Field actions", not "Checks"** (HDU) | The product term the Page Builder zone, the documentation and the configuration manager use; the switch names where the contributor goes next |
| 2026-10-01 | **The Field actions switch stays at the end of Content**, not in Field settings (HDU, on seeing it: « un peu perdu au milieu des autres sections ») | A capability, not a setting, one line; a lone switch between two titled groups reads as lost; and the children block its list creates is drawn right under Content by the editor whatever we do, so cause and effect stay together. The 2026-09-25 decision of field-actions.md stands |
| 2026-10-01 | **The Content list & ordering block is hidden for fields, not moved** (HDU asked for it after jExperience and closed, then « Hide CONTENT LIST ») | Its position is hard-coded second by jContent's FormBuilder, out of reach of a module; whether it shows is a flag the engine's override sets. Folded was the first cut; hidden is cleaner for a one-node list whose node is managed in the Page Builder |
| 2026-10-01 | **Pattern stays visible next to the mask, both tooltips say the pattern is derived** (HDU: « pattern est déduit de mask non ? c'est bien précisé quelque part ? ») | It is derived in the view and nothing said so; a pattern alone still validates by regex, and a typed one replaces the derived one |
| 2026-10-01 | **The layout is checked by CI, "hidden" is spelled out** | Rule 2 above makes a forgotten property fail in silence; listing the hidden ones in the hidden fieldset is what lets the check tell a choice from an omission |
| 2026-10-01 | **Numbers and dates: the slider keeps its minimum and maximum in Content; the date bound modes and their dynamic fieldsets move to Constraints** (the arbitrated schema: « curseur : Libellé · Obligatoire · Min · Max » ; « bornes → Contraintes ») | A slider is drawn by its bounds, a date is constrained by them; the dynamic bound fieldsets follow the mode that selects them, moved from their own overrides so the `fieldsets/` flags (mandatory offsets) travel with the fields |
| 2026-10-02 | **The "advanced settings" mixins become supertypes of their field types; the hidden always-activated storage fieldset is gone** (HDU review of #359: the editor added the mixin on every save, which a translator's role cannot do, and the always-activated fieldset showed no CND default; HDU: « fait l'alternative propre : déplacer ces propriétés sur les types primaires et retirer les mixins ») | Moving the properties onto the type under their names bricks every field saved before: Jackrabbit builds no effective node type for a node whose primary type and mixin declare the same property ("ambiguous property definition"), every write fails, the removal of the mixin included — verified on 8080; a type removed from the CND stays registered anyway. A supertype gives the same editor (no switch, the properties as the type's own, defaults shown) and keeps those fields writable; `AdvancedSettingsMixinMigration` drops the redundant mixin |
| 2026-10-02 | **`size` and `cols` stay hidden with `form` and `dirname`** (HDU: « j'avais confondu cols avec rows ») | Presentational, superseded by any stylesheet sizing the controls; `rows` is the one with an effect of its own, and it stays |
