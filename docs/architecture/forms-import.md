# Importing forms and results from Jahia Forms

Specification of the import of [Jahia Forms](https://github.com/Jahia/forms-core) (`forms-core` 3.x) forms
and their submissions into Formidable. Status: **draft for review** — nothing of it is implemented yet. The
open points are listed at the end; the spikes there can change parts of the design.

## Goal and scope

A site that collected submissions with Jahia Forms moves to Formidable without losing them: each Forms form
is recreated as a Formidable form, and its submissions become Formidable results of that form, listed,
filtered and exported by the Formidable results screen like the ones it collects itself.

| In scope | Out of scope (first version) |
|---|---|
| Forms 3.x (`forms-core` 3.x, and the three field types of `forms-extended-inputs`) | Forms 2.x and older |
| Recreating the forms: steps, fields, labels, placeholders, help texts, the required rule and its messages, the choices, the captcha, the save and email actions | The pages that place a Forms form: their `fcnt:formReference` nodes are not replaced by Formidable form references |
| Importing every submission saved by the Forms *save to JCR* action, files included | The jExperience prefill of Forms fields (`fcnt:mfffPrefill`): Formidable has its own profile mapping, to be wired in a later step |
| A dry-run report, then the import, re-runnable | The drafts a visitor saved with *save the form for later* (`fcnt:storedForm`): they are not submissions |
| | Deleting anything in Forms: the source is never modified |

## Decisions

| Decision | Why |
|---|---|
| **The import reads Jahia export files, not the Forms repository** | The source can live on another instance, or on an instance being retired; `forms-core` need not be installed where Formidable runs, so the `fcnt:*` types are never needed on the target. The files are the standard Jahia exports: the form export of the Forms builder (one zip per form) and the export of the site's `formFactory` node (results). |
| **A separate module, `formidable-forms-import`** | The engine knows nothing of Forms. The import is used once per site, then uninstalled; it depends on `formidable-engine` and `formidable-elements`, never the other way round. |
| **A recreated field keeps the node name it has in Forms** (`text-input_0_1`, `email-input_0_2`…) | Formidable keys a submission's values by the field's node name (see [Save to JCR](save-to-jcr.md)); Forms keys them the same way. Keeping the names maps every value to its field with no mapping table, and the integrity checks see declared fields only. |
| **The submitter's IP address and user name are not imported** | Formidable does not store them for its own submissions (personal data, see [Save to JCR](save-to-jcr.md)); an imported submission must not hold more than a native one. The administration guide says so, so that a site needing them exports them from Forms first. |
| **A value with no field to land on is kept as it is** | The results screen shows a value whose name the form does not declare, after the known fields, under its raw name; dropping it would lose data the contributor may still need. The dry-run report lists these values. |
| **Forms created unpublished, results written in live** | The contributor reviews and publishes each recreated form, as any form. Results only exist in live in both products: they are written there, as `SaveToJcrFormAction` does, and are never published. |
| **`origin` = `jahia-forms` on an imported submission** | `origin` is the discriminator [Save to JCR](save-to-jcr.md) documents for "a legacy-forms import": the results screen and the exports can tell an imported submission from a native one. |

## Source model (Forms 3.x)

What the import reads. Paths are relative to the root of each export file.

### The export files

Both are Jahia *document view* XML exports (`repository.xml`; a form zip also holds `live-repository.xml`,
identical for a published form):

- names and values are ISO 9075-encoded (`_x0020_` is a space, `_x0030_6` is the node name `06`);
- a multi-valued property is one attribute, its values separated by spaces (each value encoded);
- a reference is written `#/<path>`, relative to the export root (`parentForm="#/forms/contact-us"`);
- every node carries `jcr:created`, `jcr:createdBy`, `jcr:lastModified`;
- i18n properties live on `j:translation_<lang>` child nodes.

### A form (`exportedForm<n>.zip`)

```text
<formName>                      fcnt:form + fcmix:stepMetaData, fcmix:formSavable, fcmix:trackUser,
                                fcmix:displayCaptcha, fcmix:formTheme, fcmix:submissionConstraints
  j:translation_<lang>          jcr:title, the messages (start/end date, submission limit, already submitted)
  actions                       fcnt:action
    <action>                    fcnt:saveToJcrAction | fcnt:sendEmailAction | fcnt:sendEmailToSubmitterAction |
                                fcnt:redirectToAPageAction | fcnt:redirectToUrlAction, settings as fcnt:definitionOptions
  step-<n>                      fcnt:step (j:translation_<lang>/jcr:title)
    <fieldName>                 a fcmix:definition type (table below)
      j:translation_<lang>      jcr:title — the field's label
      placeholder, helptext     fcnt:definitionOptionsTranslatable, j:translation_<lang>/jsonValue
      inputsize, …              fcnt:definitionOptions, jsonValue
      <choices>                 fcnt:definitionOptions(Translatable), jsonValue = [{"key","value"}]
      validations               fcnt:validationRules › requiredValidation, emailValidation, …
                                (each with a translatable `message`)
      prefills                  fcnt:prefills › fcnt:mfffPrefill (not imported)
```

The form's properties hold its building language (`buildingLang`), its captcha switch (`displayCaptcha`),
user tracking (`trackUser`), the submission window and limit, and a `layoutJson` the Forms builder draws
from. Fieldsets are not containers: `fcnt:fieldsetStartDefinition` and `fcnt:fieldsetEndDefinition` are
siblings that open and close a group among the step's fields.

### The results (`formFactory` export)

```text
formFactory                     fcnt:formFactory
  results                       fcnt:resultsFolder
    <formName>                  fcnt:formResults — parentForm (weakref to the fcnt:form), buildingLang,
                                j:translation_<lang>/jcr:title
      labels                    fcnt:resultLabels
        <fieldName>             fieldId (the field's UUID), j:translation_<lang>/label and choices
      actions                   a copy of the form's actions (ignored)
      submissions               fcnt:submissions + jmix:autoSplitFolders (MM/dd/HH…, no year level)
        fakeSplittedResultForAreaDisplay   placeholder, skipped
        <MM>/<dd>/<HH>/…        fcnt:splittedResult
          <uuid>                fcnt:result — jcr:created (the submission date), origin (the referer or
                                request URI), ip_address and jcr:createdBy (when the form tracks users)
            <fieldName>         fcnt:resultField — result (string, always multiple), label (weakref to
                                labels/<fieldName>), optional
              <file>            jnt:file, for a file field
  forms                         fcnt:formsFolder (the forms; the form exports are the complete source)
```

How a value is written:

- every value is a string; one answer is a one-value `result`, several (checkboxes, multiple select) are
  several values;
- a choice field stores the option **key**, its label is in `labels/<fieldName>` `choices` (JSON
  `[{"key","value"}]`, per language);
- a date is the moment.js `toISOString()` of the chosen date: a UTC instant (`2024-08-12T22:00:00.000Z` for
  a date picked as 13 August in Paris);
- matrix, rating and country fields store one JSON object as a string, with a `rendererName`
  (`matrixRadios`, `matrixCheckboxes`, `rating`, `country`);
- a file field stores a JSON object `{"url":[],"name":[],"type":[],"size":[],"image":[],"rendererName":"fileUpload"}`,
  the files themselves as `jnt:file` children of the `fcnt:resultField`;
- a password field stores a placeholder, never the password;
- empty answers are not stored.

**A renamed field.** The `fcnt:resultField` keeps the name the field had when the visitor submitted, while
the label node follows the field's current name. The field a value belongs to is the one whose UUID is the
label's `fieldId`; the node name is only the fallback, for a label that has lost its field.

## Target model

The tree [Save to JCR](save-to-jcr.md) describes, which the import writes as `SaveToJcrFormAction` does:

```text
/sites/<site>/formidable-results/<entry>       fmdb:formResults — parentForm → the recreated fmdb:form
  submissions/<yyyy>/<MM>/<dd>/<submission>    fmdb:formSubmission — jcr:created, origin, locale, referer
    data                                       fmdb:submissionData — one string property per field node name
    files/<fieldName>/<file>                   jnt:folder / jnt:folder / jnt:file
```

The recreated form is an `fmdb:form` placed in a folder the administrator chooses (default:
`/sites/<site>/contents/imported-forms`), its fields in `fields` (and in `fmdb:step` nodes for a form with
several steps), its actions in `actions`.

## Mapping

### Form

| Forms | Formidable |
|---|---|
| `fcnt:form` name | The `fmdb:form` node name (the folder's next free name on a clash) |
| `j:translation_<lang>/jcr:title` | `jcr:title`, per language |
| One step | The fields directly in `fields` |
| Several steps | One `fmdb:step` per step, `label` from the step's title |
| `displayCaptcha=true` | The `fmdbmix:captcha` mixin on the form |
| Start and end dates, submission limit, once per user (`fcmix:submissionConstraints`) | Not imported (no Formidable equivalent); listed in the report |
| `isFormSavable` (save for later) | Not imported; listed in the report |
| `trackUser` | Nothing (no IP or user stored) |
| `layoutJson`, `fcmix:formTheme` | Nothing: the Formidable form takes the site's look |

### Fields

Every field gets its label (`jcr:title`), placeholder and help text in each language the export holds,
and `required=true` when it has a `requiredValidation`; the message of that rule becomes the field's
required message. A validation without an equivalent is listed in the report.

| Forms type | Formidable type | Notes |
|---|---|---|
| `fcnt:inputDefinition` | `fmdb:inputText` | `rangeLengthValidation` → `minLength`, `maxLength`; `regexValidation` → `pattern` |
| `fcnt:emailDefinition` | `fmdb:inputEmail` | |
| `fcnt:passwordDefinition` | `fmdb:inputText` | No password field in Formidable; reported. Its values were never stored |
| `fcnt:phoneDefinition` | `fmdb:inputText` | `type=tel` to verify |
| `fcnt:textAreaDefinition` | `fmdb:textarea` | |
| `fcnt:numberDefinition` | `fmdb:inputNumber` | `rangeValidation` → `minValue`, `maxValue` |
| `fcnt:simpleDateDefinition`, `fcnt:datePickerDefinition` | `fmdb:inputDate` | `dateBeforeValidation`, `dateAfterValidation` → the fixed date bounds |
| `fcnt:hiddenDefinition` | `fmdb:inputHidden` | |
| `fcnt:fileUploadDefinition` | `fmdb:inputFile` | `fileValidation` → `accept`; `fileNumberValidation` → `multiple` (the exact count to verify) |
| `fcnt:multipleCheckBoxesDefinition`, `…InlineDefinition` | `fmdb:checkbox` | Manual options, see [Choices](#choices) |
| `fcnt:multipleRadiosDefinition`, `…InlineDefinition` | `fmdb:radio` | |
| `fcnt:selectBasicDefinition` | `fmdb:select` | |
| `fcnt:selectMultipleDefinition` | `fmdb:select` with `multiple` | |
| `fcnt:countryListDefinition` | `fmdb:select`, options from the `country` source | Values converted, see [Values](#values) |
| `fcnt:switchDefinition` | `fmdbext:switch` | Needs `formidable-extended-inputs`; `fmdb:checkbox` with one option otherwise |
| `fcnt:ratingDefinition` | `fmdbext:rating` | Needs `formidable-extended-inputs`; `fmdb:inputNumber` otherwise |
| `fcnt:acceptTermCheckboxDefinition` (extended inputs) | `fmdbext:consent` | Needs `formidable-extended-inputs` |
| `fcnt:imageCheckboxDefinition` (extended inputs) | `fmdb:checkbox` | Images dropped; reported |
| `fcnt:contentDisplayDefinition` (extended inputs) | `fmdb:richText` | |
| `fcnt:fieldsetStartDefinition` … `fcnt:fieldsetEndDefinition` | `fmdb:fieldset` holding the fields between them | The legend from the start's title |
| `fcnt:matrixRadiosDefinition`, `fcnt:matrixCheckBoxesDefinition` | `fmdb:textarea` | No matrix in Formidable; reported, values kept as text |
| `fcnt:buttonDefinition`, `fcnt:buttonTripleDefinition` | Nothing | The form's own buttons replace them |

A type the table does not know (a third-party Forms field) becomes `fmdb:inputText` and is listed.

### Choices

A Forms option is a `{key, value}` pair, its key stored in the results and its value shown. Formidable
manual options (`fmdbmix:manualOptions`, see [Choice field options sources](choice-field-options-sources.md))
are `{"value","label","selected"}` entries per language: the Forms **key becomes the value** and the Forms
value the label, so the stored results need no conversion.

### Actions

| Forms action | Formidable action |
|---|---|
| `fcnt:saveToJcrAction` | `fmdb:save2jcrAction` |
| `fcnt:sendEmailAction` | `fmdb:emailNotificationAction` (`to`, `from`, `subject`; the template to review) |
| `fcnt:sendEmailToSubmitterAction` | Reported: no action sends to an address the visitor typed (to verify) |
| `fcnt:redirectToAPageAction`, `fcnt:redirectToUrlAction` | Reported: no redirect action in Formidable; the submission message replaces it |

### Submissions

| Forms (`fcnt:result`) | Formidable (`fmdb:formSubmission`) |
|---|---|
| `jcr:created` | `jcr:created` — the submission date, which also files the submission under `yyyy/MM/dd` (spike 1) |
| `origin` (referer or request URI) | `referer` |
| — | `origin` = `jahia-forms` |
| — | `locale` = the form's `buildingLang` (Forms does not record the visitor's language) |
| — | `timeZone`: not set (Forms does not record it) |
| `ip_address`, `jcr:createdBy` | Not imported |
| The node name (a UUID) | The import marker (below), as `sourceId` |
| Each `fcnt:resultField` | One property of `data`, named after the field it belongs to (by `label` → `fieldId`) |
| A field's `jnt:file` children | `files/<fieldName>/<file>` |

The submission's own node name follows the Formidable pattern (`submission-<yyyyMMdd-HHmmss>-<xxx>`, from
the original date).

### Values

| Forms value | Formidable value |
|---|---|
| Text, email, number, hidden, textarea | Unchanged |
| A choice key (one or several) | Unchanged (the key is the option's value) |
| A date (UTC instant) | `yyyy-MM-dd`, the date in the time zone the administrator gives (default: the server's), since Forms stored the visitor's midnight as an instant |
| Country (JSON) | Its country code, matching the `country` options source |
| Rating (JSON) | The rating number |
| Matrix (JSON) | One line per row, `row: answer(s)` |
| File (JSON + `jnt:file`) | The files copied under `files/<fieldName>/`; the JSON is dropped |
| Password placeholder | Dropped |

## The import

### Running it

An administration page of the module (jContent, *Additional* › *Import from Jahia Forms*), for site
administrators:

1. **Upload** the form export zips and the `formFactory` export, and choose the site and the folder of the
   forms.
2. **Dry run.** Nothing is written; the page shows the report.
3. **Import.** The forms, then the submissions, by batches of 100, each batch saved on its own. The page
   shows the progress and the final report.

The report, per form: the field conversions (with every reported item of the tables above), the number of
submissions found, imported and already imported, the values with no field, the files and their size.

### A form without its export

When the `formFactory` export holds results for a form whose export was not given, the import builds the
form from the results alone: one field per label node, named after it, labelled with its translations
(the node name when they are empty), `fmdb:inputText` for a field without choices, `fmdb:select` with the
label's `choices` for one with choices. The report flags the form as **rebuilt from its results**, to be
completed by hand.

### Running it twice

Each imported submission carries a marker mixin, `fmdbimportmix:importedSubmission` with
`sourceId` = the Forms result's UUID: a submission whose source is already imported is skipped, so an
import that stopped half-way is run again as is. A recreated form carries the same kind of marker with the
Forms form's UUID: a second run fills the existing form's results instead of creating a second form.

### Permissions

The results entry is created as `SaveToJcrFormAction` creates it: inheritance broken, the readers synced
from the recreated form's `fmdb-results-reader` grants once it is published (see
[Results permissions](../administration/results-permissions.md)). The Forms roles (`formFactoryResults`,
`singleFormResults`…) are not carried over: the report lists the forms whose results had dedicated
readers, and the guide tells how to grant them in Formidable.

## Tests

- **Unit tests** of the reader (ISO 9075 decoding, multi-values, references), of each field and value
  conversion, of the rebuild from results — with the anonymised sample exports as fixtures.
- **A Cypress spec** that uploads the sample exports, checks the dry-run report, imports, checks the forms
  (unpublished, fields, labels in two languages) and the results screen (count, values, dates, the imported
  origin), runs the import a second time and checks nothing was duplicated.

## Open points

| # | Point | Effect |
|---|---|---|
| Spike 1 | Can a session set `jcr:created` on a new `fmdb:formSubmission`, as the Jahia import does? | If not: an `importedAt`-style property holding the original date, and the results screen and the split reading it when present |
| Spike 2 | The value an `fmdb:inputDate` submits and stores (`yyyy-MM-dd`?) | The date conversion |
| Spike 3 | `formidable-extended-inputs` absent: confirm the fallbacks keep the values readable | The switch, rating and consent rows |
| 4 | The email actions: template conversion, and the *send to the submitter* case | The actions table |
| 5 | The page placements of the Forms forms (`fcnt:formReference`): replace them in a second step? | Scope |
| 6 | The jExperience prefill of the Forms fields: map it to the Formidable profile mapping in a second step? | Scope |
| 7 | The anonymisation of the sample exports before they are committed as fixtures (they hold real email addresses) | Tests |
