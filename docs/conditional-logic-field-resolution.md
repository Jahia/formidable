# Conditional Logic Field Resolution

## Purpose

Describe the persistence and resolution model currently used by Formidable conditional logic.

The current model keeps two representations in sync:

- the authoring payload stored in the multivalue `logics` property
- the repository-side dependency index stored under `logicsSrc`

## Stored rule format

Each `logics` entry is stored as JSON.

Example:

```json
{
  "logicId": "a1b2c3d4",
  "sourceNodeId": "4028c1e2-934f-2f92-0193-4f6ac4f00041",
  "sourceFieldName": "role",
  "sourceFieldType": "fmdb:select",
  "operator": "notIn",
  "values": ["marketing", "sales"]
}
```

Meaning:

- `logicId` identifies the rule and matches the child node name under `logicsSrc`
- `sourceNodeId` is the canonical source identifier in the JSON payload
- `sourceFieldName` and `sourceFieldType` remain editor/runtime metadata

## Repository-side structure

For each target field carrying `fmdbmix:formLogicElement`, the repository may also contain:

```text
targetField (fmdbmix:formLogicElement)
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

1. `sourceNodeId` from the JSON rule
2. an existing `logicsSrc/<logicId>/logicNodeSource` weakreference, if still valid
3. `sourceFieldName` as fallback

That fallback exists for recovery scenarios. It is not the preferred path.

## Synchronization rules

`FormLogicSyncService` is responsible for keeping JSON and `logicsSrc` aligned.

### During normal authoring

When `logics` is added, changed, or removed:

1. parse each JSON rule
2. ensure every rule has a `logicId`
3. resolve the source field
4. update `sourceNodeId` in the JSON if needed
5. create or update `logicsSrc/<logicId>`
6. remove orphan `logicsSrc` children not referenced by any remaining JSON rule

### After duplication, import, or session-save copy

When a subtree duplication occurs:

1. find `logicsSrc` entries whose weakreference points outside the current form
2. remove only those broken or out-of-scope `logicsSrc` children
3. preserve the JSON `logics` entries
4. rerun synchronization so the source can be rebound from:
   - `sourceNodeId`
   - a still-valid local weakref
   - `sourceFieldName`

## Invariants

The system should maintain these invariants:

1. every valid JSON rule has a non-empty `logicId`
2. every rule source should be resolvable preferentially by `sourceNodeId`
3. every live `logicsSrc/<logicId>` child corresponds to an active JSON rule
4. `logicNodeSource` must point to a source field within the same form when the mapping is valid

## Import/export and copy behavior

### Import/export

The current model relies on:

- `sourceNodeId` in JSON
- `logicNodeSource` weakreferences in `logicsSrc`

Import/export no longer involves any legacy field-id migration path. Any repair work is about rebinding broken or out-of-scope references, not migrating an older persisted identifier format.

### Copy/paste or workspace copy

Copying remains the sensitive case:

- a rule is valid only if its source can still be resolved safely in the copied form
- broken external weakrefs are removed
- surviving JSON rules are rebound when possible
- unresolved rules may remain degraded until a valid source can be resolved again

The duplication cleanup listener currently runs for:

- `IMPORT`
- `WORKSPACE_COPY`
- `SESSION_SAVE` copy paths such as GraphQL `copyNode`

The `SESSION_SAVE` support exists because some supported copy flows do not surface as `WORKSPACE_COPY`. The listener is guarded and only runs when the added node or copied form subtree already contains `logics` or `logicsSrc`.

## Known limitation: duplicate system names

Conditional logic is not fully safe when two different fields share the same system name.

Example:

- `termination/select-an-option`
- `reduction/select-an-option`
- a target rule should depend on `reduction/select-an-option`

If `sourceNodeId` and the weakref are both unavailable, the fallback by `sourceFieldName` may bind the rule to `termination/select-an-option` instead.

Possible user-visible effects:

- a field becomes visible or hidden based on the wrong source answer
- browser-side logic evaluates the wrong field
- server-side required validation may reason about the wrong visibility state

Recommendation: keep system names unique across the form whenever a field participates in conditional logic.
