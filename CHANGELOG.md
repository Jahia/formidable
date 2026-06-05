# formidable Changelog

## 0.2.0

### Breaking Changes

* Implement weakref model for conditional logic field resolution (#73)

* Clarified form type ownership and updated server-side semantics for containers, CAPTCHA, and authentication. (#86)

* Conditional step visibility in multi-step forms (#62)

* Conditional required field verification based on conditional rule (#62)

### New Features

* Hardened form CAPTCHA handling by validating verification endpoints and moving CAPTCHA tokens from URL parameters to secure request headers.

* Changed the public Java package for custom form actions to `org.jahia.modules.formidable.engine.api`. If you have custom form action implementations that import from the previous package, update your imports to use the new package and recompile your module.

* Add results permissions and delete action (#76)

### Bug Fixes

* Fixed form submissions so authentication and CAPTCHA can't be bypassed during repository errors (#79)

* Updated documentation based on review feedback.

* Improved form submission error handling and documented operational limits.

* Removed User and IP Address columns from the Form Results submissions view.

* Hardened form submission against cross-origin requests by requiring same-origin Origin/Referer checks (#82)
  Fixed uploaded file MIME detection so declared filenames no longer influence allowed-type checks (#82)

* Removed `ipAddress`, `submitterUsername`, and `userAgent` from newly stored form submissions; existing submissions keep these legacy properties but new CSV and JSON exports no longer include them.
  Hardened forwarded form submissions by sanitizing multipart field names to prevent header injection.
  Changed form submissions to be rejected with `FMDB-012` when the configured action list cannot be read, and with `FMDB-500` when form field metadata cannot be collected, instead of silently continuing.

* Improved form submission validation and updated email/forward actions to use shared utilities (#87)

* Package.json dependencies and versions (#99)

* Added sample extension modules and made conditional-logic rendering consistent across form containers.

* Review 2.1245 (#84)

* Improved reliability of escaping, validation, and configuration handling by expanding Java unit tests. (#92)

* Tighten outbound action safety checks (#80)

## 0.1.0

### New Features

* Initial Formidable alpha foundation for Jahia 8.2+
* Form rendering based on Jahia JavaScript modules
* Editor extensions powered by Module Federation
* First custom selector UI: SelectOptions
* Additional editor-side support for conditional logic
* Server-side submission pipeline
* Built-in actions (Save to JCR, Send email notification, Send submitted content by email, Forward submission to an external endpoint)
* Built-in protections (CAPTCHA support, optional authenticated-user-only submission guard)
* First Form Results administration UI in jContent for stored submissions
