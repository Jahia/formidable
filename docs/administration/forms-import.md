# Importing the forms and results of Jahia Forms

A site that collected submissions with Jahia Forms (`forms-core` 3.x) moves to Formidable without losing
them: the import recreates each Forms form as a Formidable form, and writes every submission Forms saved as
a result of that form. The design is in [Importing forms and results from Jahia Forms](../architecture/forms-import.md).

## What you get

- One results entry per Forms form, with every submission Forms saved, its files included, dated as the
  visitor submitted it. The submitter's IP address and user name are not imported: Formidable keeps neither
  for its own submissions. A date keeps the day the visitor chose, except for visitors at +13 and +14 hours
  from UTC (New Zealand in summer, Tonga, Samoa, Kiribati), who get the previous day.
- What receives those results is your choice, per form, when the dialog shows what the import will do:
  - **Results only**, the default: the entry alone, flagged **Imported, no form** on the Results page, with
    the system names of the fields as column heads (`your-first-name`), read by the administrators of the
    site.
  - **Create the form**: a Formidable form in the **Imported from Jahia Forms** content folder of the site
    (`contents/imported-forms`), unpublished, with the entry under it. Its fields keep the labels,
    placeholders, help texts, required rules and choices Forms had, under system names generated from the
    labels; what Formidable has no equivalent for is listed in the report, never dropped in silence: a
    password field (not recreated), a matrix (a text area), a redirect action, a prefill, a conditional
    logic, a save-for-later setting.
  - **Attach to an existing form**: the results go under a form of the site you pick, built by hand or
    reworked, and show on the Results page with the labels and the readers of that form. The dialog shows
    where the values of each Forms field land: a field of the same name is matched, a field with the same
    label is proposed, and you pick the field for the others, or keep the name as it is.
- A second import of the same export, or of a later export of the same site, adds only what is missing: a
  form already imported is left as it is, even reworked, an entry already written receives its new
  submissions under the same names, and a submission already imported is skipped.

## Taking the export

On the instance that runs Jahia Forms, open the **Repository explorer**, open the site, right-click its
`formFactory` node, choose **Export**, then **Export Zip with live content**. That zip is the only export
that holds the three things the import needs: the identifiers of the forms and of their fields, which a
later run looks them up by; the results; and the uploaded files. **Export XML** and **Export Zip** without
live content hold no identifier, and an export taken from jContent reads the edit workspace, without the
results: the import refuses them with this procedure.

## Running the import

1. Turn the button on: in `karaf/etc/org.jahia.modules.formidable.formsImport.cfg`, set
   `importButtonEnabled=true` (see [Configuration](configuration.md)). The change applies at once, no restart.
2. Open the **Results** page of the target site in jContent. The **Import Jahia Forms results** button shows in its toolbar to
   the administrators of the site, the users who hold the site-administrator role on it.
3. Click **Import Jahia Forms results**, drop the zip on the dialog or choose it. The dialog analyses the export without
   writing anything, and shows what the import will do: per form, whether it is already there, its fields
   and what could not be carried over, the submissions found, to import and already imported, the files.
4. Choose, for each form that is not there yet, what receives its results: **Results only**, **Create the
   form**, or **Attach to an existing form**. For an attachment, pick the form, then check where each field
   lands: a field of the form marked **same name** or **same label** was matched for you; pick a field for
   the others, or leave **Keep the name**. A field of the form picked in one row is no longer offered in the
   others, nor is the name of another field of the source, so that no two fields land under one name. For a
   choice field, the row lists the imported values that none of its options carries: they are kept as they
   are, so add the options or pick another field.
5. Click **Import**. The import runs on the server, by batches; the dialog follows it and can be closed
   meanwhile — opening it again shows the running import, then its report. One import runs at a time per
   site.
6. Read the final report, then publish the forms you had created: until a form is published, its entry on
   the Results page shows as **Unpublished**, with the labels of the form as column heads, read from the
   edit workspace. Review each form, complete what the report lists, then publish it. Grant the **Form
   results reader** role on the form to the people who read its results, as for any form
   ([Results permissions](results-permissions.md)). Results attached to a form of the site already have the
   readers of that form.
7. Turn the button off again.

The export the dialog accepts is bounded by `maxFileSizeMb` (200 MB by default).

## Settings

| Setting | Default | Effect |
|---|---|---|
| `importButtonEnabled` | `false` | Shows the **Import Jahia Forms results** button on the Results page of every site |
| `maxFileSizeMb` | `200` | The largest export the dialog accepts |
