---
# Allowed version bumps: patch, minor, major
formidable: patch
---

Removed `ipAddress`, `submitterUsername`, and `userAgent` from newly stored form submissions; existing submissions keep these legacy properties but new CSV and JSON exports no longer include them.
Hardened forwarded form submissions by sanitizing multipart field names to prevent header injection.
Changed form submissions to be rejected with `FMDB-012` when the configured action list cannot be read, and with `FMDB-500` when form field metadata cannot be collected, instead of silently continuing.
