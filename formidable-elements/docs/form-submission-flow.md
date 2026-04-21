# Form Submission Flow

## Overview

Form submission is handled server-side by `FormSubmitAction`, a Jahia `Action` OSGi service.

The pipeline is driven by the child nodes of the `actions` node (`fmdb:actionList`) autocreated on every `fmdb:form`. Actions are executed in order, as configured by the contributor.

---

## Pipeline

### Text fields only (no file upload)

```
Browser
  └─ POST FormData → .formidableSubmit.do
       Jahia pipeline (enforced order):
         1. CAPTCHA server-side verification (if fmdbmix:captcha mixin is present)
         2. Actions in order (fmdb:actionList child nodes)
       └─ { "success": true | "success": false, "message": "..." }
  └─ show success or error message
```

### With file uploads

```
Browser
  └─ POST FormData (text fields + files) → .formidableSubmit.do
       Jahia pipeline (enforced order):
         1. CAPTCHA server-side verification (if fmdbmix:captcha mixin is present)
         2. Actions in order (fmdb:actionList child nodes)
            └─ fmdb:forwardAction:
                 - reads multipart stream directly via Commons FileUpload
                   (Jahia does not consume file parts — no <multipart-config> in web.xml)
                 - applies FormDataParser security controls (see below)
                 - reconstructs multipart/form-data body
                 - strips CAPTCHA token fields
                 - POSTs to targetUrl
       └─ { "success": true | "success": false, "message": "..." }
  └─ show success or error message
```

> **Note:** There is no pre-upload step. Files are streamed directly inside the main submit
> request and processed by `FormDataParser` inside `ForwardFormAction`.

---

## File upload security (FormDataParser)

All file parts pass through `FormDataParser` which enforces the following controls in order:

| # | Control | Implementation |
|---|---|---|
| 1 | Content-Type validation | `ServletFileUpload.isMultipartContent()` |
| 2 | Per-file size limit | `upload.setFileSizeMax()` |
| 3 | Total request size limit | `upload.setSizeMax()` |
| 4 | File part count limit (CVE-2023-24998) | `upload.setFileCountMax()` |
| 5 | Filename sanitisation | Path traversal (`../`) and XSS chars stripped; UUID used for storage name |
| 6 | MIME type detection | Apache Tika magic-byte detection (ignores client-supplied `Content-Type`) |
| 7 | MIME type allowlist | Field-level `accept` property takes priority; falls back to global cfg allowlist |

Limits and the global allowlist are configured in `org.jahia.modules.formidable.cfg` via `FormidableConfig`.

---

## CAPTCHA

| Condition | Behaviour |
|---|---|
| `fmdbmix:captcha` mixin present on the form | Token verified server-side before any action runs |
| `fmdbmix:captcha` mixin absent | No verification, pipeline runs immediately |

CAPTCHA configuration (`siteKey`, `scriptUrl`, `verifyUrl`, `secretKey`) is read from
`org.jahia.modules.formidable.cfg` — not stored in JCR.

Token field names auto-injected by the widget (stripped from forwarded data):

| Provider | Field name |
|---|---|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha | `h-captcha-response` |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

---

## Action nodes (`fmdb:actionList`)

Every `fmdb:form` has an autocreated `actions` child node of type `fmdb:actionList`. The contributor adds action nodes inside via the Content Editor. Actions are executed in the order they appear in the list.

| Node type | Description |
|---|---|
| `fmdb:save2jcrAction` | Saves form data as JCR child nodes (not yet implemented) |
| `fmdb:emailAction` | Sends email via Jahia `MailService`; `${fieldName}` interpolation in subject and body |
| `fmdb:forwardAction` | Forwards form data (text + files) to a third-party endpoint as `multipart/form-data` |

To add a new action type, see `AGENTS.md` → *Form action pipeline*.

---

## Edit / Preview mode

`default.server.tsx` passes `isSubmitDisabled = true` when
`renderContext.isEditMode() || renderContext.isPreviewMode()`. The submit button is disabled
and shows a tooltip. No request is sent to `formidableSubmit.do`.

---

## CSRF

Form submissions use `XMLHttpRequest` (not `fetch`). Jahia's OWASP CSRFGuard patches
`XMLHttpRequest.prototype.send` at page load to inject the CSRF token automatically.
`fetch` is not patched and must not be used for form submission.

---

## Key files

| File | Role |
|---|---|
| `src/components/Form/Form.client.tsx` | `handleSubmit` — POST FormData (with files) to `submitActionUrl` via XHR |
| `src/components/Form/default.server.tsx` | Computes `submitActionUrl`; reads `fmdbmix:captcha` attrs for widget |
| `formidable-engine/.../FormSubmitAction.java` | Jahia `Action` OSGi — runs CAPTCHA then iterates `actions` child nodes |
| `formidable-engine/.../FormDataParser.java` | Secure multipart parser (Tika, allowlist, size + count limits) |
| `formidable-engine/.../ForwardFormAction.java` | Reads file stream via `FormDataParser`, builds multipart body, POSTs to `targetUrl` |
| `formidable-engine/.../FormAction.java` | Interface implemented by each action type |
| `formidable-engine/.../FormidableConfigService.java` | Reads unified cfg (CAPTCHA + upload limits); verifies CAPTCHA token |
