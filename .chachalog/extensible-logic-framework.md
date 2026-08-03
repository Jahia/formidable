---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Conditional-logic sources are now declared through semantic value-kind mixins (`fmdbmix:choiceField`/`dateField`/`numberField`/`booleanField`), so any module's field type can offer show/hide rules with the operators of its kind — including the new numeric and boolean operator sets. Form elements also gain a stable `fieldKey` identity so rules survive renames, copies and imports (#172)
