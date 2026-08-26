---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed choice-field option settings to be required: a field can no longer be saved without a usable option list (#196)

**Breaking change.** You are affected if you create or import forms programmatically: a choice field can no longer be saved without the settings its options mode needs — the option list for manually typed options, the source for a declared source, the root category for category options, the root node and content type for content options. In the editor nothing changes beyond the standard required indicators; existing forms are not modified, but the next edit of an incomplete field asks for the missing values.
