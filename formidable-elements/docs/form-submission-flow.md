# Form Submission Flow

## Overview

Form submission is handled by `FormSubmitServlet`, a dedicated OSGi servlet registered via
`HttpService` at `/modules/formidable-engine/form-submit`. It operates **outside the Jahia
render chain**, ensuring it is always the first consumer of the multipart request stream.

The pipeline is driven by the child nodes of the `actions` node (`fmdb:actionList`)
autocreated on every `fmdb:form`. Actions are executed in order, as configured by the
contributor.

> **Why a dedicated servlet?**  
> For Guest users, Jahia's FileUpload filter consumes the multipart stream before Jahia
> `Action` services run. The servlet bypasses this filter entirely.

---

## Pipeline

```
Browser
  └─ POST multipart/form-data → /modules/formidable-engine/form-submit?fid=UUID&lang=fr[&ct=TOKEN]
       FormSubmitServlet → FormSubmissionPipeline (outside Jahia render chain):

         Step 1  verifyMultipart         Content-Type must be multipart/form-data
         Step 2  readRoutingParams       fid + lang read from URL query params     — 0 byte read
         Step 3  guardContentLength      reject if Content-Length > max            — 0 byte read
         Step 4  resolveFormNode         JCR getNodeByIdentifier(fid) in "live"    — 0 byte read
         Step 5  verifyAuthentication    if fmdbmix:requireAuthentication → reject Guest
         Step 6  verifyCaptcha           if fmdbmix:captcha → verify 'ct' URL param — 0 byte read
         Step 7  collectFormFieldInfo    walk form node: build field whitelist
                                         + per-field accept types (fmdb:inputFile)  — 0 byte read
         Step 8  parseMultipart          FIRST AND ONLY read of the request stream
                   ├─ undeclared fields skipped inline (not read, not stored)
                   ├─ text fields  → Map<String, List<String>> parameters
                   └─ file parts   → List<FormFile>
                        ├─ size limits (per-file + total)
                        ├─ Tika magic-byte MIME detection
                        └─ MIME allowlist (field accept or global cfg)
         Step 9  dispatchActions         execute fmdb:actionList nodes in order
                   └─ fmdb:forwardAction:
                        - reads pre-parsed files from request attribute
                        - reconstructs multipart/form-data body
                        - POSTs to targetUrl

       └─ { "success": true } or { "success": false, "errorCode": "FMDB-XXX" }
  └─ show success or error message
```

> **DoS mitigation (defence in depth):**
> - Step 3: oversized requests rejected on `Content-Length` header alone — 0 byte read
> - Step 6: invalid CAPTCHA token → rejected before any file data is read
> - Step 7: field whitelist built before parsing — undeclared fields never touch memory or disk
> - Steps 4–7 are all O(1) or cheap JCR lookups; the network stream is only read at step 8

### URL parameters set by `default.server.tsx` / `Form.client.tsx`

| URL param | Value | Set by |
|---|---|---|
| `fid` | JCR identifier (UUID) of the `fmdb:form` node | `default.server.tsx` |
| `lang` | BCP 47 language tag (e.g. `en`, `fr`) | `default.server.tsx` |
| `ct` | CAPTCHA token — only when `fmdbmix:captcha` is present | `Form.client.tsx` at submit time |

No hidden `<input>` fields are injected into the form body for routing.
The CAPTCHA widget field (`cf-turnstile-response`, etc.) is deleted from `FormData` by
`Form.client.tsx` before submission — it is never in the body, only in `ct`.

Submissions are always processed against the `live` workspace.
Edit and preview modes disable the submit button server-side; any bypass attempt receives
a 400 (`FMDB-004`) because the node will not be found in `live`.

---

## Security mixins on `fmdb:form`

| Mixin | Effect |
|---|---|
| `fmdbmix:captcha` | Requires a valid CAPTCHA token (`ct`) — verified at step 6 before any file data is read |
| `fmdbmix:requireAuthentication` | Rejects Guest (anonymous) submissions at step 5 with `FMDB-009` |

Both mixins are applied via the Content Editor. Neither requires configuration in JCR properties.

---

## Field whitelist (step 7 → step 8)

`collectFormFieldInfo()` walks the `fmdb:form` node tree (direct children + `fmdb:step`
children, excluding `fmdb:actionList`) and builds a `Set<String>` of declared field names.

During `parseAll()`, any multipart item whose field name is absent from this set is
**skipped without reading its content**. The Commons FileUpload iterator advances past
undeclared items automatically.

