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

Rebuilds the `FormidableSite4Tests` site with a ready-to-use, published set of
live forms for manual UI and submission testing (this is provisioning, not a
test — CI never runs it):

| Page under `/sites/FormidableSite4Tests/home` | Content |
|---|---|
| `playground-simple-page.html` | Minimal contact form (published in EN and FR, custom required messages on the name fields); its fields map to the visitor profile when jExperience is there, see below |
| `playground-steps-page.html` | Three-step form with navigation |
| `playground-complete-page.html` | Every built-in field type, plus sourced choice fields (countries + `product/tv` sample categories), a gender radio and a number of children; mapped to the visitor profile when jExperience is there |
| `playground-languages-page.html` | Choice field whose French labels are only half translated, to try the site's *Replace untranslated content with the default language content* setting both ways |

All forms carry a save-to-JCR action, so submissions land in the results
screens. The script also:

- declares the `optionsSources` module configuration (`countries`, plus `tv`
  backed by the static `fmdbSampleStaticList` initializer of
  formidable-test-module-samples-java);
- creates and publishes the sample categories
  `/sites/systemsite/categories/product/tv/{plasma,oled,led}` used by the
  category-mode field;
- provisions the results reader user **john-doe / John#1234** (server-level,
  kept across runs, site member as editor) with `fmdb-results-reader` granted
  on the simple form only — to exercise the results access rights.

Prerequisites: current `formidable-engine`, `formidable-elements` and
`formidable-test-module-samples-java` deployed on the target instance.

### With jExperience

When `formidable-jexperience-engine` is deployed (the script asks the repository for its
mapping mixin, and goes on without mappings when the type is unknown), the playground also
enables jExperience and the module on the site, and maps the two contact forms to jCustomer's
default visitor profile properties, so the whole chain — editor section, mapping rule written at
publication, submission event, profile update — is testable at once:

| Form | Field | Profile property | Strategy |
|---|---|---|---|
| simple | `firstName`, `lastName` | `firstName`, `lastName` | always set, prefill on |
| simple | `email` | `email` | set if missing, prefill on |
| simple | `phoneNumber` (shown when a call is asked for, masked `+99 9 99 99 99 99`) | `phoneNumber` | always set |
| simple | `message` | — | marked **sensitive**: never leaves the site |
| complete | `email` | `email` | set if missing |
| complete | birth date | `birthDate` | always set, prefill on |
| complete | `gender` (radio) | `gender` | always set, prefill on |
| complete | `kids` (number) | `kids` | always set |
| complete | `country` (sourced select, ISO codes) | `countryName` | always set |
| complete | interests (checkbox group) | `formidableInterests` — multi-valued, playground card | always set |
| complete | `newsletter` (switch) | `formidableOptIn` — boolean, playground card | always set |
| complete | employee code | — | marked **sensitive** |

jCustomer's default schema has no multi-valued and no boolean property, so the script creates the two
it needs in a **Formidable playground** card of the visitor profile (through jExperience's admin proxy,
once; a jCustomer that cannot be reached is logged and the two mappings are simply skipped at
publication). The other choice fields stay unmapped. The sample submissions the script makes at the end run
through the live pages, so with the tracker on the site they reach jCustomer as `form` events and
the mapped values land on one visitor's profile (all of them are one Cypress visitor, so the
profile ends with the last values — and keeps the first email, which is set only if missing).
For that the run declares a plain Chrome user agent: the tracker's own crawler list names
HeadlessChrome and would otherwise start in fallback mode and send nothing. The mapping rules of
the previous run's forms are deleted from jCustomer before the site is, so they do not pile up.

The script lives in `cypress/playground/provision-forms.play.ts`; the `.play.ts`
extension keeps it outside the Cypress spec pattern used by the test runs.
