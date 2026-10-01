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
                        [storage mixin: hidden, always activated]       the type's former "advanced settings"
Content list & ordering the editor's own block, shown once the switch created the actions list — folded
Field settings  1.05    1 Help & presentation                           help text, title attribute
                        2 Value & input                                 placeholder, default value, mask, pattern,
                                                                        autocomplete, spell check, suggested values…
                        3 Constraints                                   minimum / maximum length…
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
too — the options and their mode on a choice field, the minimum and maximum of a slider, the declaration
of a consent field — which is decided type by type as each family moves (table below).

**Field settings** gathers the rest, in five fieldsets whose names, label keys and ranks are the same in
every override, so that a type's own file, its mixins' files and a third-party module's file all land in
the same groups:

| Rank | Fieldset              | Name                  | Label key                           | Holds |
| ---- | --------------------- | --------------------- | ----------------------------------- | ----- |
| 1    | Help & presentation   | `helpAndPresentation` | `fmdb.fieldset.helpAndPresentation` | `helpText` (rank 1), the HTML `title` attribute (2), a third-party setting about the help text (the sample puts `helpTextPosition` at 1.5) |
| 2    | Value & input         | `valueAndInput`       | `fmdb.fieldset.valueAndInput`       | by frequency of use: `placeholder` (1), `defaultValue` (2), `mask` (3), `pattern` (4), `autocomplete` (5), `spellcheck` (6), `wrap` (7), `list` (7), `rows` (8), `resize` (9) |
| 3    | Constraints           | `constraints`         | `fmdb.fieldset.constraints`         | `minLength`, `maxLength` |
| 4    | Behaviour             | `behaviour`           | `fmdb.fieldset.behaviour`           | `readonly`, `disabled`, `autofocus` |
| 5    | Validation messages   | `validationMessages`  | `fmdb.fieldset.validationMessages`  | `msgValueMissing` (1), the text messages (2–5), the range messages (6–9) |

The label keys live in the bundle of `formidable-elements`; a module joining the layout resolves them
through its dependency on it.

**The children block.** Switching Field actions on autocreates the `actions` list under the field, and a
node with children makes the editor show its own **Content list & ordering** block, which jContent places
**right after Content whatever its rank** (`FormBuilder.jsx` takes the `listOrdering` section out of the
rank order and reinserts it in second position) and opens by default (`nt_base.json`, `"expanded": true`).
Its position cannot be changed from a module; its initial state can: the engine's override on
`fmdbmix:fieldActions` declares the section with `"expanded": false`, so a field opens with the block folded
— one node, nothing to order. Hiding it altogether (`"hide": true` on the section) is the other flag at hand.

**Hidden for good.** Three HTML attributes of the text input and three of the textarea have no effect, or
an effect nothing reads, in a Formidable form, and are no longer offered: `form` (associates an input placed
outside its `<form>` — ours are always inside), `dirname` (makes the browser post `<name>.dir=ltr|rtl`, which
the engine never reads), `size` and `cols` (presentational, superseded by any stylesheet sizing the controls —
the module ships none, the sample theme sets `width: 100%`). They stay in the CND and a value already set
still renders; they are listed in the hidden storage fieldset, which is how the CI check tells "hidden on
purpose" from "forgotten".

**Mask and pattern.** The text input derives its HTML `pattern` from the mask (`maskToPattern`) unless the
contributor types a pattern of their own; both tooltips say so, and the two sit side by side in Value &
input, the mask first.

### Where each family stands

| Family | Types | Status |
| ------ | ----- | ------ |
| Text | `fmdb:inputText`, `fmdb:inputEmail`, `fmdb:textarea` | **done** (2026-10-01): Content = title, system name, required; the "advanced settings" mixins dissolved into the fieldsets, kept as hidden storage |
| Numbers and dates | `fmdb:inputNumber`, `fmdb:inputRange`, `fmdb:inputDate`, `fmdb:inputDatetimeLocal` | to do — the bound modes and their dynamic fieldsets go to Constraints; a slider keeps its min and max in Content |
| Choices and files | `fmdb:select`, `fmdb:radio`, `fmdb:checkbox`, `fmdb:inputFile`, `fmdb:inputColor`, `fmdb:inputHidden` | to do — the options mode and its dynamic fieldsets stay in Content next to the options |
| Extended inputs | `fmdbext:consent`, `fmdbext:rating`, `fmdbext:scale`, `fmdbext:switch` | to do |

Until a family moves, its types keep their properties in Content and show the Field settings section with
what applies to every field already: the validation messages.

## The section ranks

Sections order by rank, and a rank is global to the form: two overrides naming the same section merge into
one, two sections with the same rank sort by name. The ranks in use, read from the overrides of this
repository (an override declares its section's rank in every file that names the section, so the merge
cannot depend on which file is read first):

