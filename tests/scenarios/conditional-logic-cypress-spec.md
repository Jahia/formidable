# Conditional Logic – Test Spec Design

## Goal

Describe the full test strategy for the conditional logic feature, covering:

1. **Unit tests** for pure TypeScript functions (`formidable-engine`, Vitest)
2. **Unit tests** for Java logic evaluation (`formidable-engine`, JUnit 5)
3. **E2E tests** for the Content Editor selector type (`tests/`, Cypress)

This complements the broader backend and runtime scenarios documented in [conditional-logic.md](./conditional-logic.md).

---

## Part 1 – TypeScript unit tests (Vitest)

### Setup

Add Vitest to `formidable-engine/` as a dev dependency. These tests run without Jahia, in pure Node — no DOM, no Apollo, no JCR.

**Suggested location:** `formidable-engine/src/javascript/ConditionalLogic/__tests__/`

### 1.1 `parseRule`

| Test | Input | Expected |
|------|-------|----------|
| empty string | `''` | default rule: `logicId=''`, `sourceFieldType='fmdb:select'`, `operator='in'`, `values=[]` |
| undefined | `undefined` | same default |
| invalid JSON | `'not json'` | same default |
| valid JSON | `'{"logicId":"abc","sourceFieldName":"role","sourceFieldType":"fmdb:select","operator":"in","values":["admin"]}'` | parsed correctly |
| missing logicId | `'{"sourceFieldName":"role","sourceFieldType":"fmdb:select","operator":"in","values":["admin"]}'` | `logicId=''`, rest parsed |
| unknown sourceFieldType | `'{"sourceFieldName":"x","sourceFieldType":"fmdb:textarea","operator":"in"}'` | falls back to `fmdb:select` |
| values with non-strings | `'{"sourceFieldName":"x","sourceFieldType":"fmdb:select","operator":"in","values":["a",123,null]}'` | filters to `["a"]` |
| date rule with value | `'{"logicId":"d","sourceFieldName":"dt","sourceFieldType":"fmdb:inputDate","operator":"after","value":"2025-01-01"}'` | `value='2025-01-01'`, `values=[]` |
| date rule with between | `'...", "operator":"between","values":["2025-01-01","2025-06-01"]}'` | `values` has 2 entries, `value` undefined |

### 1.2 `normalizeStoredRule`

| Test | Source type | Operator | Input | Expected output shape |
|------|------------|----------|-------|----------------------|
| select with values | `fmdb:select` | `in` | `values: ["admin"]` | keeps `values`, no `value` |
| date non-between | `fmdb:inputDate` | `after` | `value: "2025-01-01"` | keeps `value`, no `values` |
| date between | `fmdb:inputDate` | `between` | `values: ["a","b","c"]` | truncates `values` to 2 entries |
| single checkbox | `fmdb:checkbox` (1 choice) | `isChecked` | any | no `values`, no `value` |
| checkbox group | `fmdb:checkbox` (3 choices) | `containsAny` | `values: [...]` | keeps `values` |
| no source (undefined) | – | – | any | returns default empty rule |
| invalid operator for source | `fmdb:select` | `isChecked` | any | sanitized to `in` |
| preserves logicId | any | any | `logicId: "xyz"` | output has `logicId: "xyz"` |

### 1.3 `getOperatorsForSource`

| Test | Source type | Choices count | Expected operators |
|------|------------|---------------|--------------------|
| no source | – | – | `['in']` |
| select | `fmdb:select` | any | `['in', 'notIn']` |
| radio | `fmdb:radio` | any | `['in', 'notIn']` |
| single checkbox | `fmdb:checkbox` | 1 | `['isChecked', 'isUnchecked']` |
| checkbox group | `fmdb:checkbox` | 3 | `['containsAny', 'containsAll']` |
| date | `fmdb:inputDate` | 0 | `['before', 'after', 'on', 'between']` |

### 1.4 `sanitizeOperator`

| Test | Source | Operator | Expected |
|------|--------|----------|----------|
| valid operator for source | select | `in` | `in` |
| invalid operator for source | select | `isChecked` | `in` (first valid) |
| no source | – | `in` | `in` |

### 1.5 `buildSourceFieldOptions`

Build a mock `GraphNode[]` list representing a flat form tree.

| Test | Input | Expected |
|------|-------|----------|
| target is last | nodes: `[select, checkbox, date, inputText]`, currentPath = inputText | returns 3 options (select, checkbox, date) |
| target is first | currentPath = select | returns `[]` |
| target not found | currentPath = unknown | returns `[]` |
| unsupported type excluded | nodes include `fmdb:textarea` | textarea not in result |
| choice values parsed | select node has `options` property with JSON values | `choiceValues` populated |
| date has empty choices | date node | `choiceValues = []` |
| displayName used as label | node with `displayName: "My Role"` | option `label = "My Role"` |
| displayName absent | node without `displayName` | option `label = node.name` |

