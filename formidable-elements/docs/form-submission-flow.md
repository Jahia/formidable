# Form Submission Flow

## Overview

The form submission flow depends on the properties configured on the `fmdb:form` node via the `fmdbmix:actionPipeline` mixin.

Three properties drive the behaviour:

| Property | Type | Role |
|---|---|---|
| `captcha` | weakreference → `fmdb:captchaAction` | Drives the front-end widget; verified server-side in JCR mode only |
| `destination` | weakreference → `fmdbmix:formDestination` | Where form data goes: JCR storage or third-party transfer |
| `actions` | weakreference-multiple → `fmdbmix:formSideEffect` | Side effects run after a successful destination (e.g. email) |

---

## Two modes

### JCR mode (`destination` = `fmdb:save2jcrAction` or absent)

```
Browser
  └─ POST FormData → submitActionUrl (.formidableSubmit.do)
       Jahia pipeline (enforced order):
         1. captcha server-side verification (if captcha configured)
         2. save2jcr (if destination configured)
         3. side effects in order (email, ...)
       └─ { "success": true }
  └─ show success message
```

Captcha token is verified by Jahia before any data is stored or any email is sent.

### Transfer mode (`destination` = `fmdb:sendDataAction`)

```
Browser
  └─ POST FormData → destinationUrl (third-party, multipart/form-data)
       includes: all fields + files + captcha token
       third party verifies the captcha token itself
       └─ 2xx OK
  └─ POST FormData → submitActionUrl (.formidableSubmit.do)   [only if side effects configured]
       Jahia pipeline (captcha NOT re-verified — token is single-use):
         1. side effects in order (email, ...)
       └─ { "success": true }
  └─ show success message
```

If the destination rejects (non-2xx), the pipeline is **not called** — side effects do not run.

---

## Captcha behaviour by mode

| Mode | Widget rendered | Jahia verifies token | Who verifies token |
|---|---|---|---|
| JCR | Yes (if `captcha` set) | ✅ Yes | Jahia (`CaptchaVerificationFormAction`) |
| Transfer | Yes (if `captcha` set) | ❌ No | The third-party destination |

The captcha token is single-use. In transfer mode the browser sends it to the destination — Jahia must not consume it.

---

## Token field names (auto-injected by the widget into FormData)

| Provider | Field name |
|---|---|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha | `h-captcha-response` |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

---

## Pipeline actions

### `fmdb:captchaAction` (referenced via `captcha` property)

Configured once per form. Provides `siteKey` + `scriptUrl` for the front-end widget and `secretKey` for server-side verification.

### Destinations (`destination` property — one of)

| Node type | Description |
|---|---|
| `fmdb:sendDataAction` | Client POSTs full `FormData` directly to `targetUrl` (files supported). Jahia never sees the data. |
| `fmdb:save2jcrAction` | *(not yet implemented)* Will save form data as JCR child nodes |

### Side effects (`actions` property — multiple)

| Node type | Class | Description |
|---|---|---|
| `fmdb:emailAction` | `SendEmailFormAction` | Sends email via Jahia `MailService`; `${fieldName}` interpolation in subject and body |

---

## Key files

| File | Role |
|---|---|
| `src/components/Form/Form.client.tsx` | `handleSubmit` — POST to destination first (if transfer mode), then pipeline |
| `src/components/Form/default.server.tsx` | Computes `destinationUrl` and `submitActionUrl`; reads `captcha` node for widget config |
| `formidable-engine/.../FormSubmitAction.java` | Jahia `Action` OSGi — enforces pipeline order per mode |
| `formidable-engine/.../CaptchaVerificationFormAction.java` | Verifies token server-side (JCR mode only) |
| `formidable-engine/.../SendEmailFormAction.java` | Sends email with `${fieldName}` interpolation |
