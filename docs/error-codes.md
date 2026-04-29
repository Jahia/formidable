# Formidable Error Codes

When a form submission fails, the servlet returns a JSON body with an opaque error code:

```json
{ "success": false, "errorCode": "FMDB-006" }
```

Detailed reasons are written to server logs only and are never exposed to the caller.

## Glossary

| Code | HTTP status | Condition |
|---|---|---|
| `FMDB-001` | 415 | `Content-Type` is not `multipart/form-data` |
| `FMDB-002` | 400 | Required URL parameter `fid` is missing, blank, or not a valid UUID |
| `FMDB-003` | 413 | `Content-Length` exceeds `upload.maxRequestSizeBytes` from config |
| `FMDB-004` | 400 | Form node not found in the `live` workspace (invalid `fid`, or form not published) |
| `FMDB-005` | 500 | CAPTCHA mixin is present on the form but `captcha.siteKey` / `captcha.secretKey` are not set in `org.jahia.modules.formidable.cfg` |
| `FMDB-006` | 400 | CAPTCHA token (`ct` URL param) is absent, expired, or rejected by the provider |
| `FMDB-007` | 400 | Multipart parsing failed — possible causes: per-file size limit, total size limit, file count limit, or MIME type not in allowlist |
| `FMDB-008` | 422 | An action in the pipeline failed (e.g. forward target returned non-2xx, email could not be sent) |
| `FMDB-009` | 401 | Authentication required — `fmdbmix:requireAuthentication` is applied on the form and the current user is Guest |
| `FMDB-010` | 400 | Field value validation failed — submitted value is not an allowed choice, or fails format validation (email, date, datetime-local, color) |
| `FMDB-500` | 500 | Unexpected internal error — check server logs |

## Source

Error codes are defined in `formidable-engine/.../servlet/ErrorCode.java`.

