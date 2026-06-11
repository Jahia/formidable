# Conditional Logic – Test Scenarios

## Setup (shared across all scenarios)

### Site & form creation

1. Create a test site with the JSP or JS template set.
2. Enable `formidable-engine` and `formidable-elements` on the site.
3. Create a form (`fmdb:form`) with a `saveToJcr` action.
4. Inside the form, create four fields in order:
   - **role** — `fmdb:select` with choices: `admin`, `editor`, `viewer`
   - **accept-terms** — `fmdb:checkbox` (single) with choice: `accepted` / `I accept`
   - **start-date** — `fmdb:inputDate`
   - **nickname** — `fmdb:inputText` (required)

This form is the baseline for most scenarios below. Specific scenarios may add or modify fields.

---

## 1. Backend – Weakref structure synchronisation

These tests verify that the `logicsSrc` child structure is correctly created, updated, and cleaned up by `FormLogicSyncListener` when a contributor edits conditional logic rules via the Content Editor.

### 1.1 Logic creation creates weakref nodes

**Given** `nickname` has no `logics` property.

**When** a logic rule is added to `nickname`:
```json
{"logicId":"aaa11111","sourceFieldName":"role","sourceFieldType":"fmdb:select","operator":"in","values":["admin"]}
```

**Then:**
- `nickname` has a `logics` string[] property containing the JSON above.
- `nickname` has a child node `logicsSrc` of type `fmdb:logicList`.
- `logicsSrc` has a child node `aaa11111` of type `fmdb:logicSrc`.
- `aaa11111` has a `logicNodeSource` weakreference pointing to the `role` node.
- The `role` node UUID matches the weakref target UUID.

### 1.2 Adding a second rule creates a second weakref

**Given** `nickname` already has the rule from 1.1.

**When** a second rule is added:
```json
{"logicId":"bbb22222","sourceFieldName":"accept-terms","sourceFieldType":"fmdb:checkbox","operator":"isChecked"}
```

**Then:**
- `logics` contains two JSON entries.
- `logicsSrc` has two children: `aaa11111` and `bbb22222`.
- `bbb22222/logicNodeSource` points to the `accept-terms` node.

### 1.3 Adding a third rule (date source)

**When** a third rule is added:
```json
{"logicId":"ccc33333","sourceFieldName":"start-date","sourceFieldType":"fmdb:inputDate","operator":"after","value":"2025-01-01"}
```

**Then:**
- `logics` contains three JSON entries.
- `logicsSrc` has three children: `aaa11111`, `bbb22222`, `ccc33333`.
- `ccc33333/logicNodeSource` points to the `start-date` node.

### 1.4 Removing a rule removes its weakref

**When** the second rule (`bbb22222`) is removed from `logics`.

**Then:**
- `logics` contains two entries (rules `aaa11111` and `ccc33333`).
- `logicsSrc` has two children: `aaa11111` and `ccc33333`.
- `bbb22222` no longer exists under `logicsSrc`.

### 1.5 Removing all rules removes logicsSrc children

**When** all rules are removed from `logics` (property cleared or removed).

**Then:**
- `logicsSrc` has no children, or does not exist.

### 1.6 Changing the source field updates the weakref

**Given** `nickname` has a rule pointing to `role`.

**When** the rule's `sourceFieldName` is changed to `accept-terms`.

**Then:**
- The weakref node under `logicsSrc` now points to the `accept-terms` node.

### 1.7 logicsSrc is only created when needed

**Given** a new `fmdb:inputText` field is created with no logic.

**Then:**
- The field does NOT have a `logicsSrc` child node.

---

## 2. Backend – Rename resilience

These tests verify that renaming a source field does not break existing logic rules.

### 2.1 Renamed source field still resolves via weakref

**Given** `nickname` has a logic rule pointing to `role` via `logicId` `aaa11111`.

**When** the `role` node is renamed to `user-role` (in JCR).

**Then:**
- `logicsSrc/aaa11111/logicNodeSource` still points to the same node (by UUID).
- The weakref target node's name is now `user-role`.
- The `sourceFieldName` in the JSON may still say `role` (stale), but the weakref is authoritative.

