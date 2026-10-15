# Administering Formidable

For the administrator of a Jahia instance running the forms.

- [Upgrade notes](upgrade-notes.md) — the manual steps between specific versions, and the migrations that run at startup
- [Configuration files](configuration.md) — the five files of the engine, one per theme, the file of the Forms import module, and the one file per entry of two lists; what each holds, and how the single file of earlier builds is carried over
- [Error codes](error-codes.md) — the opaque codes a failed submission returns, and what each one means
- [CAPTCHA server-side validation](captcha-server-side-validation.md) — configuring a provider, when and how a token is verified
- [Field actions: services and limits](field-actions.md) — where a check's service and credential are configured, the limits of the pre-check endpoint, and how to try a check without an account
- [Results viewing permissions](results-permissions.md) — per-form access to the submission results, deletion, what happens when a form is deleted
- [Create accounts and sign in from a form](how-to-accounts-actions.md) — the `formidable-accounts` module: author steps, the CAPTCHA and rate limits it requires, e-mail activation, what it protects and what it does not
- [Create content from a form](how-to-content-action.md): the `formidable-content` module: the folders and groups to prepare, the mapping lines, how files are stored, the security model (who may write where) and what the module does not do
- [Importing the forms and results of Jahia Forms](forms-import.md) — the export to take, the Import button of the Results page, what the import recreates and reports, what to do after

Choice fields can take their options from categories or from content: declaring those sources is
described with the feature, in [Choice field options sources](../architecture/choice-field-options-sources.md#declaring-sources-administrator).