### 1.6 `buildLogicIdToNameMap`

| Test | Input | Expected |
|------|-------|----------|
| empty array | `[]` | empty map |
| undefined | `undefined` | empty map |
| one valid node | `[{name: "abc", property: {refNode: {name: "role", uuid: "..."}}}]` | `Map { "abc" => "role" }` |
| broken refNode (null) | `[{name: "abc", property: {refNode: null}}]` | empty map |
| missing property | `[{name: "abc"}]` | empty map |
| multiple nodes | 3 valid nodes | map with 3 entries |

### 1.7 `findFormPath`

| Test | Input | Expected |
|------|-------|----------|
| node with fmdb:form ancestor | `ancestors: [{primaryNodeType: {name: "fmdb:form"}, path: "/x/y"}]` | `"/x/y"` |
| no form ancestor | `ancestors: [{primaryNodeType: {name: "fmdb:fieldset"}}]` | `undefined` |
| null node | `null` | `undefined` |
| multiple ancestors | form is second in list | returns the form's path |

### 1.8 `extractCurrentNodePath`

| Test | Source | Expected |
|------|--------|----------|
| from field.node.path | `{field: {node: {path: "/a"}}}` | `"/a"` |
| from field.nodePath | `{field: {nodePath: "/b"}}` | `"/b"` |
| from field.path | `{field: {path: "/c"}}` | `"/c"` |
| from editorContext.nodeData.path | `{editorContext: {nodeData: {path: "/d"}}}` | `"/d"` |
| from editorContext.path | `{editorContext: {path: "/e"}}` | `"/e"` |
| from context (legacy) | `{context: {path: "/f"}}` | `"/f"` |
| nothing available | `{field: {}}` | `undefined` |
| priority: field.node.path wins | both field.node.path and editorContext.path set | field.node.path |

### 1.9 `extractLanguage`

| Test | Source | Expected |
|------|--------|----------|
| from editorContext.nodeData.language | set | that value |
| from editorContext.lang | set | that value |
| from window.contextJsParameters.uilang | set | that value |
| nothing | empty props | `'en'` |

### 1.10 `extractWorkspace`

| Test | Source | Expected |
|------|--------|----------|
| from editorContext.nodeData.workspace | `'LIVE'` | `'LIVE'` |
| from editorContext.workspace | `'EDIT'` | `'EDIT'` |
| nothing | empty | `'EDIT'` |

---

## Part 2 – Java unit tests (JUnit 5)

### Setup

