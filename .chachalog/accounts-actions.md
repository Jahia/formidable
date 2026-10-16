---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Added the formidable-accounts module (Create account and Sign in form actions), a password field type, sensitive fields, contextual form actions that answer the browser, and the redirect action. The groups a form may add a visitor to are an administrator's allow list, the activation link uses only the configured public origin, back-office accounts never sign in through a form, and only an action last saved by someone holding `formidableAccountsActions` runs (review jahia-private#5909).
