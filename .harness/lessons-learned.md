# Lessons Learned

Mistakes made during the weakref model implementation. Do not repeat.

## Jahia API

- **`DefaultEventListener` has no `setNodeTypes()`**. Override `getNodeTypes()` instead.
- **`DefaultEventListener` has no `getSessionFactory()`**. Use `JCRSessionFactory.getInstance()` or `JCRTemplate.getInstance()`.
- **`doExecuteWithSystemSession(String, String, JCRCallback)` is deprecated**. Use `doExecuteWithSystemSessionAsUser(JahiaUser, String, Locale, JCRCallback)` with `null` user for system session.
- **`setOperationTypes()` takes `Set<Integer>`**, not `int[]`. Use `Set.of(Integer.valueOf(...))` when passing `int` constants.
- **`JCRNodeWrapper.getAncestors()` returns `List<JCRItemWrapper>`**, not `List<JCRNodeWrapper>`. Use `instanceof JCRNodeWrapper` pattern matching to cast.
- **`JCRSessionWrapper.getNode()` already returns `JCRNodeWrapper`**. Do not redundantly cast.

## CND constraints matter at code level

- **`protected` properties cannot be written by a contributor session**. If a listener writes a `protected` property, it must use a system session (`doExecuteWithSystemSessionAsUser`), not `getCurrentUserSession`.
- **`mandatory` means the property always exists on a persisted node**. Do not add `hasProperty()` checks for mandatory properties — it is dead code.
- **`autocreated` means the child node always exists**. Do not add `hasNode()` checks or `addNode()` fallbacks for autocreated children — it is dead code. This applies to ALL autocreated nodes across ALL CND files (`logicsSrc`, `fields`, `actions`, etc.), not just the ones in the file being edited. Always check the CND before writing a `hasNode()` guard.

## General coding

- **Do not store UUIDs in a map then re-lookup the node by UUID**. If the node is already available and the session is the same, store the node directly.
- **Do not wrap a single `getNode()` call in a private method**. Inline it.
- **Do not keep entries in a list while skipping their tracking in a companion set**. If an entry is skipped from one structure, it must be consistently skipped from all related structures.
- **Verify every file after generating it**. Do not assume generated code compiles. Check the actual Jahia API signatures before using them.
- **When asked to review code, check ALL CND files for autocreated/mandatory constraints**. Dead code from ignoring CND constraints was missed on multiple review passes. Cross-reference every `hasNode()` and `hasProperty()` with the CND definitions before validating.
- **Do not add `nodeExists()` before `getNode()` in event listeners**. In Jahia, events are processed in the same transaction — the node exists. If it somehow doesn't, let `getNode()` throw and let the catch handle it.
- **Think about all copy/paste scopes**. A listener filtering on `fmdb:form` only catches whole-form duplication. Copying a single field between forms requires also filtering on `fmdbmix:formLogicElement`.

