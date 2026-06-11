# Security — Test scenarios

Non-regression scenarios for the submission security model around
`FormSubmitServlet` and `FormSubmissionPipeline`.

This document covers two things:
- fail-closed behavior inside the pipeline
- direct HTTP attacks against `/modules/formidable-engine/form-submit`

Reference implementation:
- `formidable-engine/.../servlet/FormSubmitServlet.java`
- `formidable-engine/.../servlet/FormSubmissionPipeline.java`

---

## Common setup

1. Create a site with the Formidable module deployed.
2. Create and publish a form with at least one text field.
3. Keep at least one public form with no auth and no CAPTCHA as the baseline target.
4. For scenarios that inspect stored data, attach a `saveToJcr` action.

---

## 1. Submit servlet gate — direct HTTP access

### Scenario

Before the multipart pipeline runs, the servlet is protected by the Jahia
Security Filter scope `formidable-submit`.

Requests that do not match the hosted same-origin policy must be rejected
before the pipeline is reached.

### 1.1 Missing same-origin signal is rejected

**Steps:**
1. Submit directly to `/modules/formidable-engine/form-submit?fid=<fid>&lang=en`
   without an acceptable `Origin` or `Referer`.

**Expected:**
- HTTP 403
- JSON body contains `FMDB-011`
- No submission is stored

### 1.2 Foreign origin is rejected

**Steps:**
1. Submit directly with a foreign `Origin`, for example `https://evil.example`.

**Expected:**
- HTTP 403
- JSON body contains `FMDB-011`
- No submission is stored

### 1.3 Same-origin request reaches the pipeline

**Steps:**
1. Submit directly with a same-origin `Origin` or `Referer`.

**Expected:**
- The request passes the Security Filter gate
- The response is no longer `FMDB-011`
- The request then succeeds or fails according to the downstream pipeline rules

---

## 2. Early request guards — direct HTTP access

### 2.1 Non-multipart request is rejected

**Steps:**
1. POST to the submit servlet with a non-multipart content type.

**Expected:**
- HTTP 415
- JSON body contains `FMDB-001`

### 2.2 Missing `fid` is rejected

**Steps:**
1. POST multipart data without the `fid` query parameter.

**Expected:**
- HTTP 400
- JSON body contains `FMDB-002`

### 2.3 Invalid `fid` is rejected

**Steps:**
1. POST multipart data with `fid=not-a-uuid`.

**Expected:**
- HTTP 400
- JSON body contains `FMDB-002`

### 2.4 Unknown or unpublished form is rejected

**Steps:**
1. POST multipart data with a valid-looking UUID that does not resolve in `live`.

**Expected:**
- HTTP 400
- JSON body contains `FMDB-004`

### 2.5 Oversized request is rejected early when `Content-Length` is present

**Steps:**
1. POST multipart data whose `Content-Length` exceeds `uploadMaxRequestSizeBytes`.

**Expected:**
- HTTP 413
- JSON body contains `FMDB-003`

---

## 3. Authentication gate — fail-closed

### Scenario

Authors apply `fmdbmix:requireAuthentication` on the form, but the runtime
pipeline reads the engine-owned semantic mixin
`fmdbmix:authenticatedOnlyForm`.

If the JCR type check throws, the submission must be rejected.

### 3.1 Authenticated form rejects Guest

**Precondition:**
1. Add the `fmdbmix:requireAuthentication` mixin to the form.
2. Publish the form.

**Steps:**
1. Log out.
2. Submit the form.

**Expected:**
- HTTP 401
- JSON body contains `FMDB-009`
- No submission is stored

### 3.2 Authenticated form accepts a logged-in user

**Precondition:** same as 3.1

**Steps:**
1. Log in as a non-guest user.
2. Submit the form.

**Expected:**
- HTTP 200 with `success: true`
- Submission is stored

### 3.3 Repository error rejects the submission

**Note:** this requires a unit test or a repository fault-injection setup.

**Precondition:**
1. The form carries the semantic auth requirement.
2. `isNodeType("fmdbmix:authenticatedOnlyForm")` throws `RepositoryException`.

