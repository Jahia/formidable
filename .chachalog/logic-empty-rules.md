---
# Allowed version bumps: patch, minor, major
formidable: patch
---

Changed rule saving so a visibility rule whose target was never chosen is removed instead of hiding the field (#193)

Rules whose reference is filled but invalid are kept and shown in error in the editor.
