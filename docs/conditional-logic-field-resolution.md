# Form Logic Weakref Model Specification

## Purpose

Define a persistence model for conditional logics that:

- keeps authoring simple
- removes import/export repair on logic source references
- uses native JCR weakreferences as the canonical dependency link
- limits post-processing to copy/paste cleanup according to the mixed-case rule

This document supersedes the earlier approach where `logics` stored `sourceFieldId` directly.

## Design summary

The new model separates:

- a simple authoring payload: `logics`
- a technical dependency index: `logicsSrc`

With this model:

- `logics` no longer stores the referenced field UUID
- `logics` stores a stable `logicId`
- each `logicId` is the **name of a child node** under `logicsSrc`
- that child node stores the actual source field as a `weakreference`

As a result:

- import/export no longer needs logic source repair
- weakreferences are handled natively by Jahia/JCR
- the remaining repair problem is copy/paste cleanup

## CND model

The runtime CND definitions described below now live in
`formidable-engine/src/main/resources/META-INF/definitions.cnd`, because they are
interpreted by engine-side logic synchronization and submission code.

### `fmdbmix:formLogicElement`

Existing:

- `logics (string) multiple indexed=no`

Added:

- `+ logicsSrc (fmdb:logicList) = fmdb:logicList autocreated`

Intent:

- `logics` remains the authoring format consumed by the editor
- `logicsSrc` is an internal structured index, not edited directly by contributors

### `fmdb:logicList`

```cnd
[fmdb:logicList] > nt:base, jmix:lockable, mix:lastModified, jmix:lastPublished, mix:versionable, jmix:observable, jmix:workflow, jmix:list
 + * (fmdb:logicSrc) = fmdb:logicSrc
```

Intent:

- one child node per logic rule
- child node **name** is the `logicId` — no separate identifier property needed
- JCR guarantees name uniqueness under a parent

### `fmdb:logicSrc`

```cnd
[fmdb:logicSrc] > jnt:content
 - logicNodeSource (weakreference) mandatory
```

Intent:

- single property: the canonical source field reference
- the node name serves as the `logicId` join key with the JSON in `logics`

### Why use node name as logicId

Using the node name as the join key instead of a separate `logicId` property:

- **Eliminates a redundant property** — one less field in the CND
- **Makes lookup direct** — `logicsSrc.getNode(logicId)` instead of iterating children
- **Guarantees uniqueness** — JCR enforces unique child names under a parent
- **Simplifies sync code** — `hasNode()` / `addNode()` / `getNode()` instead of query-by-property

## JSON format in `logics`

### Old model

Before:

```json
{
  "sourceFieldId": "42f4b274-1b7d-44f0-9fb4-805e4c8ad048",
  "sourceFieldName": "role",
  "sourceFieldType": "fmdb:select",
  "operator": "notIn",
  "values": ["marketing", "sales"]
}
```

Problem:

- UUID embedded in JSON is not managed by JCR
- import/export requires custom repair

### New model

After:

```json
{
  "logicId": "c0b7e4a9",
  "sourceFieldName": "role",
  "sourceFieldType": "fmdb:select",
  "operator": "notIn",
  "values": ["marketing", "sales"]
}
```

Meaning:

- `logicId` is the name of a child node under `logicsSrc`
- the actual source field is resolved through `logicsSrc/{logicId}/logicNodeSource`
- `sourceFieldName` and `sourceFieldType` remain editor metadata, not the canonical reference

### JCR structure example

```
inputEmail (fmdbmix:formLogicElement)
  ├─ logics = ["{ \"logicId\": \"c0b7e4a9\", ... }", "{ \"logicId\": \"d1e8f5b0\", ... }"]
  └─ logicsSrc (fmdb:logicList)
       ├─ c0b7e4a9 (fmdb:logicSrc)
       │    └─ logicNodeSource → weakref to "role" field
       └─ d1e8f5b0 (fmdb:logicSrc)
            └─ logicNodeSource → weakref to "email" field
```

## Why keep `sourceFieldName` and `sourceFieldType`

Even if the canonical source is now the weakreference, the JSON should still keep:

- `sourceFieldName`
- `sourceFieldType`

Reasons:

- editor display and editing convenience
- debug readability
- fallback context if a cleanup service must drop or rebuild entries
- human-readable exported content

They are metadata. They are no longer the authoritative source link.

## Canonical source of truth

The canonical source of truth becomes:

- `logicId` in `logics` → matches node name under `logicsSrc`
- `logicNodeSource` weakreference on that node

