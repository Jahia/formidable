# Conditional Logic Field Resolution

## Purpose

Describe the persistence and resolution model currently used by Formidable conditional logic.

The current model keeps two representations in sync:

- the authoring payload stored in the multivalue `logics` property
- the repository-side dependency index stored under `logicsSrc`

## Field identity: `fieldKey`

Every form element carrying `fmdbmix:formLogicElement` (fields, fieldsets, steps) owns a
`fieldKey` property: a random UUID string that is its **stable business identity**.

- unlike the JCR UUID, it survives import and copy
- unlike the node name, it survives renames
- unlike the visible label, it is unique within a form

It is hidden from contributors and assigned server-side:

- `FieldKeyAssignmentListener` assigns it when the element is created
- `FormLogicSyncService` assigns it on the fly to legacy elements it touches (sync target,
  resolved rule sources, and every logic element visited during duplication cleanup)

## Stored rule format

Each `logics` entry is stored as JSON.

Example:

```json
{
  "logicId": "a1b2c3d4",
  "sourceNodeId": "4028c1e2-934f-2f92-0193-4f6ac4f00041",
  "sourceFieldKey": "550e8400-e29b-41d4-a716-446655440000",
  "sourceFieldName": "role",
  "sourceFieldType": "fmdb:select",
  "operator": "notIn",
  "values": ["marketing", "sales"]
}
```

Meaning:

- `logicId` identifies the rule and matches the child node name under `logicsSrc`
- `sourceFieldKey` is the **business reference** to the source field (its `fieldKey`);
  primary resolution criterion
- `sourceNodeId` is the technical source identifier (JCR UUID); fast path and tie-breaker
- `sourceFieldName` and `sourceFieldType` remain editor/runtime metadata and legacy fallback

`jsVariable` rules (`sourceType: "jsVariable"`) reference a browser variable instead of a
field and carry none of the source-field keys.

## Repository-side structure

For each target field carrying `fmdbmix:formLogicElement`, the repository may also contain:

```text
targetField (fmdbmix:formLogicElement)
  ├─ fieldKey = "..."
  ├─ logics = ["{ \"logicId\": \"a1b2c3d4\", ... }"]
  └─ logicsSrc (fmdb:logicList)
       └─ a1b2c3d4 (fmdb:logicSrc)
            └─ logicNodeSource -> weakreference to the source field
```

Intent:

- `logics` remains the authoring format
- `logicsSrc` is technical storage
- `logicNodeSource` is the repository-native weakreference used by server-side metadata collection and runtime enrichment

## Resolution order

When Formidable needs to resolve the source field for a rule, it uses this order:

1. when the rule carries a `sourceFieldKey`:
   1. `sourceNodeId`, if the designated node carries that key (tie-breaker while several
      nodes transiently share a key, e.g. right after a same-form duplication)
   2. the existing `logicsSrc/<logicId>/logicNodeSource` weakreference, under the same
      key-match condition
   3. the first field (document order, before the target) whose `fieldKey` matches
2. legacy chain, for rules stored before `fieldKey` existed:
   1. `sourceNodeId` from the JSON rule
   2. an existing `logicsSrc/<logicId>/logicNodeSource` weakreference, if still valid
   3. `sourceFieldName` as fallback

Whatever path resolved the source, the sync then backfills the JSON: `sourceNodeId` is
refreshed and `sourceFieldKey` is written from the resolved source's `fieldKey` (assigning
one to the source when missing). Legacy rules therefore converge to the new format on
their first sync.

## Synchronization rules

`FormLogicSyncService` is responsible for keeping JSON and `logicsSrc` aligned.

### During normal authoring

When `logics` is added, changed, or removed:

1. ensure the target element has a `fieldKey`
2. parse each JSON rule
3. ensure every rule has a `logicId`
4. resolve the source field (see resolution order)
5. update `sourceNodeId` and `sourceFieldKey` in the JSON if needed
6. create or update `logicsSrc/<logicId>`
7. remove orphan `logicsSrc` children not referenced by any remaining JSON rule

### After duplication, import, or session-save copy

When a subtree duplication occurs:

1. when the added node is an element inside an existing form (same-form copy/paste),
   remap `fieldKey` collisions first: every copied element whose key already exists
   outside the copied subtree gets a fresh key, and rules **inside the copied subtree**
   are rewritten to the fresh keys — the copy references its own internal sources,
   the original is untouched
2. assign a `fieldKey` to every logic element that lacks one
3. find `logicsSrc` entries whose weakreference points outside the current form
4. remove only those broken or out-of-scope `logicsSrc` children
5. preserve the JSON `logics` entries
6. rerun synchronization so the source can be rebound from `sourceFieldKey`,
   `sourceNodeId`, a still-valid local weakref, or `sourceFieldName`

Full-form duplications and cross-form copies never collide (keys are random UUIDs), so
step 1 is a no-op for them; import repair relies on `sourceFieldKey` (JCR UUIDs change,
exported properties do not).

## Invariants

The system should maintain these invariants:

1. every valid JSON rule has a non-empty `logicId`
2. every field rule converges to carrying a `sourceFieldKey` after its first sync
3. `fieldKey` is unique within a form once duplication remapping has run
4. every live `logicsSrc/<logicId>` child corresponds to an active JSON rule
5. `logicNodeSource` must point to a source field within the same form when the mapping is valid

## Import/export and copy behavior

### Import/export

The model relies on:

- `sourceFieldKey` in JSON (survives the UUID changes caused by import)
- `sourceNodeId` in JSON (fast path, repaired when stale)
- `logicNodeSource` weakreferences in `logicsSrc`

Import/export no longer involves any legacy field-id migration path. Any repair work is about rebinding broken or out-of-scope references, not migrating an older persisted identifier format.

### Copy/paste or workspace copy

- a rule is valid only if its source can still be resolved safely in the copied form
- broken external weakrefs are removed
- surviving JSON rules are rebound when possible
- unresolved rules may remain degraded until a valid source can be resolved again

The duplication cleanup listener currently runs for:

- `IMPORT`
- `WORKSPACE_COPY`
- `SESSION_SAVE` copy paths such as GraphQL `copyNode`

The `SESSION_SAVE` support exists because some supported copy flows do not surface as `WORKSPACE_COPY`. The listener is guarded and only runs when the added node or copied form subtree already contains `logics`, `logicsSrc`, or a `fieldKey` (freshly created elements have none of those, so normal authoring is unaffected).

## Historical limitation: duplicate system names

Before `fieldKey`, conditional logic was not fully safe when two different fields shared
the same system name (e.g. `termination/select-an-option` and `reduction/select-an-option`):
when `sourceNodeId` and the weakref were both unavailable, the name-based fallback could
bind the rule to the wrong homonym.

Rules carrying a `sourceFieldKey` are immune: the key designates one specific field
regardless of names and labels. The limitation only remains for legacy rules that have
never been re-synced (their first sync backfills the key from whatever source the legacy
chain resolves — keeping system names unique until then remains a good practice).
