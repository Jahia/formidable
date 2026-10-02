---
# Allowed version bumps: patch, minor, major
formidable: patch
---

Improved the rules editor of Conditional display, which reads a field's sources once instead of twice per rule (#372).
Opening a field with rules, or adding a rule, no longer waits on two queries in sequence for every rule row.
