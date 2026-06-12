# Formidable Error Codes

When a form submission fails, the servlet returns a JSON body with an opaque error code:

```json
{ "success": false, "errorCode": "FMDB-006" }
```

When an action in the pipeline fails (`FMDB-008`), the response also includes action progress:

```json
{ "success": false, "errorCode": "FMDB-008", "actionsCompleted": 1, "actionsTotal": 3 }
```

- `actionsCompleted` — number of actions that finished successfully before the failure
- `actionsTotal` — total number of actions in the pipeline

Detailed reasons are written to server logs only and are never exposed to the caller.

## Glossary

| Code | HTTP status | Condition |
|---|---|---|
| `FMDB-001` | 415 | `Content-Type` is not `multipart/form-data` |
| `FMDB-002` | 400 | Required URL parameter `fid` is missing, blank, or not a valid UUID |
| `FMDB-003` | 413 | `Content-Length` exceeds `uploadMaxRequestSizeBytes` from `org.jahia.modules.formidable.cfg` |
| `FMDB-004` | 400 | Form node not found in the `live` workspace (invalid `fid`, or form not published) |
| `FMDB-005` | 500 | CAPTCHA is required on the form but server-side verification is not fully configured (`captchaSiteKey` / `captchaSecretKey` / `captchaVerifyUrl` missing in `org.jahia.modules.formidable.cfg`) |
| `FMDB-006` | 400 | CAPTCHA token (`X-Formidable-Captcha-Token` header) is absent, expired, or rejected by the provider |
| `FMDB-007` | 400 | Multipart parsing failed for a technical reason — possible causes: per-file size limit, total request size limit, file count limit, or low-level stream parsing/read failure |
| `FMDB-008` | 422 | An action in the pipeline failed (e.g. forward target returned non-2xx, email could not be sent) |
| `FMDB-009` | 401 | Authentication required — the form carries `fmdbmix:authenticatedOnlyForm` and the current user is Guest |
| `FMDB-010` | 400 | Submitted data failed validation — possible causes: choice value not in the allowed set, invalid text format (email, date, datetime-local, color), field constraint violation, or uploaded file MIME type rejected by the field/global allowlist |
| `FMDB-011` | 403 | Submission denied by the Jahia Security Filter check (for example, cross-origin request with no matching hosted `Origin` / `Referer`) |
| `FMDB-012` | 500 | Action list resolution failed — the pipeline could not read the configured `actions` node from the repository |
| `FMDB-013` | 403 | Authenticated submission failed the CSRF gate — the request did not carry a valid CSRF token header, or the submitted token did not match the CSRF cookie |
| `FMDB-500` | 500 | Unexpected internal, configuration, or provider/infrastructure verification error — check server logs (for example, invalid server-side validation metadata such as a malformed regex constraint, or a technical CAPTCHA verification failure) |

## Source

Error codes are defined in `formidable-engine/.../servlet/ErrorCode.java`.
