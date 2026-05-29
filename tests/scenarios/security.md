# Security — Test scenarios

Non-regression test scenarios for the fail-closed security gates
in `FormSubmissionPipeline`.

---

## Common setup

1. Create a site with the Formidable module deployed
2. Create a form with at least one text field, publish it
3. Verify that a normal submission (no auth, no captcha) succeeds — baseline

---

## 1. Authentication gate — fail-closed

### Scenario

The `verifyAuthentication` step checks `formNode.isNodeType("fmdbmix:requireAuthentication")`.
If this JCR call throws a `RepositoryException`, the submission must be **rejected** (fail-closed),
not silently allowed through.

### 1.1 Normal behavior — authenticated form rejects anonymous user

**Precondition:**
1. Add the `fmdbmix:requireAuthentication` mixin to the form
2. Publish the form

**Steps:**
1. Log out (become Guest)
2. Submit the form via POST

**Expected:**
- HTTP 401 with error code `FMDB-009`
- No submission is stored

### 1.2 Normal behavior — authenticated form accepts logged-in user

**Precondition:** same as 1.1

**Steps:**
1. Log in as a non-guest user
2. Submit the form via POST

**Expected:**
- HTTP 200 with `success: true`
- Submission is stored

### 1.3 Repository error — submission is rejected (fail-closed)

> **Note:** this scenario requires simulating a JCR error. It cannot be tested
> via Cypress alone — use a unit test or a Groovy script that patches the
> session to throw on `isNodeType()`.

**Precondition:**
1. Form has `fmdbmix:requireAuthentication`
2. The JCR session is degraded so that `isNodeType()` throws `RepositoryException`

**Steps:**
1. Submit the form

**Expected:**
- HTTP 500 with error code `FMDB-500`
- Server log contains an `ERROR` entry containing:
  `[FormSubmissionPipeline] Could not check fmdbmix:requireAuthentication on form '<fid>' — rejecting submission (fail-closed)`
- No submission is stored

> **Note:** scenarios 1.4 and 1.5 are deferred documentation only at this stage.
> They are not automated as Cypress tests yet and must not be counted as runtime CSRF coverage.

### 1.4 CSRFGuard — authenticated form rejects missing token

**Precondition:**
1. Add the `fmdbmix:requireAuthentication` mixin to the form
2. Publish the form
3. Log in as a non-guest user
4. Ensure the submit endpoint `/modules/formidable-engine/form-submit` is covered by
   `org.jahia.modules.jahiacsrfguard-formidable-engine.cfg`

**Steps:**
1. Submit the form to `/modules/formidable-engine/form-submit?fid=<fid>&lang=en`
2. Do not send any CSRF token parameter or header expected by Jahia CSRFGuard

**Expected:**
- The request is rejected before the submission pipeline completes
- HTTP response indicates CSRF rejection for an authenticated user
- No submission is stored

### 1.5 CSRFGuard — authenticated form accepts valid token

**Precondition:** same as 1.4

**Steps:**
1. Load a page as the authenticated user so Jahia CSRFGuard issues a valid token
2. Submit the form to `/modules/formidable-engine/form-submit?fid=<fid>&lang=en`
3. Send the valid CSRF token with the request, using the transport expected by Jahia CSRFGuard

**Expected:**
- The request passes the CSRFGuard check
- HTTP 200 with `success: true`
- The submission pipeline runs normally
- Submission is stored

---

## 2. CAPTCHA gate — fail-closed

### Scenario

The `verifyCaptcha` step checks `formNode.isNodeType("fmdbmix:captcha")`.
If this JCR call throws, the submission must be **rejected**.

### 2.1 Normal behavior — captcha form rejects missing token

**Precondition:**
1. Add the `fmdbmix:captcha` mixin to the form
2. Configure a valid CAPTCHA provider server-side
3. Publish the form

**Steps:**
1. Submit the form without a `ct` parameter

**Expected:**
- HTTP 400 with error code `FMDB-006`
- No submission is stored

### 2.2 Normal behavior — captcha not configured rejects submission

**Precondition:**
1. Add the `fmdbmix:captcha` mixin to the form
2. Do NOT configure CAPTCHA server-side
3. Publish

**Steps:**
1. Submit the form

**Expected:**
- HTTP 500 with error code `FMDB-005`
- No submission is stored

### 2.3 Repository error — submission is rejected (fail-closed)

> **Note:** same as 1.3, requires simulating a JCR error.

**Precondition:**
1. Form has `fmdbmix:captcha`
2. The JCR session is degraded so that `isNodeType()` throws `RepositoryException`

**Steps:**
1. Submit the form

**Expected:**
- HTTP 500 with error code `FMDB-500`
- Server log contains an `ERROR` entry containing:
  `[FormSubmissionPipeline] Could not check fmdbmix:captcha on form '<fid>' — rejecting submission (fail-closed)`
- No submission is stored

---

## 3. Combined gates

### 3.1 Form with both auth + captcha — both gates enforced

**Precondition:**
1. Add both `fmdbmix:requireAuthentication` and `fmdbmix:captcha` to the form
2. Configure CAPTCHA server-side
3. Publish