This prevents:
- Injection of arbitrary fields into forwarded data
- Exploitation of `${fieldName}` interpolation in email templates
- Storage or processing of data the form was not designed to collect

---

## File upload security (FormDataParser.parseAll)

All file parts pass through `FormDataParser` which enforces the following controls in order:

| # | Control | Implementation |
|---|---|---|
| 1 | Field whitelist | Undeclared fields skipped before any read |
| 2 | Per-file size limit | `upload.setFileSizeMax()` |
| 3 | Total request size limit | `upload.setSizeMax()` |
| 4 | File part count limit (CVE-2023-24998) | `upload.setFileCountMax()` — requires commons-fileupload ≥ 1.5 |
| 5 | Filename sanitisation | Path traversal (`../`) and XSS chars stripped; UUID used for storage name |
| 6 | MIME type detection | Apache Tika magic-byte detection (ignores client-supplied `Content-Type`) |
| 7 | MIME type allowlist | Field-level `accept` property takes priority; falls back to global cfg allowlist |

Limits and the global allowlist are configured in `org.jahia.modules.formidable.cfg` via `FormidableConfig`.

---

## CAPTCHA

| Condition | Behaviour |
|---|---|
| `fmdbmix:captcha` mixin present on the form | Token verified at step 6 — before any file data is read |
| `fmdbmix:captcha` mixin absent | No verification, pipeline continues |

CAPTCHA configuration (`siteKey`, `scriptUrl`, `verifyUrl`, `secretKey`) is read from
`org.jahia.modules.formidable.cfg` — not stored in JCR.

The CAPTCHA widget injects a hidden field into the DOM, but `Form.client.tsx` removes it
from `FormData` before submission and passes the token as the `ct` URL query param instead.
The token never appears in the request body.

| Provider | Widget field (removed client-side) |
|---|---|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha | `h-captcha-response` |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

---

## Action nodes (`fmdb:actionList`)

Every `fmdb:form` has an autocreated `actions` child node of type `fmdb:actionList`. The
contributor adds action nodes inside via the Content Editor. Actions are executed in the order
they appear in the list.

| Node type | Description |
|---|---|
| `fmdb:save2jcrAction` | Saves form data as JCR child nodes (not yet implemented) |
| `fmdb:emailAction` | Sends email via Jahia `MailService`; `${fieldName}` interpolation in subject and body |
| `fmdb:forwardAction` | Forwards declared form fields + pre-parsed files to a third-party endpoint as `multipart/form-data` |

To add a new action type, see `AGENTS.md` → *Form action pipeline*.

---

## Error responses

Failed submissions return `{ "success": false, "errorCode": "FMDB-XXX" }`.
Detailed reasons are written to server logs only. See `docs/error-codes.md` for the full glossary.

---

## Edit / Preview mode

`default.server.tsx` passes `isSubmitDisabled = true` when
`renderContext.isEditMode() || renderContext.isPreviewMode()`. The submit button is disabled
and shows a tooltip. No request is sent to the servlet.

---

## CSRF

Form submissions use `XMLHttpRequest` (not `fetch`). Jahia's OWASP CSRFGuard patches
`XMLHttpRequest.prototype.send` at page load to inject the CSRF token automatically.
`fetch` is not patched and must not be used for form submission.

> **Note:** Verify that `/modules/formidable-engine/*` is covered by the CSRF filter's
> protected pages configuration (`CSRFGuard.properties`), not only `*.do` patterns.

---

## Key files

| File | Role |
|---|---|
| `src/components/Form/Form.client.tsx` | `handleSubmit` — removes CAPTCHA body field, appends `ct` URL param, POSTs via XHR |
| `src/components/Form/default.server.tsx` | Builds `submitActionUrl` with `fid` and `lang` query params |
| `formidable-engine/.../servlet/FormSubmitServlet.java` | OSGi entry point — thin wrapper, delegates to `FormSubmissionPipeline` |
| `formidable-engine/.../servlet/FormSubmissionPipeline.java` | 9-step pipeline — all submission logic |
| `formidable-engine/.../servlet/ErrorCode.java` | Error code enum — see `docs/error-codes.md` |
| `formidable-engine/.../actions/FormDataParser.java` | Secure multipart parser (whitelist, Tika, allowlist, size + count limits) |
| `formidable-engine/.../actions/ForwardFormAction.java` | Reads pre-parsed files from request attribute; forwards declared fields only |
| `formidable-engine/.../actions/FormAction.java` | Interface implemented by each action type |
| `formidable-engine/.../config/FormidableConfigService.java` | Reads unified cfg (CAPTCHA + upload limits); verifies CAPTCHA token |
