# Conditional Logic: duplicate source field names

## Problem

Some forms contain multiple distinct JCR nodes with the same name, for example:

- `termination/select-an-option`
- `reduction/select-an-option`

The conditional logic authoring payload currently stores only:

- `logicId`
- `sourceFieldName`
- `sourceFieldType`

That is not enough to distinguish the two source fields above during initial logic creation.

## What was observed

In the Content Editor source dropdown, the two sources are displayed with the same label:

- `Select an option`
- `Select an option`

There is no visible parent context (`Termination`, `Reduction`, path, etc.), so the author cannot reliably choose the intended source.

## Current backend behavior

`FormLogicSyncService.sync(...)` resolves the source weakref from `sourceFieldName` only.

Relevant code:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/logic/FormLogicSyncService.java`
  - `syncSourceField(...)`
  - `buildFieldsByNameMap(...)`

The name lookup is currently built as:

- `Map<String, JCRNodeWrapper> fieldsByName`
- `map.put(child.getName(), child)`

So when two source nodes share the same JCR name, the last one encountered in traversal order wins.

## Tests added

Added:

- `formidable-engine/src/test/java/org/jahia/modules/formidable/engine/logic/FormLogicSyncServiceTest.java`

### Test 1

`syncBindsDuplicateSourceFieldNameToLastMatchingNodeInTraversalOrder`

This test models:

- one `select-an-option` under `termination`
- one `select-an-option` under `reduction`
- a target logic that references only `sourceFieldName = "select-an-option"`

Result today:

- the weakref is created against the `reduction` node
- this is the current behavior, and the test passes

### Test 2

`syncPreservesExistingWeakrefWhenItAlreadyPointsToTerminationSource`

This test models:

- same duplicate source names as above
- but `logicsSrc/{logicId}/logicNodeSource` already points to the `termination` node

Result today:

- `sync(...)` preserves the existing weakref
- it does not overwrite it with the later `reduction` node

## Conclusion

For the specific bug covered here, the remaining defect is scoped to **initial rule creation**. Once a weakref exists and points to a valid node with the expected name, subsequent syncs preserve it. The re-sync overwrite scenario (where a correct weakref was replaced by the wrong duplicate) has been fixed in `ensureLogicSrcNode`.

The system still cannot create the correct weakref reliably during initial authoring when multiple source nodes share the same `sourceFieldName`.

This note is intentionally scoped to the **duplicate source-field binding** problem. It does **not** claim that every duplicate-name edge case in submission/runtime evaluation is fully resolved elsewhere in the stack.

**Reproduction**: contributor creates a new conditional logic rule, selects a source field that has a duplicate name in another conditional container. The sync resolves by name and may bind to the wrong instance. This produces a `logicNodeSource` weakref pointing to the wrong node, which then propagates through `LogicAwareRender` as a wrong `sourceNodeId` to the front-end.

## Changes already implemented

The following fixes address the **runtime** and **submission** paths. They do not fix the initial authoring binding described above.

### Front-end

| File | Change |
|---|---|
| `formidable-elements/src/components/FormContainer/LogicAwareRender.tsx` | `resolveSourceNodeIds()` reads `logicsSrc` weakrefs server-side and injects `sourceNodeId` (UUID) into the serialized `data-fmdb-logics` attribute |
| `formidable-elements/src/utils/conditionalLogic.ts` | `applyConditionalLogicVisibility()` resolves source wrappers by UUID via `wrappersByNodeId`. No name-based fallback. |
| `formidable-elements/src/utils/conditionalLogic.ts` | `parseConditionalLogicRule()` preserves `logicId` and `sourceNodeId` |
| `formidable-elements/src/components/FormContainer/hidden.logic.server.tsx` | Filters out `fmdb:logicList` (`logicsSrc`) nodes from rendering to remove DOM noise |

### Backend pipeline (form submission)

| File | Change |
|---|---|
| `formidable-engine/.../FormFieldMetadataCollector.java` | No longer crashes on duplicate field names. Tracks all parent containers via `Map<String, Set<String>>` |
| `formidable-engine/.../ConditionalLogicEvaluator.java` | `isHidden()` treats a field as hidden only when **all** its parent containers are hidden, not just one |

### Backend sync

| File | Change |
|---|---|
| `formidable-engine/.../FormLogicSyncService.java` | `ensureLogicSrcNode()` preserves an existing weakref when the referenced node has the expected name, preventing overwrite by the wrong duplicate on re-sync |

## Remaining defect

For this specific bug, the remaining issue is the **initial creation** of a rule targeting a source field with a duplicate name. At that point, no weakref exists yet and the sync service resolves by name only.

The dropdown in Content Editor also lacks parent context, so the contributor cannot distinguish between:

- `Select an option`
- `Select an option`

Both the dropdown display and the authoring payload must be fixed together.

## Likely fix direction

We need a unique source identifier in the authoring payload. Two options:

| Option | Pros | Cons |
|---|---|---|
| `sourceFieldPath` (relative to form) | Human-readable for debug, survives copy/paste/import better than UUID, can be resolved relative to the form node | Changes if the field is moved or renamed |
| `sourceFieldUuid` | Globally unique | Changes on copy/paste, was explicitly removed when introducing the `logicsSrc` weakref model |

**Recommended: `sourceFieldPath`** — a path relative to the form node (e.g. `fields/termination/select-an-option`). It is readable, duplication-friendly, and the sync service already has access to the form node to resolve it. It is not immutable, so `sourceFieldName` should remain as readable fallback metadata.

### Steps

1. **Content Editor dropdown** (`ConditionalLogicCmp.tsx`): display parent context in the label, e.g. `Select an option (Termination)` vs `Select an option (Reduction)`.
2. **Authoring payload**: store `sourceFieldPath` alongside `sourceFieldName` in the `logics` JSON. `sourceFieldName` remains as readable metadata.
3. **`FormLogicSyncService`**: resolve source weakref from `sourceFieldPath` first, fall back to `sourceFieldName` for rules created before the fix.
4. **`buildFieldsByNameMap`**: replace with a path-based lookup or keep as fallback only.

## Command used to run the targeted test

Because Maven was still using Java 11 by default, the test was run with:

```bash
JAVA_HOME=/usr/lib/jvm/java-19-openjdk-amd64 PATH=/usr/lib/jvm/java-19-openjdk-amd64/bin:$PATH mvn -pl formidable-engine -Dtest=FormLogicSyncServiceTest test
```

Result:

- `BUILD SUCCESS`
- `Tests run: 2, Failures: 0, Errors: 0`
