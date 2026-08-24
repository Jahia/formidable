---
# Allowed version bumps: patch, minor, major
formidable: patch
---

Fixed choice option values diverging between languages: every language now shares the default language's set (#211)

Options are authored in the site's default language; other languages only translate the labels, starting from Copy a language. The options list is no longer required, so visiting another language never blocks saving a form field.
