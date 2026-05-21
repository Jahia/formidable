# Permissions — Test scenarios

Non-regression test scenarios for the results access control model.

Reference: `.harness/spec-formidable-permissions.md`

---

## Common setup

All tests share this baseline:

1. Create a user `test-reader` and add them to the `users` group (standard site reader)
2. Create a form with at least one field, publish it
3. Submit the form so `formResults` and at least one submission exist in live
4. Do NOT assign `fmdb-results-reader` to `test-reader` on the form

---

## 1. Site reader without `fmdb-results-reader` — no access

**Precondition:** common setup, no `fmdb-results-reader` assigned to `test-reader`.

### 1.1 GraphQL query returns no results

Execute `GET_FORM_RESULTS_LIST` as `test-reader`:

```graphql
query {
  jcr(workspace: LIVE) {
    nodeByPath(path: "/sites/<siteKey>/formidable-results") {
      children(typesFilter: {types: ["fmdb:formResults"]}) {
        nodes { uuid name }
      }
    }
  }
}
```

**Expected:** empty `nodes` array, or `PathNotFoundException` if the user cannot
even read `formidable-results`.

### 1.2 GraphQL submission query fails

Execute `GET_SUBMISSIONS` as `test-reader` using the `formResults` path directly:

```graphql
query {
  jcr(workspace: LIVE) {
    nodesByQuery(
      query: "SELECT * FROM [fmdb:formSubmission] AS s WHERE ISDESCENDANTNODE(s, '/sites/<siteKey>/formidable-results/<formName>/submissions')"
      queryLanguage: SQL2
      limit: 10
      offset: 0
    ) {
      nodes { uuid }
    }
  }
}
```

**Expected:** empty result or access denied error.

### 1.3 Dashboard shows no forms

Log in as `test-reader`, navigate to the formResults admin route.

**Expected:** the form list is empty. The "no forms" message is shown.

### 1.4 JCR Browser blocked

Access JCR Browser (Tools module) as `test-reader`, navigate to
`/sites/<siteKey>/formidable-results/<formName>`.

**Expected:** node is not visible or access is denied.

---

## 2. User with `fmdb-results-reader` — read access granted

**Precondition:** common setup, then:

1. In jContent, select the `fmdb:form` node → Permissions → LIVE ROLES
2. Assign `fmdb-results-reader` to `test-reader`
3. Publish the form

### 2.1 GraphQL query returns results

Execute `GET_FORM_RESULTS_LIST` as `test-reader`.

**Expected:** the `formResults` node appears in the `nodes` array.

### 2.2 GraphQL submission query returns data

Execute `GET_SUBMISSIONS` as `test-reader`.

**Expected:** submissions are returned with their `data` properties.

### 2.3 Dashboard shows the form and submissions

Log in as `test-reader`, navigate to the formResults admin route.

**Expected:** the form appears in the left panel. Selecting it shows the
submissions table with data.

---

## 3. User with `fmdb-results-reader` — deletion blocked

**Precondition:** same as test 2 (user has `fmdb-results-reader` only).

### 3.1 GraphQL delete mutation fails

Execute `DELETE_SUBMISSIONS` as `test-reader`:

```graphql
mutation {
  jcr(workspace: LIVE) {
    mutateNodesByQuery(
      query: "SELECT * FROM [fmdb:formSubmission] AS s WHERE ISDESCENDANTNODE(s, '/sites/<siteKey>/formidable-results/<formName>/submissions')"
      queryLanguage: SQL2
    ) {
      delete
    }
  }
}
```

**Expected:** error (access denied / insufficient permissions). No submissions
are deleted.

### 3.2 Delete button hidden in dashboard

Log in as `test-reader`, navigate to the formResults admin route, select the form.

**Expected:** the "Delete" button is NOT visible in the toolbar. Only "Export"
and "Refresh" are shown.

---

## 4. ACL propagation on publication

**Precondition:** common setup with `formResults` already existing.

### 4.1 Adding a role assignment propagates to formResults

1. Assign `fmdb-results-reader` to `test-reader` on the form (LIVE section)
2. Publish the form
3. Check the `j:acl` node under the `formResults` in live

**Expected:** a `jnt:ace` granting `fmdb-results-reader` to `test-reader`
exists under `formResults/j:acl`.

### 4.2 Removing a role assignment propagates to formResults

1. Remove `fmdb-results-reader` from `test-reader` on the form
2. Publish the form
3. Check the `j:acl` node under the `formResults` in live

**Expected:** the ACE for `test-reader` no longer exists under
`formResults/j:acl`. `test-reader` can no longer access results.

---

## 5. ACL sync at formResults creation

**Precondition:**

1. Create a form, assign `fmdb-results-reader` to `test-reader`, publish
2. No submissions yet (no `formResults` exists)

### 5.1 First submission creates formResults with correct ACL

1. Submit the form

**Expected:**
- `formResults` is created in live
- `formResults/j:acl` has `j:inherit = false`
- A `jnt:ace` granting `fmdb-results-reader` to `test-reader` exists
- `test-reader` can immediately see the form in the dashboard

---

## 6. Inheritance repair

### 6.1 Sync restores broken inheritance if accidentally fixed

**Precondition:** common setup with `formResults` existing.

1. Via Groovy console, set `j:inherit = true` on the `formResults` node's `j:acl`
2. Trigger an ACL sync (publish the form)

**Expected:**
- `j:inherit` is reset to `false` after sync
- A user without `fmdb-results-reader` still cannot access results

---

## 7. Role existence

### 7.1 Role is created at module startup

After deploying the module, check via Groovy console:

```groovy
def session = org.jahia.services.content.JCRSessionFactory.getInstance()
    .getCurrentSystemSession("default", null, null)
println session.nodeExists("/roles/reader/fmdb-results-reader")
```

**Expected:** `true`

### 7.2 Role appears in jContent permissions UI

Select a `fmdb:form` node → Permissions → LIVE ROLES.

**Expected:** `fmdb-results-reader` appears in the list of available roles.