### 2.2 Content Editor displays the current name after rename

**Given** `role` was renamed to `user-role` as in 2.1.

**When** the Content Editor is opened for `nickname`'s logic tab.

**Then:**
- The source field dropdown shows `user-role` (the current name), not `role`.

---

## 3. Backend – Form duplication (copy/paste)

### 3.1 Copying a form preserves internal logic

**Given** a form with `nickname` → logic on `role`.

**When** the entire form is copied (paste in jContent).

**Then** in the copied form:
- `nickname` has a `logicsSrc` child.
- The weakref `logicNodeSource` on the copied `logicsSrc` child points to the **copied** `role` node (inside the copied form), not to the original one.

### 3.2 Copying a form and modifying the source field

**Given** a form was copied as in 3.1.

**When** the `role` select in the **copied** form has a new choice added (`super-admin`).

**Then:**
- Opening the Content Editor for the copied `nickname` → logic tab shows the source field dropdown with `role` selected.
- The value dropdown includes `super-admin` (the new choice).

### 3.3 Copying a single element between forms

**Given** form A has `nickname` with a logic rule pointing to `role`.

**When** `nickname` is copied from form A into form B (which does not have a `role` field).

**Then:**
- The weakref in the copied `nickname` points outside form B's subtree.
- The duplication cleanup listener removes the out-of-scope weakref.
- The orphaned logics JSON entry is also removed.

---

## 4. Backend – Import/Export

### 4.1 Import creates logicsSrc nodes

**Given** a form with logic rules is exported.

**When** the export is imported into a different site.

**Then:**
- Each logic element with rules has a `logicsSrc` child.
- Each weakref points to the correct (imported) source field node.
- The UUIDs in `logicNodeSource` match the imported nodes (JCR reassigns UUIDs on import).

### 4.2 Import of a form without logic

**Given** a form with no conditional logic is exported.

**When** it is imported.

**Then:**
- No `logicsSrc` node is created on any element.
- No WARN logs related to `logicsSrc` appear.

---

## 5. Backend – Server-side submission with conditional logic

These tests use direct HTTP POST (curl) to bypass the UI and test the server-side conditional logic evaluator.

### Setup

Create a form with:
- **show-details** — `fmdb:radio` with choices: `yes`, `no`
- **firstname** — `fmdb:inputText` (required), with logic: visible when `show-details` = `yes`
- **lastname** — `fmdb:inputText` (required), with logic: visible when `show-details` = `yes`
- A `saveToJcr` action.

Publish the form. Note the form UUID (`fid`).

### 5.1 Hidden required fields do not block submission

```bash
curl -X POST "http://localhost:8080/modules/formidable-engine/form-submit?fid=FORM_UUID&lang=en" \
  -H "Content-Type: multipart/form-data" \
  -F "show-details=no"
```

**Expected:** `{"success": true}` — `firstname` and `lastname` are hidden (condition not met), so their required constraint is skipped.

### 5.2 Visible required fields block submission when absent

```bash
curl -X POST "http://localhost:8080/modules/formidable-engine/form-submit?fid=FORM_UUID&lang=en" \
  -H "Content-Type: multipart/form-data" \
  -F "show-details=yes"
```

**Expected:** `{"success": false, "errorCode": "FMDB-..."}` — `firstname` and `lastname` are visible (condition met) and required, but missing.

### 5.3 Visible required fields pass when present

```bash
curl -X POST "http://localhost:8080/modules/formidable-engine/form-submit?fid=FORM_UUID&lang=en" \
  -H "Content-Type: multipart/form-data" \
  -F "show-details=yes" \
  -F "firstname=Jean" \
  -F "lastname=Dupuis"
```

**Expected:** `{"success": true}` — all visible required fields are present.

### 5.4 Hidden field values are ignored

```bash
curl -X POST "http://localhost:8080/modules/formidable-engine/form-submit?fid=FORM_UUID&lang=en" \
  -H "Content-Type: multipart/form-data" \
  -F "show-details=no" \
  -F "firstname=Jean" \
  -F "lastname=Dupuis"
```

