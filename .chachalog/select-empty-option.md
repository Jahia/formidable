---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added a configurable empty option label on select fields, so they start empty instead of preselecting a value (#196)

Single-choice selects only: the option is not rendered on multiple selects. Starting empty makes the required validation of the field effective in the browser.
