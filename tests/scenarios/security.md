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
- Server log contains an `ERROR` entry with message:
  `Could not check fmdbmix:requireAuthentication on form '<uuid>' — rejecting submission (fail-closed)`
- No submission is stored

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
- Server log contains an `ERROR` entry with message:
  `Could not check fmdbmix:captcha on form '<uuid>' — rejecting submission (fail-closed)`
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
- HTTP 403 with `FMDB-009` (auth check runs first, rejects before captcha is even evaluated)

**Steps (variant):**
1. Log in as a valid user
2. Submit without captcha token

**Expected:**
- HTTP 400 with `FMDB-006` (auth passes, captcha rejects)