**Expected:** `{"success": true}` — the extra `firstname` and `lastname` values are submitted but should be ignored since the fields are hidden. If a `saveToJcr` action is present, the saved submission should NOT contain `firstname` or `lastname`.

### 5.5 Transitive visibility: hidden source hides dependent

Create:
- **level1** — `fmdb:radio` with choices: `show`, `hide`
- **level2** — `fmdb:select` with choices: `a`, `b`, `c`. Logic: visible when `level1` = `show`.
- **level3** — `fmdb:inputText` (required). Logic: visible when `level2` = `a`.

```bash
curl -X POST "http://localhost:8080/modules/formidable-engine/form-submit?fid=FORM_UUID&lang=en" \
  -H "Content-Type: multipart/form-data" \
  -F "level1=hide"
```

**Expected:** `{"success": true}` — `level2` is hidden because `level1` ≠ `show`. `level3` is transitively hidden because its source (`level2`) is hidden. The required constraint on `level3` is skipped.

### 5.6 Multiple rules (AND logic)

Create a field with two rules:
- visible when `role` in `admin`
- visible when `accept-terms` isChecked

```bash
# Only one condition met → hidden
curl -X POST ... -F "role=admin"
# Expected: field is hidden (accept-terms not checked)

# Both conditions met → visible
curl -X POST ... -F "role=admin" -F "accept-terms=accepted"
# Expected: field is visible
```

---

## 6. Operator-specific scenarios (server-side)

Each scenario submits directly via curl. The target field is required and has a single logic rule.

### 6.1 Select / Radio

| Operator | Source value | Rule values | Expected visibility |
|----------|-------------|-------------|---------------------|
| `in` | `admin` | `["admin","editor"]` | visible |
| `in` | `viewer` | `["admin","editor"]` | hidden |
| `notIn` | `viewer` | `["admin","editor"]` | visible |
| `notIn` | `admin` | `["admin","editor"]` | hidden |
| `in` | _(absent)_ | `["admin"]` | hidden |

### 6.2 Checkbox (single)

| Operator | Source value | Expected visibility |
|----------|-------------|---------------------|
| `isChecked` | `accepted` | visible |
| `isChecked` | _(absent)_ | hidden |
| `isUnchecked` | _(absent)_ | visible |
| `isUnchecked` | `accepted` | hidden |

### 6.3 Checkbox (group)

Source choices: `sports`, `music`, `reading`.

| Operator | Submitted values | Rule values | Expected visibility |
|----------|-----------------|-------------|---------------------|
| `containsAny` | `sports,music` | `["sports","reading"]` | visible |
| `containsAny` | `music` | `["sports","reading"]` | hidden |
| `containsAll` | `sports,reading` | `["sports","reading"]` | visible |
| `containsAll` | `sports` | `["sports","reading"]` | hidden |

### 6.4 Date

| Operator | Submitted date | Rule value(s) | Expected visibility |
|----------|---------------|---------------|---------------------|
| `before` | `2024-06-01` | `2025-01-01` | visible |
| `before` | `2025-06-01` | `2025-01-01` | hidden |
| `after` | `2025-06-01` | `2025-01-01` | visible |
| `after` | `2024-06-01` | `2025-01-01` | hidden |
| `on` | `2025-01-01` | `2025-01-01` | visible |
| `on` | `2025-01-02` | `2025-01-01` | hidden |
| `between` | `2025-03-15` | `["2025-01-01","2025-06-01"]` | visible |
| `between` | `2024-03-15` | `["2025-01-01","2025-06-01"]` | hidden |
| `between` | `2025-01-01` | `["2025-01-01","2025-06-01"]` | visible (inclusive) |
| `between` | `2025-06-01` | `["2025-01-01","2025-06-01"]` | visible (inclusive) |

---

## 7. Frontend – Content Editor (selectorType)

These tests run in Cypress against the jContent Content Editor.

### 7.1 Source field dropdown lists only supported types

**Given** a form with fields: select, radio, checkbox, date, inputText, textarea.

**When** the logic tab is opened on `inputText`.

**Then** the source dropdown lists only: select, radio, checkbox, date. Not inputText or textarea.

### 7.2 Source field dropdown excludes self

