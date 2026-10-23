# Formidable tests

Cypress project for the Formidable modules. It targets a running Jahia instance
(default: `http://localhost:8080`, credentials from `.env` / `set-env.sh`).

## Running the test suites

```bash
yarn e2e:ci      # full headless run (what CI executes)
yarn e2e:debug   # interactive Cypress runner
yarn typecheck   # type-checks the whole suite (specs, support, page objects) in a second
```

## Manual-testing playground

```bash
yarn playground
```

Rebuilds two sites with the same ready-to-use, published set of live forms for manual UI and
submission testing (this is provisioning, not a test — CI never runs it; the specs' own site,
`FormidableSite4Tests`, is left alone). On each site the forms sit in the content folder
`/sites/<site>/contents/forms/playground`, each on a page of its own under `/sites/<site>/home`
(`<form name>-page.html`). The sites alone tell the two looks apart: no form carries a **Custom
CSS** property. The nodes are created in title order (Complete form first), which is the order
jContent's trees show them in:

| Site | Template set | What it shows |
|---|---|---|
| **Formidable Playground Plain** (`FormidablePlaygroundPlain`) | `formidable-test-module-templateset-jsp`, which ships no stylesheet | The modules' own look, the markup and the class hooks as [the styling contract](../docs/styling/README.md) describes them |
| **Formidable Playground CSS** (`FormidablePlaygroundCss`) | `formidable-test-module-templateset-tsx`, whose `Layout` ships `src/design/registration-yellow-theme.css` | The complete stylesheet written against that contract, every variable family and the class hooks a visitor sees — a business look on top of the same markup, the case the authoring UI must stay readable in |

| Form | Content |
|---|---|
| `simple` | Minimal contact form (published in EN and FR, custom required messages on the name fields, a select revealing a conditional phone field, an optional single file); the only one showing the two optional buttons, **Reset** and **New form** (offered in the success message); its fields map to the visitor profile when jExperience is there, see below |
| `newsletter` | Two small fields; only on the two-forms page |
| `steps` | Three-step form with navigation, a fieldset inside step 2 and conditional logic driven by the delivery method |
| `complete` | Every built-in field type, in three blocks: the visitor profile fields first (mapped and prefilled when jExperience is there), the other field types, and the choice fields completing the options matrix below |
| `languages` | Choice field whose French labels are only half translated, to try the site's *Replace untranslated content with the default language content* setting both ways |
| `two-forms-page` | A page holding the simple form (referenced) next to the newsletter one: two results sets, two mappings, one tracking script |
| `vanished`, `unpublished`, `emptied` (Plain site only) | Three throwaway forms that leave results behind them — deleted from both workspaces, unpublished, emptied of its only submission — the entries the Results page flags or shows empty |

The complete form covers every options mode of a choice field, single and multiple:

| Options mode | Single | Multiple |
|---|---|---|
| Manual (options typed by the author) | `gender`, `deliveryMethod` (radio), `department` (select) | interests (checkbox group) |
| Options source (declared in the module configuration) | `country` (select, `country`) | `viewing` (checkbox group, a static list: Streaming, Cable, Satellite, Antenna) |
| Category (children of a picked category) | `tvCategory` (radio, `product/tv`: Plasma, OLED, LED) | `audioCategories` (checkbox group, `product/audio`: Headphones, Speakers, Soundbar) |
| Content (nodes under a picked root) | `agency` (select, texts under `contents/agencies`) | `services` (multiple select, texts under `contents/services`) |

Every field has an option set of its own, so no two of them read as one field repeated.

All forms carry a save-to-JCR action, so submissions land in the results
screens. The script also:

- makes sure of the options sources, one configuration file each, touching no
  other: `country`, installed from the very file formidable-test-module-samples-java
  ships (restored if missing), plus `tv` and `viewing` backed by that module's
  static `fmdbSampleStaticList` initializer (`tv` with a localized label, offered
  in the editor and used by no field);
- creates and publishes the sample categories
  `/sites/systemsite/categories/product/tv/{plasma,oled,led}` and
  `product/audio/{headphones,speakers,soundbar}` the category-mode fields point at;
- provisions the results reader user **john-doe / John#1234** (server-level,
  kept across runs, member of both sites as editor) with `fmdb-results-reader` granted
  on the simple form of each site only — to exercise the results access rights.