| Rank | Section             | Declared by                                                                 | Scope                 |
| ---- | ------------------- | --------------------------------------------------------------------------- | --------------------- |
| 1.05 | Field settings      | the field types and their mixins (elements), the validation-message mixins (elements), `fmdbsamplemix:helpTextPosition` (sample) | fields |
| 1.10 | Logic               | `fmdbmix:formLogicElement` (engine)                                         | fields and containers |
| 1.10 | Responses           | `fmdbmix:responses` (elements)                                              | form                  |
| 1.15 | jExperience         | `fmdbmix:jExperienceProfileMapping` (jexperience-engine)                    | fields                |
| 1.20 | Buttons             | `fmdbmix:buttons` (elements)                                                | form                  |
| 1.30 | Multi-step          | `fmdbmix:multistep` (elements)                                              | form                  |
| 1.40 | Style               | `fmdbmix:style` (elements)                                                  | form                  |
| 1.05 | Configuration       | `fmdbmix:fieldActionFeedback` (engine)                                      | a field action, not a field: no clash |

1.50, the former Validation messages section, is free: its three overrides now feed the Validation
messages fieldset of Field settings. The platform's own sections have their ranks in jContent's
`nt_base.json`: `content` 1.0, `classification` 2.0, `metadata` 3.0, `layout` 4.0, `options` 5.0, `seo` 6.0,
`listOrdering` 7.0, `visibility` 8.0 — which is why Formidable's sections sit between 1.0 and 2.0. Two of
them ignore their rank in the editor: `listOrdering` is always drawn second, right after Content (above),
and `visibility` is drawn apart, as the advanced options. A module can set a core section's `expanded`
or `hide` flag by naming it; it cannot move those two.

**Adding a section.** Pick a rank between the neighbours it should sit among, declare it with a `labelKey`
resolvable from your module's bundle (or the site's template set) and list it here. A section that names
an existing one by `name` joins it instead — which is how a third-party module adds a fieldset to Field
settings (the sample does).

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
- **Dissolving an "advanced settings" mixin** into the shared fieldsets keeps the mixin as **storage**:
  its properties still belong to it, so the node must carry it for the values to be saved. The override
  declares the mixin's fieldset in `content` (merging with the generated one, not duplicating it) with
  `"isAlwaysActivated": true` and `"hide": true` — the editor adds the mixin on save, shows no switch —
  and lists in it the properties meant to stay hidden. Every field saved from the editor gains the mixin;
  the properties are optional and the views read them as before. The clean-up (the properties declared
  by the type, the mixin retired) is a CND change for a later version: removing a property from a
  registered type is refused by the definitions checker in the same version.
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
| `fmdbmix_advanced<Type>Settings.json` | the type's module | the mixin's properties in the shared fieldsets, the hidden ones in its hidden storage fieldset |
| `fmdbmix_validationMessages.json`, `…textValidationMessages.json`, `…rangeValidationMessages.json` | elements | the messages in the Validation messages fieldset, ranks 1, 2–5, 6–9 |
| `fmdbmix_fieldActions.json` | engine | nothing of the switch, which stays where the editor generates it, at the end of Content; it folds the editor's Content list & ordering block (`listOrdering`, `"expanded": false`) |
| `fmdbsamplemix_helpTextPosition.json` | the sample module | a third-party setting in Help & presentation, rank 1.5 |

A module adding a setting to a built-in field declares the section and the fieldset with the same name,
label key and rank as above (the keys resolve through its dependency on `formidable-elements`), lists its
field with a rank that places it, and keeps its mixin's own fieldset hidden and always activated.

## The CI check

A property an override forgets stays where the editor generated it — in Content, or in the hidden storage
fieldset — in silence. `scripts/check-editor-forms.mjs`, run by CI (`on-code-change.yml`), reads every
`forms/*.json` of the repository and, for each one declaring the `fieldSettings` section, compares the
fields it lists with the properties its `nodeType` declares in the CND: every property placed exactly
once, hidden ones in the hidden fieldset, nothing the CND does not declare. A property marked `hidden` in
the CND is not a field and is not expected; a type's inherited properties (`jcr:title`, `ce:systemName`)
are not its own and are not expected. An override that does not declare the section — the files of the
families still to move — is not checked, so the check tightens as the layout spreads.

The layout itself is asserted by the Cypress spec `fields/225` for the text family (through
`forms.editForm`, on a field carrying its storage mixin and on one without it: Content, the switch after
the type's fieldset, the folded children block, the five fieldsets), `fields/222` for the sample's setting
and `fields/223` for the switch.

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
| 2026-10-01 | **The Content list & ordering block is folded for fields, not moved** (HDU asked for it after jExperience and closed) | Its position is hard-coded second by jContent's FormBuilder, out of reach of a module; its initial state is a flag the engine's override sets. Hiding it was weighed as the cleaner option for a one-node list and left as the next flag to flip |
| 2026-10-01 | **Pattern stays visible next to the mask, both tooltips say the pattern is derived** (HDU: « pattern est déduit de mask non ? c'est bien précisé quelque part ? ») | It is derived in the view and nothing said so; a pattern alone still validates by regex, and a typed one replaces the derived one |
| 2026-10-01 | **The layout is checked by CI, "hidden" is spelled out** | Rule 2 above makes a forgotten property fail in silence; listing the hidden ones in the hidden fieldset is what lets the check tell a choice from an omission |
