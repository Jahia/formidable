# Recommendation: Source Field Disambiguation via UUID

## Problem Statement

The current conditional logic system is ambiguous when resolving the source field during:

1. **Initial rule creation** — the editor dropdown uses `source.name` as its value, so two fields with the same name are indistinguishable
2. **Rebuild after duplication/import** — when weakrefs break, the system falls back to `sourceFieldName`, which can be ambiguous

## Observation: UUID Is Already Available

The codebase already has nearly everything needed:

- `SourceFieldOption` already carries `id: node.uuid` (from `mapSourceField()`)
- The public runtime (`formidable-elements`) already resolves by `sourceNodeId` in `applyConditionalLogicVisibility()`
- The `ConditionalLogicRule` interface in `formidable-elements` already declares `sourceNodeId?: string`
- The `logicId` + weakref mechanism already handles renames and runtime resolution

The gap is narrow: **the editor component and the backend sync service do not use the UUID that is already available**.

## Proposed Changes

### 1. Editor: Use UUID as Dropdown Key

In `ConditionalLogicCmp.tsx` and `ConditionalLogic.utils.ts`:

- Use `source.id` (UUID) as the `value` property of dropdown options instead of `source.name`
- Resolve `selectedSource` by UUID, not by name
- Filter already-used siblings by UUID, not by name
- Store `sourceNodeId` in the rule JSON alongside `sourceFieldName`

### 2. Editor: Disambiguate Duplicate Labels

In `buildSourceFieldOptions()` or in the component:

- When multiple sources share the same `displayName`, append a `:N` suffix (1-based, in display order)
- Example: `Select an option:1`, `Select an option:2`

### 3. Backend Sync: Resolve by UUID First

In `FormLogicSyncService.syncSourceField()`:

Resolution chain:

```
1. sourceNodeId (UUID) → session.getNodeByIdentifier() → verify node is within form subtree
   ↓ (if absent, invalid, or out of scope)
2. existing weakref from logicsSrc/{logicId} → if already correct, keep it
   ↓ (if absent or broken)
3. sourceFieldName → legacy fallback (may be ambiguous, accepted)
```

This requires:

- Parsing `sourceNodeId` from the JSON in `parseLogicEntry()`
- Adding a UUID-first resolution path in `syncSourceField()`
- Passing the JCR session to allow `getNodeByIdentifier()` calls

### 4. Backend Sync: Write Back sourceNodeId

When the sync resolves a source field (by any method), if `sourceNodeId` is absent or stale in the JSON, write back the resolved node's UUID. This ensures that after the first successful sync, future syncs benefit from UUID resolution.

### 5. Duplication / Import: No Change Needed

The existing `cleanupAfterDuplication()` flow remains unchanged:

1. Weakrefs pointing outside the form subtree are purged
2. `sync()` re-runs on each logic element
3. `sourceNodeId` from the JSON will not resolve (UUIDs changed) → falls through to `sourceFieldName` fallback
4. If names are unique within the copied subtree (normal case), resolution succeeds

The rare case of "duplicate names across containers + duplication" remains ambiguous — same as today. This is acceptable because:

- JCR node names are unique within a parent
- Duplicate names only exist across different fieldsets
- The combination of duplicate names + duplication is structurally rare

### 6. Java Record: Add sourceNodeId

In `ConditionalLogicRule.java` (the record used by the evaluator), no change is strictly needed — the evaluator works from the weakref-resolved map. But `ParsedLogicEntry` in `FormLogicSyncService` should carry `sourceNodeId`.

### 7. No Change to ConditionalLogicEvaluator

The submission pipeline evaluator already uses `logicIdToFieldName` populated from resolved weakrefs. It does not need `sourceNodeId` — the weakref is the source of truth at runtime.

## What This Solves

| Scenario | Before | After |
|---|---|---|
| Two sources with same name, initial creation | ❌ ambiguous | ✅ UUID distinguishes |
| Source field renamed after rule created | ✅ weakref | ✅ unchanged |
| Public runtime | ✅ weakref | ✅ unchanged |
| Duplication + unique names | ✅ name fallback | ✅ unchanged |
| Duplication + duplicate names | ❌ ambiguous | ❌ still ambiguous |

## Why Not `fieldKey`

The `fieldKey` / `sourceFieldKey` proposal would additionally solve the last row (duplication + duplicate names). However:

1. **Cost is high** — new JCR property on all fields, autocreation listener, migration of all existing fields, GraphQL schema change, full editor flow rewrite, duplication remapping logic
2. **The critical part is unspecified** — the duplication remapping strategy (regenerate keys + rewrite all `sourceFieldKey` references in the copied subtree) is left open in the spec
3. **The edge case is structurally rare** — requires fields with the same node name in different containers AND a duplication of that subtree
4. **No customer issue reported** — this is a theoretical fragility, not a demonstrated pain point

If the duplication + duplicate names case becomes a real customer issue, `fieldKey` can be introduced later with a complete duplication spec. The UUID-based approach does not preclude it — it is additive.

## Summary

Use the JCR UUID that is already available everywhere as the selection key in the editor and as the primary resolution criterion in the sync service. This is a localized change (editor + `syncSourceField`), requires no migration, no new data model, and solves the main problem (ambiguity at creation time).