The source field UUID must not be duplicated in JSON anymore.

## Resolution model at runtime

Runtime evaluation should work like this:

1. read one JSON rule from `logics`
2. read its `logicId`
3. resolve the corresponding node: `logicsSrc.getNode(logicId)`
4. read `logicNodeSource` weakreference
5. evaluate the rule against that resolved field

This means runtime logic resolution no longer depends on a field UUID stored in JSON.

## Authoring workflow

### Creation

When a contributor creates a new logic rule:

1. generate a new `logicId` (e.g. short UUID)
2. append the JSON entry to `logics`
3. the sync listener creates `logicsSrc/{logicId}` with `logicNodeSource` pointing to the source field

### Update

When a contributor edits a rule:

- keep the same `logicId`
- update JSON metadata in `logics`
- the sync listener updates `logicNodeSource` if the source changed

### Delete

When a contributor deletes a rule:

- remove the JSON entry from `logics`
- the sync listener removes the `logicsSrc/{logicId}` child node

## Invariants

The system must maintain these invariants:

1. every JSON rule has a non-empty `logicId`
2. every `logicId` in `logics` has exactly one child node named `logicId` under `logicsSrc`
3. every `logicsSrc` child points to one source field through `logicNodeSource`
4. there are no orphan `logicsSrc` nodes (nodes without a matching JSON rule)
5. there are no duplicate `logicId` values under the same form logic element (guaranteed by JCR)

## Import/export behavior

### Expected outcome

This model is specifically designed so that import/export no longer needs custom source-field repair.

Reason:

- `logicNodeSource` is a real JCR weakreference
- Jahia/JCR already knows how to export/import and resolve repository references natively

Therefore:

- no more `sourceFieldId` repair after XML import
- no need to derive field UUIDs again from names after import
- no post-import logic source repair service is required for import/export

### What still needs to be validated

Even though this is the intended benefit, the implementation should be validated with a real export/import roundtrip for:

- a field with one rule
- a field with multiple rules
- nested fieldsets / steps
- multiple source field types

But architecturally, this model removes the original cause of the import/export problem.

## Copy/paste behavior

Import/export is no longer the issue.

The remaining issue is copy/paste.

### Mixed-case rule

Use one general rule for all copy/paste cases:

- a logic rule remains valid only if its source can still be resolved as a valid local dependency for the pasted subtree
- if it can be resolved uniquely and safely in the pasted context, keep or repair it
- if it cannot be resolved safely, remove it
- if no rules remain, clear `logics` and `logicsSrc`

This covers:

- target pasted alone
- target pasted with all its source fields
- target pasted with only some of its source fields

### Copy/paste strategy in detail

#### Case A: target pasted without its sources

Expected result:

- pasted logic rules must be removed
- matching `logicsSrc` entries must be removed

Reason:

- a logic must not silently keep a dependency on an unrelated field outside the intended pasted subtree

#### Case B: target pasted with all its sources

Expected result:

- logic rules should remain valid
- technical mapping should point to the copied sources in the pasted subtree

#### Case C: partial copy

Expected result:

- resolvable rules are kept
- unresolved rules are removed
- if the field ends up with zero remaining rules, `logics` becomes empty and `logicsSrc` becomes empty

### Important semantic constraint

Do not guess against pre-existing fields already present in the destination form.

In other words:

- no fallback to a same-name field already in the target form
- no cross-form inference
- no best-effort merge with existing form structure

Logic dependencies must remain local and explicit.

## Listener architecture

This model needs two different listener responsibilities.

They should not be merged, because they solve different problems.

### Listener A: authoring synchronization

Purpose:

- keep `logicsSrc` synchronized with `logics` after normal repository writes

This is the mechanism that intercepts save-time changes to `logics`.

#### Recommended listener type

Use a Jahia `DefaultEventListener`.

#### Recommended event types

- `PROPERTY_ADDED`
- `PROPERTY_CHANGED`
- `PROPERTY_REMOVED`

#### Recommended node type filter

- `fmdbmix:formLogicElement`

#### Fine-grained filter inside `onEvent()`

Inside the listener, process only events where:

- `event.getPath().endsWith("/logics")`

This keeps the listener narrowly scoped to authoring changes on the logic payload.

#### Why this is the right mechanism

This synchronization is:

- technical
- repository-driven
- needed regardless of the UI entry point

So a JCR listener is the right level.

This is preferable to:

- a content-editor-only hook
- a validator
- a Drools rule

#### Responsibilities

When `logics` changes, the listener should call an idempotent Java service that:

