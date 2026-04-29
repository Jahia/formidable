# CAPTCHA Server-side Validation

The captcha integration covers both the front-end widget (rendering + token collection) and server-side token verification.

## Configuration

CAPTCHA is configured globally via `org.jahia.modules.formidable.captcha.cfg` (or Felix Web Console). No JCR node is needed.

| Property | Description |
|---|---|
| `siteKey` | Public key — used by the front-end widget |
| `scriptUrl` | Provider JS API URL — injected in the page to render the widget |
| `verifyUrl` | Provider siteverify endpoint — used for server-side verification |
| `secretKey` | Secret key — used for server-side verification, never exposed to the client |

To enable CAPTCHA on a form, apply the `fmdbmix:captcha` mixin to the `fmdb:form` node.

---

## When is the token verified server-side?

Always — when `fmdbmix:captcha` is present on the form, `FormSubmitAction` verifies the token first, before any action in the pipeline runs.

---

## How verification works

`CaptchaConfigService.verify()` is called first in the pipeline. It:

1. Reads `secretKey` and `verifyUrl` from the OSGi configuration
2. Reads the token from the POST parameters (tries each known provider field name)
3. POSTs to `verifyUrl` with `secret` + `response` + optional `remoteip`
4. Returns `false` if `success` is not `true` — `FormSubmitAction` stops the pipeline with HTTP 400

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

