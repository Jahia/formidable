---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed source-based option settings to be required: a choice field must name the source its options mode needs (#196)

**Breaking change.** You are affected if you create or import forms programmatically with a source-based options mode: the field can no longer be saved without the settings that mode needs — the source for a declared source, the root category for category options, the root node and content type for content options. The list of manually typed options is NOT required. In the editor nothing changes beyond the standard required indicators; existing forms are not modified, but the next edit of an incomplete field asks for the missing values.