Prerequisites: current `formidable-engine`, `formidable-elements`,
`formidable-extended-inputs` and `formidable-test-module-samples-java`
deployed on the target instance, plus the two test template sets,
`formidable-test-module-templateset-jsp` and `formidable-test-module-templateset-tsx`
(the CSS site renders the stylesheet the latter ships).

`yarn playground:maintenance` adds a form with no repository-writing
action (`maintenance-free`, the contrast case for the read-only maintenance mode) to the
same folder of each playground site, which it creates and publishes if the main script has not
run yet. Only the folder is its own: it needs the sites `yarn playground` has already built, and
its forms render live once that script has published each site's home in the page language.

### With the Forms import

Spec 76 (`actions/76-forms-import.cy.ts`) needs `formidable-forms-import` deployed next to the engine: the
Import button of the Results page, its endpoint and the import content types are the module's. The test site
is created without it (`FORMIDABLE_MODULE_IDS` lists the modules every spec needs): the spec enables the
module on the site itself, which is what shows the button, and the playground does the same when the module
is deployed.

### With jExperience

When `formidable-jexperience-engine` is deployed (the script asks the repository for its
mapping mixin, and goes on without mappings when the type is unknown), the playground also
enables jExperience and the module on both sites, and maps the two contact forms of each to jCustomer's
default visitor profile properties, so the whole chain — editor section, mapping rule written at
publication, submission event, profile update — is testable at once:

| Form | Field | Profile property | Strategy |
|---|---|---|---|
| *rule* | *a required field* | | *always set: the visitor just stated it* |
| *rule* | *an optional field* | | *set if missing: it completes the profile, never overrides it* |
| *rule* | *an identifying value a later submission should not overwrite* (`email`) | | *set if missing even when required* |
| simple | `firstName`, `lastName` | `firstName`, `lastName` | always set, prefill on |
| simple | `email` | `email` | set if missing, prefill on |
| simple | `phoneNumber` (shown when a call is asked for, masked `+99 9 99 99 99 99`) | `phoneNumber` | set if missing, prefill on — a field the logic hides is prefilled all the same, and shows its value once revealed |
| complete | `email` | `email` | set if missing, prefill on — with the first visitor's address, which the simple form stated: the sample entries type theirs over it |
| complete | birth date | `birthDate` | always set, prefill on, then **read-only** — the visitor sees the date the profile knows and cannot change it |
| complete | `gender` (radio) | `gender` | set if missing, prefill on, then **read-only** — a radio group has no native read-only, the page puts the profile's choice back on every change |
| complete | `kids` (number, default 1) | `kids` | set if missing, prefill on — the one field with an author's default: the profile's value replaces it, a profile without one leaves it |
| complete | `country` (sourced select, ISO codes) | `countryName` | set if missing, prefill on, then **hidden** — the field disappears once the profile knows the country, its value still submitted |
| complete | interests (checkbox group) | `formidableInterests` — multi-valued, playground card | always set, prefill on — the multi-valued shape |
| complete | `newsletter` (switch) | `formidableOptIn` — boolean, playground card | set if missing, prefill on — the boolean shape |

jCustomer's default schema has no multi-valued and no boolean property, so the script creates the two
it needs in a **Formidable playground** card of the visitor profile (through jExperience's admin proxy,
once; a jCustomer that cannot be reached is logged and the two mappings are simply skipped at
publication). The other choice fields stay unmapped. The sample submissions the script makes at the end run
through the live pages, so with the tracker on the site they reach jCustomer as `form` events and
the mapped values land on one visitor's profile (all of them are one Cypress visitor, so the
profile ends with the last values — and keeps the first email, which is set only if missing). The three
fields that ask for something once the profile's value is in (birth date and gender read-only, country
hidden) are left to the prefill from the second visitor on, as a visitor would have to leave them; the
opt-in is set or cleared as each entry says, since the prefill brings the profile's own along.
For that the run declares a plain Chrome user agent: the tracker's own crawler list names
HeadlessChrome and would otherwise start in fallback mode and send nothing. The mapping rules of
the previous run's forms are deleted from jCustomer before the site is, so they do not pile up.

The script lives in `cypress/playground/provision-forms.play.ts`; the `.play.ts`
extension keeps it outside the Cypress spec pattern used by the test runs.
