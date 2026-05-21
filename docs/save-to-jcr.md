# Save To JCR

This document describes how the `fmdb:save2jcrAction` server-side action stores Formidable submissions in JCR.

## Overview

`SaveToJcrFormAction` is a `FormAction` OSGi service bound to the JCR node type:

- `fmdb:save2jcrAction`

When a form submission reaches the server-side pipeline, this action persists:

- submission metadata
- submitted field values
- uploaded files

under a site-level results tree.

## Storage Tree

The action stores data under:

```text
/sites/<site>/formidable-results/<form-results>/submissions/YYYY/MM/DD/<submission>
```

Example:

```text
/sites/industrial/formidable-results/form-actions/submissions/2026/05/05/submission-20260505-150707-a9e
```

The node types are:

- `fmdb:resultsFolder`
- `fmdb:formResults`
- `fmdb:submissions`
- `fmdb:splittedSubmission`
- `fmdb:formSubmission`

## One Results Folder Per Form

Each form has one `fmdb:formResults` node.

Identity is resolved through the `parentForm` weak reference stored on the `fmdb:formResults` node:

- if a matching `parentForm` already exists, that node is reused
- if the form JCR name changed, the existing `fmdb:formResults` node is renamed to the current form name
- if no matching node exists, a new one is created

This gives two properties:

- stable identity across form renames
- human-readable folder names

## Submission Node Naming

Each submission node is created with a readable name:

```text
submission-YYYYMMdd-HHmmss-XXX
```

Example:

```text
submission-20260505-150707-a9e
```

Where:

- `YYYYMMdd-HHmmss` is the server-side timestamp
- `XXX` is a short UUID-derived suffix

If a collision still happens, `JCRContentUtils.findAvailableNodeName(...)` adds Jahia's standard numeric suffix.

## Auto-Splitting

The `submissions` child node is configured for Jahia auto-splitting with:

```text
date,jcr:created,yyyy;date,jcr:created,MM;date,jcr:created,dd
```

This means the submission is first created under `submissions`, then moved into:

- year folder
- month folder
- day folder

using `JCRAutoSplitUtils.applyAutoSplitRules(...)`.

The content definition also allows direct `fmdb:formSubmission` children under `fmdb:submissions` as a fallback, so persistence is not blocked if splitting cannot happen.

## Important Implementation Detail

After auto-splitting, the submission node may have moved to another path.

Because of that, the implementation always reloads the node by identifier before writing the form data and files. This avoids using a stale JCR wrapper that still points to the pre-move path.

## Data Layout Inside One Submission

Each `fmdb:formSubmission` contains:

- metadata properties on the submission node itself
- a `data` child node for field values
- a `files` child node for uploaded files

### Metadata Properties

Typical submission-level properties:

- `origin`
- `status`
- `ipAddress`
- `locale`
- `submitterUsername`
- `userAgent`
- `referer`

### Field Values

Field values are stored as JCR properties on the `data` child node (`fmdb:submissionData`):

```text
<submission>/data
```

The `fmdb:submissionData` type uses residual property definitions with `indexed=no`:

```
- * (string) indexed=no
- * (string) multiple indexed=no
```

This ensures submitted form data (names, emails, addresses) never enters the Lucene search index — avoiding index bloat, improving re-indexing performance, and keeping personal data out of search results. The dashboard queries submissions by `jcr:created` on `fmdb:formSubmission`, then navigates to `data` to read properties directly.

Rules:

- single-value fields become single JCR properties
- multi-value fields become multi-valued JCR properties

### Uploaded Files

Uploaded files are stored under:

```text
<submission>/files/<fieldName>/<fileName>/jcr:content
```

The file binary is stored in `jcr:data`, with:

- `jcr:mimeType`
- `jcr:lastModified`

The storage file name is based on the parser-generated safe storage name, then normalized through `findAvailableNodeName(...)`.

## Execution Flow

At runtime, the action performs the following steps:

1. Resolve the parent form from the action node.
2. Read validated uploaded files from `FormDataParser.PARSED_FILES_ATTR`.
3. Resolve or create `/sites/<site>/formidable-results`.
4. Resolve or create the form-specific `fmdb:formResults`.
5. Ensure auto-splitting is enabled on `submissions`.
6. Create the `fmdb:formSubmission` node.
7. Save the session.
8. Apply Jahia auto-splitting.
9. Save the session again.
10. Reload the moved submission node by identifier.
11. Write submitted field values into `data`.
12. Write uploaded files into `files`.
13. Save the session.

## Notes

- The `files` node is not autocreated — it is only added when uploaded files are present. This avoids an empty `jnt:folder` on every text-only submission.
- Field values use residual properties on a single `data` node rather than one child node per field. A 20-field form produces 1 node with 20 properties, not 20 child nodes. At 10,000 submissions this avoids 190,000 unnecessary nodes.
- Submissions are written directly to the `live` workspace using a system session. There is no publish step.
- Folder names are intended to stay readable for operators.
- `parentForm` is the real form identity key.
- Renaming a form does not create a second logical results folder; the existing folder is reused and renamed.