Add `src/test/java/` to `formidable-engine`. Use JUnit 5 (already in Jahia's dependency tree via `<scope>test</scope>`). Mock JCR with Mockito where needed.

### 2.1 `ConditionalLogicRule.parse`

These tests need a mock `javax.jcr.Value[]`. Use `Mockito.mock(Value.class)` with `when(v.getString()).thenReturn(json)`.

| Test | Input JSON | Expected |
|------|-----------|----------|
| valid rule | `{"logicId":"a","sourceFieldName":"role","sourceFieldType":"fmdb:select","operator":"in","values":["admin"]}` | 1 rule, all fields set |
| missing sourceFieldName | `{"operator":"in"}` | skipped (empty list) |
| missing operator | `{"sourceFieldName":"role"}` | skipped |
| blank string | `""` | skipped |
| invalid JSON | `"not json"` | skipped (no exception, empty list) |
| multiple rules | 3 valid JSON values | 3 rules |
| mixed valid and invalid | 2 valid + 1 invalid | 2 rules |
| date with value | `{"sourceFieldName":"dt","operator":"after","value":"2025-01-01"}` | `value()` = `"2025-01-01"`, `values()` empty |
| date with between | `{"sourceFieldName":"dt","operator":"between","values":["2025-01-01","2025-06-01"]}` | `values()` has 2 entries |

### 2.2 `ConditionalLogicEvaluator.isHidden`

No JCR mocking needed — the evaluator works with plain maps.

#### Test data builder

```java
var rules = Map.of(
    "nickname", List.of(rule("abc", "role", "in", List.of("admin"))),
    "extra", List.of(rule("def", "nickname", "in", List.of("test")))
);
var logicIdToFieldName = Map.of("abc", "role", "def", "nickname");
var parentContainer = Map.<String, String>of();
var submitted = Map.of("role", List.of("admin"));

var evaluator = new ConditionalLogicEvaluator(rules, logicIdToFieldName, parentContainer, submitted);
```

#### Visibility cases

| Test | Source value | Rule | Expected |
|------|------------|------|----------|
| no rules → visible | – | none | `isHidden("fieldX") = false` |
| `in` matched → visible | `role=admin` | `in ["admin"]` | `false` |
| `in` not matched → hidden | `role=viewer` | `in ["admin"]` | `true` |
| `notIn` matched → visible | `role=viewer` | `notIn ["admin"]` | `false` |
| `notIn` not matched → hidden | `role=admin` | `notIn ["admin"]` | `true` |
| `in` source absent → hidden | no `role` submitted | `in ["admin"]` | `true` |
| `isChecked` checked → visible | `cb=accepted` | `isChecked` | `false` |
| `isChecked` unchecked → hidden | no `cb` | `isChecked` | `true` |
| `isUnchecked` unchecked → visible | no `cb` | `isUnchecked` | `false` |
| `isUnchecked` checked → hidden | `cb=accepted` | `isUnchecked` | `true` |
| `containsAny` match → visible | `cb=sports,music` | `containsAny ["sports"]` | `false` |
| `containsAny` no match → hidden | `cb=music` | `containsAny ["sports"]` | `true` |
| `containsAll` all present → visible | `cb=sports,music` | `containsAll ["sports","music"]` | `false` |
| `containsAll` partial → hidden | `cb=sports` | `containsAll ["sports","music"]` | `true` |
| `before` earlier → visible | `dt=2024-06-01` | `before 2025-01-01` | `false` |
| `before` later → hidden | `dt=2025-06-01` | `before 2025-01-01` | `true` |
| `after` later → visible | `dt=2025-06-01` | `after 2025-01-01` | `false` |
| `after` earlier → hidden | `dt=2024-06-01` | `after 2025-01-01` | `true` |
| `on` exact → visible | `dt=2025-01-01` | `on 2025-01-01` | `false` |
| `on` different → hidden | `dt=2025-01-02` | `on 2025-01-01` | `true` |
| `between` inside → visible | `dt=2025-03-15` | `between ["2025-01-01","2025-06-01"]` | `false` |
| `between` outside → hidden | `dt=2024-03-15` | `between ["2025-01-01","2025-06-01"]` | `true` |
| `between` lower bound → visible | `dt=2025-01-01` | `between ["2025-01-01","2025-06-01"]` | `false` |
| `between` upper bound → visible | `dt=2025-06-01` | `between ["2025-01-01","2025-06-01"]` | `false` |
| unknown operator → hidden | any | `operator: "foo"` | `true` |

#### AND logic (multiple rules)

| Test | Rules | Submitted | Expected |
|------|-------|-----------|----------|
| both met → visible | `role in [admin]` + `cb isChecked` | `role=admin, cb=yes` | `false` |
| first met, second not → hidden | same | `role=admin` (no cb) | `true` |
| neither met → hidden | same | `role=viewer` (no cb) | `true` |

#### Transitive visibility

| Test | Setup | Expected |
|------|-------|----------|
| source hidden → dependent hidden | `level2` depends on `level1` (hide), `level3` depends on `level2` | `isHidden("level3") = true` |
| circular reference → no infinite loop | A depends on B, B depends on A | terminates, returns `false` (visiting guard) |

#### Parent container inheritance

| Test | Setup | Expected |
|------|-------|----------|
| parent hidden → child hidden | `parentContainer("child") = "parent"`, parent is hidden | `isHidden("child") = true` |
| parent visible → child uses own rules | parent visible, child has own rules | child evaluated normally |

#### logicId resolution

| Test | Setup | Expected |
|------|-------|----------|
| logicId resolves to field name | `logicIdToFieldName: {"abc": "role"}`, rule has `logicId: "abc"` | evaluates against `role` |
| logicId not in map, fallback to sourceFieldName | no logicId in map, `sourceFieldName: "role"` | evaluates against `role` |
| logicId not in map, sourceFieldName not in submitted | no match | `isHidden = true` |
| empty logicId | `logicId: ""` | uses sourceFieldName fallback |

---

## Part 3 – Cypress E2E (Content Editor)

### Suggested spec file

`tests/cypress/e2e/conditional-logic-selector.cy.ts`

### Test data

Create a form with these fields in order:

1. `role` — `fmdb:select` with choices `admin`, `editor`, `viewer`
2. `accept-terms` — `fmdb:checkbox` (single) with choice `accepted` / `I accept`
3. `start-date` — `fmdb:inputDate`
4. `nickname` — `fmdb:inputText`
5. `notes` — `fmdb:textarea` (unsupported source type, must not appear in dropdown)

The target field for editing is `nickname`.

### Setup strategy

Use `addNode()` from `@jahia/cypress` to create the form tree (same pattern as existing specs). Do not create content through the UI.

After node creation, navigate to the field in jContent and open the Content Editor.

### 3.1 Logic section is present

1. Open Content Editor for `nickname`.
2. Assert the `Logic` section/tab is rendered.
3. Assert the `logics` field is present and uses the `ConditionalLogic` selector (not a plain text input).

### 3.2 Available source fields

Open the source dropdown and verify:

- ✅ `role` is listed
- ✅ `accept-terms` is listed (label: `I accept` or its `jcr:title`)
- ✅ `start-date` is listed
- ❌ `nickname` is NOT listed (current field)
- ❌ `notes` is NOT listed (unsupported type `fmdb:textarea`)

### 3.3 Operator switching by source type

#### Select `role` as source

- operators: `in` and `notIn`
- value selector shows `admin`, `editor`, `viewer`

#### Select `accept-terms` as source

- operators: `isChecked` and `isUnchecked`
- no value dropdown shown

#### Select `start-date` as source

- operators: `before`, `after`, `on`, `between`
- non-`between`: one date input
- `between`: two date inputs (from/to)

### 3.4 Save and verify persistence

1. Select source `role`, operator `in`, values `["admin"]`.
2. Save the Content Editor.
3. Query `nickname` via GraphQL and verify:
   - `logics` property exists with one entry
   - parsed JSON contains: `logicId` (non-empty string), `sourceFieldName: "role"`, `sourceFieldType: "fmdb:select"`, `operator: "in"`, `values: ["admin"]`

### 3.5 Verify backend weakref sync

After save, query via GraphQL:

1. `nickname` has a descendant `logicsSrc` of type `fmdb:logicList`
2. `logicsSrc` has one child node named with the `logicId` from 3.4
3. that child has `logicNodeSource` pointing to the `role` node (verify UUID)

### 3.6 Source uniqueness across multivalue entries

1. Add a first rule using `role` as source.
2. Add a second `logics` entry (multivalue add).
3. In the second entry, open the source dropdown.
4. Assert `role` is NOT available (already used by sibling rule).
5. Assert `accept-terms` and `start-date` are still available.

### 3.7 Re-open editor shows persisted rule correctly

1. Save a rule as in 3.4.
2. Close the Content Editor.
3. Re-open the Content Editor for `nickname`.
4. Assert the Logic section shows the saved rule: source `role`, operator `in`, values `admin` selected.

### 3.8 Remove a rule

1. Start with a saved rule on `nickname`.
2. Open Content Editor, go to Logic section.
3. Remove the rule (multivalue remove).
4. Save.
5. Verify via GraphQL: `logics` is empty or absent, `logicsSrc` has no children.

---

## File locations summary

| Test type | Location | Runner | Dependencies to add |
|-----------|----------|--------|---------------------|
| TS unit tests | `formidable-engine/src/javascript/ConditionalLogic/__tests__/` | Vitest | `vitest` (devDependency in `formidable-engine`) |
| Java unit tests | `formidable-engine/src/test/java/.../logic/` | JUnit 5 + Mockito | JUnit 5, Mockito (test scope in `pom.xml`) |
| Cypress E2E | `tests/cypress/e2e/conditional-logic-selector.cy.ts` | Cypress | existing deps |

---

## CI integration

The current CI pipeline (`.github/workflows/`) does not run unit tests.

### Current state

| Workflow | What it does | Unit tests? |
|----------|-------------|-------------|
| `build.yml` | `yarn install` + `yarn workspace formidable-elements build` | No |
| `on-code-change.yml` | `jahia-modules-action/build` → `mvn clean install` | **JUnit: yes** (maven-surefire runs tests automatically if they exist in `src/test/`) |
| `on-code-change.yml` | `integration-tests` → Jahia Docker + Cypress | E2E only |

### What needs to change

#### JUnit 5 (Java) — already covered

`mvn clean install` runs `surefire:test` by default. As soon as test classes exist under `formidable-engine/src/test/java/`, they will be picked up automatically by the existing `on-code-change.yml` build job. **No workflow change needed.**

Dependencies to add in `formidable-engine/pom.xml`:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
```

The parent POM `jahia-modules:8.2.2.0` manages JUnit and Mockito versions via its BOM. No version tag needed.

#### Vitest (TypeScript) — one line to add

Vitest is not called by any workflow today. Two changes needed:

1. Add `vitest` as a devDependency in `formidable-engine/package.json`:

```bash
yarn workspace formidable-engine add -D vitest
```

2. Add a `"test"` script in `formidable-engine/package.json`:

```json
"scripts": {
    "build": "vite build",
    "test": "vitest run"
}
```

3. Add the test step in `build.yml` (or `on-code-change.yml`) before or after the build:

```yaml
- run: yarn workspace formidable-engine test
```

#### Cypress E2E — already integrated

The `integration-tests` job in `on-code-change.yml` already runs Cypress via `jahia-modules-action/reusable-integration-tests`. New `.cy.ts` spec files are picked up automatically.