**Expected:**
- HTTP 500
- JSON body contains `FMDB-500`
- No submission is stored

### 3.4 Authenticated user without CSRF token is rejected

**Precondition:**
1. The form carries `fmdbmix:requireAuthentication`.
2. The submit servlet path is covered by the module-scoped CSRFGuard config.
3. Log in as a non-guest user.

**Steps:**
1. Submit directly without the CSRF token expected by Jahia CSRFGuard.

**Expected:**
- The request is rejected before normal submission completion
- No submission is stored

### 3.5 Authenticated user with a valid CSRF token is accepted

**Precondition:** same as 3.4

**Steps:**
1. Load a page as the authenticated user so Jahia CSRFGuard issues a valid token.
2. Submit the form with that token using the transport expected by Jahia CSRFGuard.

**Expected:**
- HTTP 200 with `success: true`
- Submission is stored

---

## 4. CAPTCHA gate — fail-closed

### Scenario

Authors apply `fmdbmix:captcha` on the form, but the runtime pipeline reads the
engine-owned semantic mixin `fmdbmix:captchaProtectedForm`.

The browser removes the provider field from `FormData` and sends the token in
the `X-Formidable-Captcha-Token` header. If the semantic mixin lookup fails,
the submission must be rejected.

### 4.1 CAPTCHA form rejects a missing token header

**Precondition:**
1. Add the `fmdbmix:captcha` mixin to the form.
2. Configure a valid CAPTCHA provider server-side.
3. Publish the form.

**Steps:**
1. Submit the form without the `X-Formidable-Captcha-Token` header.

**Expected:**
- HTTP 400
- JSON body contains `FMDB-006`
- No submission is stored

### 4.2 CAPTCHA form rejects when server-side verification is not configured

**Precondition:**
1. Add the `fmdbmix:captcha` mixin to the form.
2. Do not configure CAPTCHA verification server-side.
3. Publish the form.

**Steps:**
1. Submit the form.

**Expected:**
- HTTP 500
- JSON body contains `FMDB-005`
- No submission is stored

### 4.3 Provider technical failure is rejected

**Precondition:**
1. Add the `fmdbmix:captcha` mixin to the form.
2. Configure CAPTCHA verification.
3. Force `verifyCaptcha(...)` to fail technically, for example timeout or DNS failure.

**Expected:**
- HTTP 500
- JSON body contains `FMDB-500`
- No submission is stored

### 4.4 Repository error rejects the submission

**Note:** this requires a unit test or a repository fault-injection setup.

**Precondition:**
1. The form carries the semantic CAPTCHA requirement.
2. `isNodeType("fmdbmix:captchaProtectedForm")` throws `RepositoryException`.

**Expected:**
- HTTP 500
- JSON body contains `FMDB-500`
- No submission is stored

---

## 5. Combined gates

### 5.1 Form with auth and CAPTCHA enforces both gates

**Precondition:**
1. Add both `fmdbmix:requireAuthentication` and `fmdbmix:captcha` to the form.
2. Configure CAPTCHA server-side.
3. Publish the form.

**Steps:**
1. Submit as Guest without the CAPTCHA header.

**Expected:**
- HTTP 401 with `FMDB-009`
- Auth check runs first and rejects before CAPTCHA is evaluated

**Variant:**
1. Log in as a valid user.
2. Submit without the CAPTCHA header.

**Expected:**
- HTTP 400 with `FMDB-006`

---

## 6. Submission tampering — direct HTTP access

### Scenario

The server collects the declared field metadata before parsing and must ignore
undeclared user-controlled multipart fields.

### 6.1 Undeclared text field is ignored

**Precondition:**
1. Create a form with one declared text field.
2. Attach `saveToJcr`.

**Steps:**
1. Submit the declared field.
2. Add an extra undeclared field in the multipart body, for example `role=admin`.

**Expected:**
- The submission may still succeed
- The undeclared field is not stored
- The undeclared field is not forwarded to actions

### 6.2 Undeclared file field is ignored

**Precondition:** same as 6.1, but with no declared file field.

**Steps:**
1. Submit a multipart request that contains an undeclared file part.

