# Form Submission Flow

## Overview

Form submission is handled by `FormSubmitServlet`, a dedicated OSGi servlet registered via
the **OSGi HTTP Whiteboard** (`service = { HttpServlet.class, Servlet.class }`, `alias=/formidable-engine/form-submit`).
It operates **outside the Jahia render chain**, ensuring it is always the first consumer of
the multipart request stream.

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

         Gate 0   checkSecurityFilter     require auto-applied `formidable-submit` scope
                                          (same-origin via Origin/Referer)
         Step 1   verifyMultipart         Content-Type must be multipart/form-data
         Step 2   readRoutingParams       fid (UUID-validated) + lang read from URL query params  — 0 byte read
         Step 3   guardContentLength      early reject if Content-Length > max                    — 0 byte read
         Step 4   resolveFormNode         JCR getNodeByIdentifier(fid) in "live"                 — 0 byte read
         Step 5   verifyAuthentication    if fmdbmix:requireAuthentication → reject Guest
         Step 6   verifyCaptcha           if fmdbmix:captcha → verify 'ct' URL param             — 0 byte read
         Step 7   collectFormFieldInfo    walk form node: build field whitelist,
                                          per-field type, allowed choices, accept types,
                                          and field constraints (required, min/maxLength,
                                          pattern, min/maxDate)                                   — 0 byte read
         Step 8   parseMultipart          FIRST AND ONLY read of the request stream
                   ├─ undeclared fields skipped inline (not read, not stored)
                   ├─ text fields  → validated → Map<String, List<String>>
                   │    ├─ choice fields (select, radio, checkbox): value checked against
                   │    │   allowed choices declared in JCR
                   │    ├─ typed fields (email, date, datetime-local, color): format validated
                   │    └─ field constraints applied (required, minLength, maxLength, pattern,
                   │        minDate, maxDate)
                   └─ file parts   → List<FormFile>
                        ├─ size limits (per-file + total)
                        ├─ file count limit (config.getUploadMaxFileCount())
                        ├─ Tika magic-byte MIME detection
                        └─ MIME allowlist (field accept or global cfg)
         Step 9   validateRequired        post-parse: check required fields absent from the
                                          submitted body (e.g. unchecked checkbox/radio)
         Step 10  dispatchActions         execute fmdb:actionList nodes in order
                   ├─ fmdb:emailNotificationAction:
                   │    - subject + to normalized with FieldEscaper.headerSafe()
                   │    - HTML body uses FieldEscaper.html() for interpolated values
                   └─ fmdb:forwardAction:
                        - reads targetId from JCR node
                        - resolves URI via configService.resolveForwardTarget(targetId)
                          (targets defined in org.jahia.modules.formidable.cfg only)
                        - reconstructs multipart/form-data body with pre-parsed files
                        - POSTs to resolved URI

       └─ { "success": true } or { "success": false, "errorCode": "FMDB-XXX" }
  └─ show success or error message
