# CAPTCHA Server-side Validation

The captcha integration covers both the front-end widget (rendering + token collection) and server-side token verification.

## Configuration

CAPTCHA is configured globally via `org.jahia.modules.formidable.cfg` (or Felix Web Console). No JCR node is needed.

| Property | Description |
|---|---|
| `captchaSiteKey` | Public key — used by the front-end widget |
| `captchaScriptUrl` | Provider JS API URL — injected in the page to render the widget |
| `captchaWidgetVar` | Name of the global `window` object exposed by the provider script (e.g. `turnstile`, `hcaptcha`, `grecaptcha`) |
| `captchaTokenField` | Name of the hidden form field auto-injected by the widget (e.g. `cf-turnstile-response`) |
| `captchaVerifyUrl` | Provider siteverify endpoint — used for server-side verification |
| `captchaSecretKey` | Secret key — used for server-side verification, never exposed to the client |

To enable CAPTCHA on a form, apply the `fmdbmix:captcha` mixin to the `fmdb:form` node.
This author-facing mixin extends `fmdbmix:captchaProtectedForm`.

---

## When is the token verified server-side?

Always — when CAPTCHA is enabled on the form, the submission pipeline verifies the token first,
before any action in the pipeline runs.

---

## How verification works

`FormSubmissionPipeline.verifyCaptcha(...)` runs in the submission pipeline. It:

1. Checks that the form carries `fmdbmix:captchaProtectedForm`
2. Reads the `ct` request parameter from the submission URL/query string
3. Calls `FormidableConfigService.verifyCaptcha(token, remoteAddr)`
4. `FormidableConfigService` reads `captchaSecretKey` and `captchaVerifyUrl` from OSGi config
5. It POSTs to `captchaVerifyUrl` with `secret` + `response` + optional `remoteip`
6. If verification fails, the pipeline stops with `FMDB-006` / HTTP 400

## Token field names (auto-injected by the widget)

| Provider | Field name in POST |
|---|---|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha | `h-captcha-response` |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

## Verification endpoints

| Provider | Endpoint |
|---|---|
| Cloudflare Turnstile | `https://challenges.cloudflare.com/turnstile/v0/siteverify` |
| hCaptcha | `https://hcaptcha.com/siteverify` |
| Google reCAPTCHA v2 | `https://www.google.com/recaptcha/api/siteverify` |

All three share the same request shape (`secret` + `response` + optional `remoteip`) and the same `{ "success": boolean }` response.

---

## Error codes (Turnstile)

| Code | Meaning |
|---|---|
| `missing-input-secret` | Secret key not provided |
| `invalid-input-secret` | Secret key is invalid |
| `missing-input-response` | Token not provided |
| `invalid-input-response` | Token is malformed or expired |
| `timeout-or-duplicate` | Token already used or expired (300 s limit) |