**Expected:**
- The undeclared file part is ignored by the pipeline
- No file metadata or binary is stored for that undeclared field

### 6.3 Disguised file content is rejected by server-side MIME detection

**Precondition:**
1. Create a file field restricted to `.txt`.

**Steps:**
1. Submit a file whose name ends with `.txt` but whose content is actually another type.

**Expected:**
- HTTP 400
- JSON body contains `FMDB-010`

---

## 7. Forward target validation

### Scenario

Forward targets are configured server-side in `org.jahia.modules.formidable.cfg`.
Contributors only store a stable `targetId` in JCR; they do not control the
final URL.

### 7.1 Standard HTTPS target is accepted

**Precondition:**
1. Configure `forwardTargets=crm|CRM|https://api.example.com/forms/intake`.
2. Restart or reload the module configuration.

**Expected:**
- The `CRM` entry is selectable
- Selecting it stores the stable value `crm`

### 7.2 Target with embedded credentials is rejected

**Precondition:**
1. Configure `forwardTargets=bad-creds|Bad creds|https://user:pass@api.example.com/forms/intake`.
2. Restart or reload the module configuration.

**Expected:**
- The `Bad creds` entry is absent
- The target cannot be resolved from `targetId`

### 7.3 Development targets stay constrained to explicit local hosts

**Precondition:**
1. Enable `enableDevForwardTargets=true`.
2. Configure:
   - `devForwardTargets=local-ok|Local OK|http://localhost:8081/ingest`
   - `devForwardTargets=bad-host|Bad host|http://example.com/ingest`

**Expected:**
- `Local OK` is present
- `Bad host` is absent

---

## 8. Outbound dependency timeouts

### Scenario

Outbound verification and forwarding calls must fail within a bounded time
budget instead of hanging servlet threads indefinitely.

### 8.1 CAPTCHA verification slow provider fails within timeout budget

**Precondition:**
1. Add `fmdbmix:captcha` to the form.
2. Configure a valid-looking `captchaVerifyUrl` that accepts the request but
   does not respond within the application timeout budget.
3. Publish the form.

**Steps:**
1. Submit the form with a non-empty `X-Formidable-Captcha-Token` header.
2. Measure total request duration.

**Expected:**
- The request fails instead of hanging indefinitely
- Total duration stays bounded by the configured connect and request timeouts

### 8.2 CAPTCHA verification unreachable provider fails within timeout budget

**Precondition:**
1. Add `fmdbmix:captcha` to the form.
2. Configure `captchaVerifyUrl` to an unreachable host or port.
3. Publish the form.

**Steps:**
1. Submit the form with a non-empty `X-Formidable-Captcha-Token` header.
2. Measure total request duration.

**Expected:**
- The request fails instead of hanging indefinitely
- Total duration stays bounded by the configured connect timeout

### 8.3 Forward action slow upstream target fails with bounded latency

**Precondition:**
1. Configure a forward target that accepts the connection but does not return a
   response within the application timeout budget.
2. Attach an `fmdb:forwardAction` using that target to a published form.

**Steps:**
1. Submit the form.
2. Measure total request duration.

**Expected:**
- The request fails instead of hanging indefinitely
- HTTP response reflects the downstream action failure
- Total duration stays bounded by the configured connect and request timeouts

### 8.4 Forward action unreachable upstream target fails with bounded latency

**Precondition:**
1. Configure a forward target that points to an unreachable host or port.
2. Attach an `fmdb:forwardAction` using that target to a published form.

**Steps:**
1. Submit the form.
2. Measure total request duration.

**Expected:**
- The request fails instead of hanging indefinitely
- Total duration stays bounded by the configured connect timeout

### 8.5 Forward action DNS resolution timeout fails with bounded latency

**Precondition:**
1. Configure a forward target hostname whose DNS resolution stalls beyond the
   configured DNS timeout.
2. Attach an `fmdb:forwardAction` using that target to a published form.

**Steps:**
1. Submit the form.
2. Measure total request duration.

**Expected:**
- The request fails instead of hanging indefinitely during hostname resolution
- Total duration stays bounded by the DNS timeout budget
