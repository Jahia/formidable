# Formidable tests

Cypress project for the Formidable modules. It targets a running Jahia instance
(default: `http://localhost:8080`, credentials from `.env` / `set-env.sh`).

## Running the test suites

```bash
yarn e2e:ci      # full headless run (what CI executes)
yarn e2e:debug   # interactive Cypress runner
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
| `playground-simple-page.html` | Minimal contact form (published in EN and FR, with a custom required message on the full name field) |
| `playground-steps-page.html` | Three-step form with navigation |
| `playground-complete-page.html` | Every built-in field type, plus sourced choice fields (countries + `product/tv` sample categories) |

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

The script lives in `cypress/playground/provision-forms.play.ts`; the `.play.ts`
extension keeps it outside the Cypress spec pattern used by the test runs.
