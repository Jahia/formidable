---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added choice-field options filled live from the site contents under a picked root, with an edit and live preview (#196)

The submitted value is the content path relative to the picked root, and the list is capped by a configurable limit: above it the field reports an error instead of silently truncating the options. Note that publishing the form also publishes the picked root and the contents under it; mark drafts as work in progress to keep them out.
