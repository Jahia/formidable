---
# Allowed version bumps: patch, minor, major
formidable: patch
---

Fixed form submissions being rejected for logged-in visitors on the upcoming Jahia CSRF protection (#200)

Affects platforms running jahia-csrf-guard 4.3 and later: authenticated visitors received an error on every form submission, while anonymous visitors were unaffected. Cross-site submissions remain rejected by the built-in origin check.