1. parses `logics`
2. ensures every rule has a `logicId`
3. for each rule, resolves `sourceFieldName` to a field UUID in the form tree
4. creates or updates `logicsSrc/{logicId}` with `logicNodeSource` weakreference
5. removes orphan `logicsSrc` children not referenced by any JSON rule
6. saves only if something changed

#### Sync code pattern

```java
// Create or update
for (JSONObject rule : rules) {
    String logicId = rule.getString("logicId");
    String sourceFieldName = rule.getString("sourceFieldName");
    String sourceFieldId = resolveFieldId(formNode, sourceFieldName);

    if (logicsSrc.hasNode(logicId)) {
        JCRNodeWrapper existing = logicsSrc.getNode(logicId);
        // update weakref if changed
    } else {
        JCRNodeWrapper newNode = logicsSrc.addNode(logicId, "fmdb:logicSrc");
        newNode.setProperty("logicNodeSource", session.getNodeByIdentifier(sourceFieldId));
    }
}

// Remove orphans
NodeIterator children = logicsSrc.getNodes();
while (children.hasNext()) {
    JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
    if (!activeLogicIds.contains(child.getName())) {
        child.remove();
    }
}
```

#### Loop prevention

The listener must not react to its own technical updates.

To achieve that:

- listen only to `.../logics`
- do not react to changes under `logicsSrc`
- keep the sync service idempotent

### Listener B: duplication cleanup

Purpose:

- cleanup and normalize logic dependencies after subtree duplication

This is not authoring sync.
This is post-duplication integrity cleanup.

#### Recommended listener type

Use a Jahia `DefaultEventListener`.

#### Recommended event types

- `NODE_ADDED`

#### Recommended node type filter

- `fmdb:form`

#### Recommended operation type filter

At minimum:

- `JCRObservationManager.IMPORT`
- `JCRObservationManager.WORKSPACE_COPY`

Optionally also:

- `JCRObservationManager.WORKSPACE_CLONE`

depending on whether that operation is used in your actual workflows.

#### Responsibilities

The duplication cleanup listener should call an idempotent Java cleanup service that:

1. walks the duplicated form subtree
2. for each `fmdbmix:formLogicElement` node with `logicsSrc` children:
   - checks if `logicNodeSource` weakref resolves to a node **within the same form subtree**
   - if yes: keep the rule
   - if no (points outside the form, or is broken): remove the rule from `logics` and the child from `logicsSrc`
3. clears `logics` and `logicsSrc` if no rules remain

## Cleanup mechanism

### Recommended approach

Use:

- a Java idempotent service
- triggered by listeners
- responsible for structural cleanup and consistency enforcement

This service is not for import/export repair anymore.
It is for:

- authoring synchronization
- copy/paste cleanup
- integrity cleanup
- orphan removal

### Why not Drools

Drools is not the preferred mechanism here because the task is:

- technical
- deterministic
- local to one node
- not business-rule driven

A Java service triggered by a listener is simpler to reason about and test.

### Idempotency expectations

Running the cleanup service multiple times on the same node must produce the same result.

Expected properties:

- no duplicate creation
- no repeated mutation once state is clean
- no save when nothing changed

## Migration from the old model

Existing `logics` entries that still contain `sourceFieldId` must be migrated.

Migration steps:

1. for each `fmdbmix:formLogicElement` node with `logics`:
   - for each JSON rule containing `sourceFieldId`:
     - generate a new `logicId`
     - create `logicsSrc/{logicId}` with `logicNodeSource` pointing to the source field UUID
     - replace `sourceFieldId` with `logicId` in the JSON
     - keep `sourceFieldName` and `sourceFieldType` as metadata
2. save the session

JSON after migration should no longer contain source UUIDs.

## Contribution UX impact

Contributor impact should remain minimal:

- the editor still edits `logics`
- `logicsSrc` remains hidden technical storage
- no contributor-facing change in complexity

This is a key design constraint and should be preserved.

## Recommended final stance

Adopt this model:

- `logics` stays as fast authoring payload
- `logics` stores `logicId` (which is the node name under `logicsSrc`)
- `logicsSrc` child nodes each have a single `logicNodeSource` weakreference
- import/export relies on native JCR reference handling
- copy/paste is handled by an idempotent Java cleanup service triggered by listener
- node name = `logicId` — no redundant identifier property

This gives:

- simple contribution
- native import/export behavior
- explicit and inspectable dependencies
- deterministic cleanup after duplication
- minimal CND footprint
