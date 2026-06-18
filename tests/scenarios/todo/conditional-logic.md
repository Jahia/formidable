# Conditional Logic – Remaining Test Scenarios (TODO)

Scenarios not yet covered by the automated suite. See `tests/scenarios/logics.md` for what is already covered.

---

## 4.2 Import of a form without logic

**Given** a form with no conditional logic is exported.

**When** it is imported.

**Then:**
- No `logicsSrc` node is created on any element.
- No WARN logs related to `logicsSrc` appear.

---

## 5. Server-side submission with conditional logic (not yet covered by Cypress)

These scenarios are covered by Java unit tests (`ConditionalLogicEvaluator`) but not yet by Cypress E2E tests using direct HTTP POST.

### Setup

Create a form with:
- **show-details** — `fmdb:radio` with choices: `yes`, `no`
- **firstname** — `fmdb:inputText` (required), with logic: visible when `show-details` = `yes`
- **lastname** — `fmdb:inputText` (required), with logic: visible when `show-details` = `yes`
- A `saveToJcr` action.

### 5.1 Hidden required fields do not block submission

Submit with `show-details=no`. Expected: `{"success": true}`.

### 5.2 Visible required fields block submission when absent

Submit with `show-details=yes` but no `firstname`/`lastname`. Expected: error.

### 5.3 Visible required fields pass when present

Submit with `show-details=yes`, `firstname=Jean`, `lastname=Dupuis`. Expected: success.

### 5.4 Hidden field values are ignored in saved submissions

Submit with `show-details=no`, `firstname=Jean`, `lastname=Dupuis`. Expected: success, but saved submission should NOT contain `firstname` or `lastname`.

### 5.5 Transitive visibility: hidden source hides dependent

Three levels: `level1` → `level2` → `level3`. When `level1=hide`, both `level2` and `level3` are transitively hidden.

### 5.6 Multiple rules (AND logic)

Field visible only when `role=admin` AND `accept-terms=accepted`. Test partial and full satisfaction.

---

## 6. Operator-specific scenarios (not covered by Cypress E2E)

Each scenario submits directly via HTTP POST. Covered by Java unit tests but not by Cypress.

### 6.1 Select / Radio operators: `in`, `notIn`
### 6.2 Checkbox (single): `isChecked`, `isUnchecked`
### 6.3 Checkbox (group): `containsAny`, `containsAll`
### 6.4 Date: `before`, `after`, `on`, `between` (including boundary values)

---

## 8. Frontend – Runtime visibility (preview/live)

Tests that render the form in jContent preview or live mode and interact with it.

### 8.1 Field without logic is always visible
### 8.2 Field hidden when condition not met (page load)
### 8.3 Field shown when condition met (interaction)
### 8.4 Field re-hidden when condition no longer met
### 8.5 Hidden field inputs are disabled
### 8.6 Shown field inputs are re-enabled
### 8.7 Multiple rules (AND) in the UI
### 8.8 Step navigation with hidden required fields

---

## 9. Form structure scenarios

### 9.1 Logic on a field inside a fieldset (source outside fieldset)
### 9.2 Logic on a step (entire step conditionally shown/hidden)
### 9.3 Cross-step logic (source in step 1, target in step 2)
### 9.4 Source in later step not offered (ordering constraint)

---

## 10. Regression

### 10.1 Non-conditional fields render normally
### 10.2 Form without actions submits without error
### 10.3 Existing form migration (legacy rules without logicId get normalized on first save)
