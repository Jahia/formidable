---
# Allowed version bumps: patch, minor, major
formidable: minor
---

An extension point for form actions shipped by other modules: a contextual action receives the response, the locale and the site, can return a same-origin redirect and a message the visitor reads, can refuse a submission with its own message (FMDB-018), and can declare that it consumes sensitive values. A new password input (formidable-extended-inputs) is a sensitive field: its value reaches only such an action, never Save to JCR, the e-mail and forward actions, jExperience, the field actions or the logs, and a placeholder in a form message never prints it. A redirect action sends the visitor to a page of the site, or a same-origin path, after a submission.