```

> **DoS mitigation (defence in depth):**
- Gate 0: cross-origin requests are rejected before the multipart pipeline starts
> - Step 3: oversized requests are rejected early when a `Content-Length` header is present — 0 byte read
> - Step 6: invalid CAPTCHA token → rejected before any file data is read
> - Step 7: field whitelist built before parsing — undeclared fields never touch memory or disk
> - Steps 4–7 are all O(1) or cheap JCR lookups; the network stream is only read at step 8

`guardContentLength` is an optimization, not the definitive size limit. When a client submits
the request with `Transfer-Encoding: chunked`, `getContentLengthLong()` returns `-1`, so step 3
cannot reject the request before the body is read. In that case the authoritative request-size
enforcement still happens at step 8, where `ServletFileUpload.setSizeMax(...)` aborts oversized
multipart bodies during streaming.

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

## Trust model

The submission pipeline resolves the target form node with the current user's `live` JCR session, so normal
read permissions on the form still apply at form-resolution time. Once the form has been resolved and validated,
the configured actions execute under a system session. This is intentional: Guest and low-privilege users must
still be able to trigger server-side effects such as sending emails or saving submissions. The trust boundary is
therefore the form configuration itself: contributors who can configure a form and its action list are treated as
trusted actors, because the runtime will execute those configured actions with elevated JCR privileges.

---

## CSRF and cross-origin protection

The submit servlet is protected by two different mechanisms depending on whether the user is
anonymous or authenticated:

1. `formidable-submit` is a Jahia Security Filter scope auto-applied for `origin: hosted`.
   Requests that do not present a same-origin `Origin` or `Referer` never reach the multipart pipeline.
   This is the primary CSRF control for all submissions.
2. For authenticated users, Jahia CSRFGuard also injects and validates a `CSRFTOKEN`.
   Because the submit endpoint is `/modules/formidable-engine/form-submit` rather than `*.do`,
   the module ships a module-scoped CSRFGuard config extending `urlPatterns` to protect that
   servlet path explicitly.

### Protection matrix

| Form configuration | Protection that applies | Residual risk |
|---|---|---|
| Guest form without CAPTCHA | `formidable-submit` Security Filter only (`origin: hosted`) | Relies solely on the browser-supplied same-origin `Origin` / `Referer` signal enforced by the Security Filter |
| Guest form with `fmdbmix:captcha` | `formidable-submit` Security Filter + CAPTCHA token validation | Same residual risk as above if the origin signal is missing or downgraded, but the CAPTCHA token adds a second non-replayable credential tied to the hosting page |
| Authenticated form without CAPTCHA | `formidable-submit` Security Filter + Jahia CSRFGuard token | Requires both a same-origin request and a valid CSRF token; residual risk is lower and mainly depends on the correctness of those platform controls |
| Authenticated form with `fmdbmix:captcha` | `formidable-submit` Security Filter + Jahia CSRFGuard token + CAPTCHA token validation | Lowest residual risk in this flow; CAPTCHA is still defence in depth, not the primary CSRF control |

### Why guests do not use CSRFGuard as the primary control

Jahia-wide, guest traffic cannot rely on CSRFGuard tokens as the default CSRF mechanism because
guest pages are expected to remain CDN-cacheable. Injecting a per-request or per-session CSRF token
into cached HTML would break that caching model. For that reason, the guest path in Formidable
depends primarily on the Security Filter's `origin: hosted` check, while authenticated users still
benefit from CSRFGuard on top of the same-origin gate.

Forms using `fmdbmix:captcha` add another barrier: the CAPTCHA token is a non-replayable credential
tied to the hosting page. This is defence in depth, not the primary CSRF control.

---

## Field whitelist (step 7 → step 8)

`collectFormFieldInfo()` walks the `fields` child node (`fmdb:fieldList`) of the `fmdb:form`,
including fields nested inside `fmdb:step` children, and builds:

- `Set<String> allowedNames` — declared field names
- `Map<String, String> fieldTypes` — JCR primary node type per field
- `Map<String, Set<String>> allowedChoices` — for choice fields, the set of valid values
- `Map<String, Set<String>> fieldAcceptTypes` — for file fields, allowed MIME types
- `Map<String, FieldConstraints> fieldConstraints` — per-field constraints (required, minLength, maxLength, pattern, minDate, maxDate)

During `parseAll()`, any multipart item whose field name is absent from `allowedNames` is
**skipped without reading its content**. The Commons FileUpload iterator advances past
undeclared items automatically.

This prevents:
- Injection of arbitrary fields into forwarded data
- Exploitation of `${fieldName}` interpolation in email templates
- Storage or processing of data the form was not designed to collect

---

## Input validation (step 8)

`FormDataParser` validates every text field before it enters the pipeline:

| Field category | Control |
|---|---|
| Choice fields (`fmdb:select`, `fmdb:inputRadio`, `fmdb:inputCheckbox`) | Value checked against the `allowedChoices` set built from JCR; rejected if not in set |
| Typed fields (`email`, `date`, `datetime-local`, `color`) | Format validated with a strict regex |
| All text fields | `FieldConstraints` applied: required, minLength, maxLength, pattern, minDate, maxDate |

Required fields that are legitimately absent from the multipart body (e.g. unchecked
checkbox) are detected at step 9 (`validateRequired`) after parsing, rather than during
parsing.

Plain-text values are preserved as submitted. XSS protection is applied at each output sink
by escaping for the target context, not by mutating input during parsing.

---

## Output escaping (step 10 — email actions)

`FieldEscaper` centralises output escaping:

| Method | Context |
|---|---|
| `html(value)` | Escapes a value for safe insertion into HTML element content — used in HTML email body |
| `headerSafe(value)` | Strips `\r`, `\n`, `\t` and trims — applied to `to` and `subject` headers |
| `plainText(value)` | Null-safe passthrough — used in plain-text email body |

---

## File upload security (FormDataParser.parseAll)

All file parts pass through `FormDataParser` which enforces the following controls in order:

| # | Control | Implementation |
|---|---|---|
| 1 | Field whitelist | Undeclared fields skipped before any read |
| 2 | Per-file size limit | `upload.setFileSizeMax()` |
| 3 | Total request size limit | `upload.setSizeMax()` |
| 4 | File part count limit (CVE-2023-24998) | `upload.setFileCountMax(config.getUploadMaxFileCount())` — requires commons-fileupload ≥ 1.5 |
| 5 | Filename sanitisation | Filename normalized with Jahia's standard JCR node-name escaping rules; blank results fall back to `upload` |
| 6 | MIME type detection | Apache Tika magic-byte detection (ignores client-supplied `Content-Type`) |
| 7 | MIME type allowlist | Field-level `accept` property (multiple choicelist) takes priority; falls back to global cfg allowlist |

Limits and the global allowlist are configured in `org.jahia.modules.formidable.cfg` via `FormidableConfig`.

### Uploaded file lifecycle and temporary-file handling

`FormDataParser` does **not** create temporary files on disk for uploaded parts, and therefore
does not perform any explicit temp-file cleanup after processing.

More precisely:

- It uses the **streaming API** from Commons FileUpload: `ServletFileUpload#getItemIterator(req)`.
- For each file part, it opens the part stream and reads it into a `ByteArrayOutputStream`.
- It then stores the uploaded content in the `FormFile` record as `byte[] data`, not as a `File`
  handle or a disk-backed `FileItem`.