**Steps:**
1. Submit as Guest without captcha token

**Expected:**
- HTTP 401 with `FMDB-009` (auth check runs first, rejects before captcha is even evaluated)
**Steps (variant):**
1. Log in as a valid user
2. Submit without captcha token

**Expected:**
- HTTP 400 with `FMDB-006` (auth passes, captcha rejects)

---

## 4. Forward action target validation

### Scenario

Forward targets are configured server-side in `org.jahia.modules.formidable.cfg`.
Contributors only store a stable `targetId` in JCR; they do not control the final URL.
Configuration parsing must reject malformed or unsafe target URIs before they become selectable.

### 4.1 Standard HTTPS target is accepted

**Precondition:**
1. Configure `forwardTargets=crm|CRM|https://api.example.com/forms/intake`
2. Restart or reload the module configuration

**Steps:**
1. Open the editor for an `fmdb:forwardAction`
2. Inspect the `targetId` choice list

**Expected:**
- The `CRM` entry is present
- Selecting it stores the stable value `crm`

### 4.2 Target with embedded credentials is rejected

**Precondition:**
1. Configure
   `forwardTargets=bad-creds|Bad creds|https://user:pass@api.example.com/forms/intake`
2. Restart or reload the module configuration

**Steps:**
1. Open the editor for an `fmdb:forwardAction`
2. Inspect the `targetId` choice list
3. If possible, inspect server logs during activation

**Expected:**
- The `Bad creds` entry is absent from the choice list
- The target cannot be resolved from its `targetId`
- Server logs show the entry was skipped as invalid configuration

### 4.3 Development targets stay constrained to explicit local hosts

**Precondition:**
1. Enable `enableDevForwardTargets=true`
2. Configure:
   - `devForwardTargets=local-ok|Local OK|http://localhost:8081/ingest`
   - `devForwardTargets=bad-host|Bad host|http://example.com/ingest`
3. Restart or reload the module configuration

**Steps:**
1. Open the editor for an `fmdb:forwardAction`
2. Inspect the `targetId` choice list

**Expected:**
- `Local OK` is present
- `Bad host` is absent

---

## 5. Outbound dependency timeouts

### Scenario

Outbound network calls performed during submission must fail within a bounded time budget.
An unreachable CAPTCHA provider or a slow forward target must not leave the servlet thread
waiting indefinitely.

### 5.1 CAPTCHA verification — slow provider fails closed

**Precondition:**
1. Add the `fmdbmix:captcha` mixin to the form
2. Configure a valid-looking `captchaVerifyUrl` that accepts the request but does not respond
   within the application timeout budget
3. Publish the form

**Steps:**
1. Submit the form with a non-empty `ct` token
2. Measure total request duration

**Expected:**
- The submission fails instead of hanging indefinitely
- HTTP 400 with error code `FMDB-006`
- Server logs contain an error for the failed CAPTCHA verification request
- End-to-end duration stays bounded by the configured connect/request timeouts, with normal
  network overhead

### 5.2 CAPTCHA verification — unreachable provider fails closed

**Precondition:**
1. Add the `fmdbmix:captcha` mixin to the form
2. Configure `captchaVerifyUrl` to an unreachable host or port
3. Publish the form

**Steps:**
1. Submit the form with a non-empty `ct` token
2. Measure total request duration

**Expected:**
- The submission fails instead of hanging indefinitely
- HTTP 400 with error code `FMDB-006`
- Server logs contain an error for the failed CAPTCHA verification request
- End-to-end duration stays bounded by the configured connect timeout, with normal
  network overhead

### 5.3 Forward action — slow upstream target fails with bounded latency

**Precondition:**
1. Configure a valid forward target URL that accepts the connection but does not return a
   response within the application timeout budget
2. Attach an `fmdb:forwardAction` using that target to a published form

**Steps:**
1. Submit the form
2. Measure total request duration

**Expected:**
- The submission fails instead of hanging indefinitely
- HTTP 502
- Server logs contain an error for the failed forward request
- End-to-end duration stays bounded by the configured connect/request timeouts, with normal
  network overhead

### 5.4 Forward action — unreachable upstream target fails with bounded latency

**Precondition:**
1. Configure a valid forward target URL pointing to an unreachable host or port
2. Attach an `fmdb:forwardAction` using that target to a published form

**Steps:**
1. Submit the form
2. Measure total request duration

**Expected:**
- The submission fails instead of hanging indefinitely
- HTTP 502
- Server logs contain an error for the failed forward request
- End-to-end duration stays bounded by the configured connect timeout, with normal
  network overhead

### 5.5 Forward action — DNS resolution timeout fails with bounded latency

**Precondition:**
1. Configure a valid forward target hostname whose DNS resolution stalls beyond the
   application timeout budget
2. Attach an `fmdb:forwardAction` using that target to a published form

**Steps:**
1. Submit the form
2. Measure total request duration

**Expected:**
- The submission fails instead of hanging indefinitely during hostname resolution
- HTTP 502
- Server logs contain a warning or error for timed-out hostname resolution
- End-to-end duration stays bounded by the dedicated DNS resolution timeout, with normal
  network overhead
