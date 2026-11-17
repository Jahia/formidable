---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added the split of the uploads for a form that quarantines its files (#489)

A form action can now declare from its Java code that it quarantines the uploads of a form, with `ContextualFormAction#quarantinesUploads(actionNode)`. The uploads of such a form reach the quarantining action alone: Save to JCR, the e-mail actions, the forward action and any third-party action receive the values and no file. A form with no quarantining action keeps its behaviour.

An administrator can make the quarantine mandatory with the new setting `uploadsRequireQuarantine` of `org.jahia.modules.formidable.uploads.cfg`, `false` by default. With `true`, a form with no quarantining action hands its uploads to no action.

A module of your own can store a submission the way Save to JCR does, with `FormResultsStore#storeSubmission`.
