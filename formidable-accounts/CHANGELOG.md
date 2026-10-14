# formidable-accounts Changelog

Release notes for the whole repository are assembled by [chachalog](../.chachalog) into the root
`CHANGELOG.md`; this file tracks the module itself so it can be read (or extracted) on its own.

## Unreleased (0.6.0-SNAPSHOT)

### New Features

* New module: **Create account** (`fmdbacc:createAccountAction`) and **Sign in** (`fmdbacc:signInAction`) form
  actions. A visitor registers, or signs in, from a Formidable form; the sign-in is a real Jahia login through
  `AuthenticationService` (Jahia 8.2.3.0 and later), so the login event fires and jExperience merges the visitor's
  anonymous profile into the user's.
* Create account: user name from the e-mail (lower case, sanitised, checked with the platform's own syntax rule),
  password hashed by `JahiaUserManagerService.createUser`, first and last name, optional group (global, `name:siteKey`
  or path; privileged groups are refused), sign-in afterwards (default on), redirect to a page or back to the place named
  by a return parameter of the page, a password rule (regular expression, default one digit, one lower-case, one
  upper-case letter, one symbol, 8 to 64 characters).
* Optional e-mail activation (default off): the account is created locked with a random token (only its SHA-256 is
  stored) and an expiry (hours, default 48), the link is mailed with Jahia's mail service and followed on
  `/modules/formidable-accounts/verify`, which unlocks the account and sends the visitor to a chosen page.
* `org.jahia.modules.formidable.accounts.cfg`: CAPTCHA required (default), attempts per remote address and per account in
  memory, groups a form can never add a visitor to, public base URL and sender of the activation e-mail.
* English and French labels, tooltips and visitor messages; `dev-unsigned` Maven profile for local deployments on Jahia
  Enterprise.

### Security

* Both actions refuse to run on a form without the CAPTCHA option unless the operator set `requireCaptcha=false`.
* Attempts are limited per remote address and per account; the sentence is the same for an account that exists and one
  that does not, and the same for every cause of a failed sign-in or sign-up (a taken address included).
* The password is a sensitive value (`fmdbmix:sensitiveField`): it is never stored, mailed, forwarded, logged, echoed or
  sent to jExperience; a password field that is not sensitive is refused.
* No CSRF whitelist: the actions ride the form-submit servlet (Security Filter, same origin, CAPTCHA); the verification
  endpoint is a GET that only follows an unguessable single-use token.

### Tests

* JUnit: the rate limiter, the password rule, the user name rule, the activation token and mail, the directory on mocked
  Jahia services, the authenticator, the guard, both actions, the verification servlet, the field chooser.
* Cypress: `91-accounts-create-account`, `92-accounts-sign-in`, `93-accounts-no-password-leak`.

### Documentation

* `README.md` and `docs/administration/how-to-accounts-actions.md`.
