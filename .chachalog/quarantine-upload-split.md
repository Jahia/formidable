---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added the hold of the uploads of a form, for a module that quarantines files (#489)

A form has a new option, "Hold uploads in quarantine". With the option on, the actions on submission receive the values and no file, and a second list, "Actions after release", receives the files once they are released. Save to JCR, the two e-mail actions and the forward action can run in that second list. The option needs a module that holds the files: without one, the form refuses a submission that carries a file, with the new error code FMDB-018. A form with the option off keeps its behaviour.

A module holds the files through the new `SubmissionHold` service, and releases a submission through the new `DeferredActions` service. An action type of your own runs after release when it declares `fmdbmix:deferrableAction`. Such an action has no HTTP request: it reads the language, the referer and the time zone that the engine kept, and nothing else.

An administrator can make the quarantine mandatory with the new setting `uploadsRequireQuarantine` of `org.jahia.modules.formidable.uploads.cfg`, `false` by default. With `true`, a form with the option off hands its uploads to no action.