As a consequence:

- there is **no `DiskFileItemFactory`** in this flow
- there is **no spool-to-`/tmp` step** performed by `FormDataParser`
- there is **nothing to delete on disk** from `FormDataParser` itself

After parsing, the validated uploads remain in memory as `List<FormFile>` and are converted
to the SPI-level `List<SubmittedFile>` passed to each `FormAction`.

Actions that need uploaded files reuse that in-memory list:

- `SendEmailContentFormAction` reads the submitted files list and, when enabled, attaches the
  in-memory `byte[]` payloads to the outgoing email
- `ForwardSubmissionFormAction` reads the same submitted files list and rebuilds a new
  `multipart/form-data` request body in memory before POSTing it to the configured target

Those actions do not manipulate temporary files on disk either.

> **Operational note:** if temporary upload files are observed under the servlet container's
> temp directory, they are not created by `FormDataParser` itself. They would have to come from
> another layer such as the servlet container, an upstream filter, or a different multipart
> handling path.

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
| `fmdb:save2jcrAction` | Saves form data as JCR child nodes under `formidable-results` (see `docs/save-to-jcr.md`) |
| `fmdb:emailNotificationAction` | Sends a notification email via Jahia `MailService`; `${fieldName}` interpolation in subject and body; headers normalized and HTML body escaped |
| `fmdb:emailContentAction` | Sends the submitted form content by email; may optionally attach validated uploaded files |
| `fmdb:forwardAction` | Forwards declared form fields + pre-parsed files to a target endpoint resolved from config by ID |

### forwardAction — target registry

The target URL is never stored in JCR. The contributor picks a `targetId` from a choicelist
populated by `FormidableForwardTargetsInitializer`. The available targets are defined by an
administrator in `org.jahia.modules.formidable.cfg`:

```
forwardTargets=salesforce-prod|Salesforce Prod|https://api.salesforce.com/services/
crm-staging|CRM Staging|https://crm.internal/hook
```

Optional development-only targets can be enabled explicitly:

```
enableDevForwardTargets=true
devForwardTargets=local-api|Local API|http://localhost:3000/hook
docker-api|Docker API|http://host.docker.internal:8080/hook
```

`devForwardTargets` only accepts plain HTTP on `localhost` and `host.docker.internal`.

`FormidableConfigService.resolveForwardTarget(targetId)` returns the configured target entry or
throws if the ID is unknown. This design prevents SSRF: contributors can only reach
pre-approved endpoints.

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

---

## Key files

| File | Role |
|---|---|
| `src/components/Form/Form.client.tsx` | `handleSubmit` — removes CAPTCHA body field, appends `ct` URL param, POSTs via XHR |
| `src/components/Form/default.server.tsx` | Builds `submitActionUrl` with `fid` and `lang` query params |
| `formidable-engine/.../servlet/FormSubmitServlet.java` | OSGi entry point — checks `formidable-submit` permission, then delegates to `FormSubmissionPipeline` |
| `formidable-engine/.../servlet/FormSubmissionPipeline.java` | 10-step pipeline — all submission logic |
| `formidable-engine/.../servlet/ErrorCode.java` | Error code enum — see `docs/error-codes.md` |
| `formidable-engine/.../actions/FormDataParser.java` | Secure multipart parser: whitelist, input validation, Tika, allowlist, size + count limits |
| `formidable-engine/.../actions/FieldEscaper.java` | Output escaping utility: `html`, `headerSafe`, `plainText` |
| `formidable-engine/.../actions/forward/ForwardSubmissionFormAction.java` | Resolves `targetId` via `FormidableConfigService`; forwards declared fields only |
| `formidable-engine/.../actions/email/SendEmailNotificationFormAction.java` | Sends notification email; headers normalized with `headerSafe()`; HTML body escapes values with `html()` |
| `formidable-engine/.../actions/email/SendEmailContentFormAction.java` | Placeholder for sending submitted form content by email, optionally with attachments |
| `formidable-engine/.../actions/FormAction.java` | Interface implemented by each action type |
| `formidable-engine/.../config/FormidableConfigService.java` | Reads unified cfg; resolves forward targets by ID; verifies CAPTCHA