**Given** a form with two selects: `role` and `category`.

**When** the logic tab is opened on `category`.

**Then** the source dropdown lists `role` but NOT `category`.

### 7.3 Source field dropdown excludes fields after target

**Given** a form with fields in order: `role`, `firstname`, `category`.

**When** the logic tab is opened on `firstname`.

**Then** the source dropdown lists `role` but NOT `category`.

### 7.4 Operator dropdown matches source type

| Source type | Expected operators |
|-------------|--------------------|
| `fmdb:select` | `in`, `notIn` |
| `fmdb:radio` | `in`, `notIn` |
| `fmdb:checkbox` (single) | `isChecked`, `isUnchecked` |
| `fmdb:checkbox` (group) | `containsAny`, `containsAll` |
| `fmdb:inputDate` | `before`, `after`, `on`, `between` |

### 7.5 Value dropdown reflects source choices

**Given** `role` is a select with choices `admin`, `editor`, `viewer`.

**When** a logic rule is created with source `role`.

**Then** the value multiselect shows: `admin`, `editor`, `viewer`.

### 7.6 Date source shows date inputs

**When** a logic rule is created with source `start-date` (date type).

**Then:**
- Non-`between` operators show a single date input.
- `between` operator shows two date inputs (from/to).

### 7.7 Each source can only be used once per target

**Given** `nickname` already has a rule on `role`.

**When** a second rule is added.

**Then** `role` is no longer available in the source dropdown for the second rule.

### 7.8 Source name updated after rename

**Given** `nickname` has a rule on `role`.

**When** `role` is renamed to `user-role` and the editor is reopened.

**Then** the source dropdown shows `user-role`, not `role`.

---

## 8. Frontend – Runtime visibility (preview/live)

These tests render the form in jContent preview or live mode and interact with the form.

### 8.1 Field without logic is always visible

A field with no `logics` property is visible on page load.

### 8.2 Field hidden when condition not met

**Given** `nickname` has logic: visible when `role` in `admin`.

**When** page loads with no selection on `role`.

**Then** `nickname` field is not visible.

### 8.3 Field shown when condition met

**When** `admin` is selected in `role`.

**Then** `nickname` field becomes visible.

### 8.4 Field re-hidden when condition no longer met

**When** `role` is changed from `admin` to `viewer`.

**Then** `nickname` field is hidden again.

### 8.5 Hidden field inputs are disabled

**When** `nickname` is hidden.

**Then** all `<input>`, `<select>`, `<textarea>` inside the hidden wrapper have `disabled` attribute.

### 8.6 Shown field inputs are re-enabled

**When** `nickname` becomes visible again.

**Then** inputs inside the wrapper no longer have `disabled` attribute.

### 8.7 Multiple rules (AND)

**Given** `nickname` requires `role` = `admin` AND `accept-terms` is checked.

- Only `role` = `admin` → hidden.
- Only `accept-terms` checked → hidden.
- Both satisfied → visible.

### 8.8 Step navigation with hidden fields

**Given** a multi-step form where step 2 has a required field controlled by logic.

**When** the condition to show step 2's field is not met.

**Then** the user can navigate from step 1 to step 2 and to step 3 without being blocked by the hidden required field.

---

## 9. Form structure scenarios

### 9.1 Logic on a field inside a fieldset

A field inside `fmdb:fieldset` with logic on a source outside the fieldset works correctly.

### 9.2 Logic on a step

A `fmdb:step` with logic (the entire step is conditionally shown/hidden).

### 9.3 Cross-step logic

Source in step 1 controls a target in step 2. Changing the source in step 1 updates visibility in step 2.

### 9.4 Source in later step not offered

A field in step 1 cannot use a field in step 2 as a logic source (ordering constraint).

---

## 10. Regression

### 10.1 Non-conditional fields render normally

Form with no conditional logic submits and displays as before.

### 10.2 Form without actions submits without error

A form with conditional logic but no `saveToJcr` or other action still processes the submission pipeline without error.

### 10.3 Existing form migration

A form created before the weakref model (with `sourceFieldId` in JSON, no `logicsSrc`) gets a `logicId` assigned and `logicsSrc` created on first save.

