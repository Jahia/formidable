# Lessons Learned

Mistakes made during the weakref model implementation. Do not repeat.

## Jahia API

- **`DefaultEventListener` has no `setNodeTypes()`**. Override `getNodeTypes()` instead.
- **`DefaultEventListener` has no `getSessionFactory()`**. Use `JCRSessionFactory.getInstance()` or `JCRTemplate.getInstance()`.
- **`doExecuteWithSystemSession(String, String, JCRCallback)` is deprecated**. Use `doExecuteWithSystemSessionAsUser(JahiaUser, String, Locale, JCRCallback)` with `null` user for system session.
- **`setOperationTypes()` takes `Set<Integer>`**, not `int[]`. Use `Set.of(Integer.valueOf(...))` when passing `int` constants.
- **`JCRNodeWrapper.getAncestors()` returns `List<JCRItemWrapper>`**, not `List<JCRNodeWrapper>`. Use `instanceof JCRNodeWrapper` pattern matching to cast.
- **`JCRSessionWrapper.getNode()` already returns `JCRNodeWrapper`**. Do not redundantly cast.
- **Do not invent JCR paths for Jahia permissions**. The path `/permissions/repository-permissions/jcr:read_live` does not exist. When creating a `jnt:role`, set `j:roleGroup` to `live-role` and let Jahia's role resolution handle derived permissions. Do not add `jnt:externalPermissions` child nodes referencing permission paths that have not been verified to exist in the target Jahia version.
- **Prefer `src/main/import/roles.xml` for static Jahia roles, but only after verifying the exact runtime shape of the imported role**. XML import is usually clearer than a Java initializer for fixed roles. However, if the role must live under a specific parent such as `/roles/reader/...` to inherit behavior, do not assume the import format will reproduce that correctly. Validate the final JCR path, role group, UI visibility, and effective permissions in a running Jahia before replacing a programmatic role initializer.

## CND constraints matter at code level

- **`protected` properties cannot be written by a contributor session**. If a listener writes a `protected` property, it must use a system session (`doExecuteWithSystemSessionAsUser`), not `getCurrentUserSession`.
- **`mandatory` means the property always exists on a persisted node**. Do not add `hasProperty()` checks for mandatory properties — it is dead code.
- **`autocreated` means the child node always exists**. Do not add `hasNode()` checks or `addNode()` fallbacks for autocreated children — it is dead code. This applies to ALL autocreated nodes across ALL CND files (`logicsSrc`, `fields`, `actions`, etc.), not just the ones in the file being edited. Always check the CND before writing a `hasNode()` guard.

## General coding

- **Do not store UUIDs in a map then re-lookup the node by UUID**. If the node is already available and the session is the same, store the node directly.
- **Do not wrap a single `getNode()` call in a private method**. Inline it.
- **Do not create trivial wrapper methods** (e.g. `safeNodePath(node)` that just calls `node.getPath()`). If the wrapper adds no error handling, no transformation, and no readability benefit, it is dead weight. Call the underlying method directly.
- **Do not keep entries in a list while skipping their tracking in a companion set**. If an entry is skipped from one structure, it must be consistently skipped from all related structures.
- **Verify every file after generating it**. Do not assume generated code compiles. Check the actual Jahia API signatures before using them.
- **When asked to review code, check ALL CND files for autocreated/mandatory constraints**. Dead code from ignoring CND constraints was missed on multiple review passes. Cross-reference every `hasNode()` and `hasProperty()` with the CND definitions before validating.
- **Do not add `nodeExists()` before `getNode()` in event listeners**. In Jahia, events are processed in the same transaction — the node exists. If it somehow doesn't, let `getNode()` throw and let the catch handle it.
- **Think about all copy/paste scopes**. A listener filtering on `fmdb:form` only catches whole-form duplication. Copying a single field between forms requires also filtering on `fmdbmix:formLogicElement`.
- **Always resolve node references by ID, never by stored name**. Names in JSON can become stale after rename/copy/import. If a weakref or UUID exists, use it to get the current node name at read time. This applies to both server-side (`ConditionalLogicEvaluator`) and client-side (`ConditionalLogicCmp`) resolution.
- **Do not use `autocreated` for rarely-needed child nodes**. If a child node is only needed when a specific feature is used (e.g. `logicsSrc` only when conditional logic is configured), do not mark it `autocreated` — it creates unnecessary nodes on every instance. Instead, create the node lazily (on first use) and add `hasNode()` guards before reading.
- **Treat every SPI boundary as immutable by default**. If validated data is passed to multiple actions/extensions, do not expose live mutable state. Defensively copy `byte[]` on construction and access, and pass unmodifiable collections across the SPI boundary, otherwise one handler can silently change what later handlers store, attach, or forward.
- **Promote repeated JCR property/child names to constants once they appear multiple times in the same class**. Strings like `parentForm` or `files` are schema-level identifiers; duplicating them makes reviews noisier and copy/paste mistakes easier.

## SonarQube

- **Do not use `log + throw` on the same error path unless both are explicitly needed**. Prefer one final logging point and propagate the original cause with contextual exceptions.
- **Never swallow `InterruptedException` in a broad `catch (Exception)`**. Catch it explicitly, call `Thread.currentThread().interrupt()`, then return or rethrow with context.
- **Do not log user-controlled data by default**. IDs, request parameters, and similar input-derived values must be treated as tainted unless there is a specific operational need to log them.
- **Guard expensive or non-trivial log arguments behind the log level**. Cache computed values and use `isDebugEnabled()` / `isWarnEnabled()` / `isErrorEnabled()` when Sonar flags method calls inside logging statements.
- **Replace generic exceptions with specific ones or remove fake failure paths entirely**. `throws Exception`, `throw new RuntimeException(e)`, and impossible `catch` blocks are common Sonar hits.
- **Remove redundant casts aggressively, especially with Jahia APIs that already return `JCRNodeWrapper`**. Sonar will keep finding them if code is copied between listeners, actions, and collectors.
- **Records containing `byte[]` need explicit `equals`, `hashCode`, and `toString`**. The generated methods compare array identity, not contents.
- **Prefer immutable/final holders over mutable servlet or service fields when Sonar flags thread-safety**. `volatile` alone is often not enough for shared component state.
- **Flatten loops that stack multiple `continue` or `break` statements**. Rewrite them around positive conditions so Sonar does not flag flow complexity.
- **Avoid hard-coded path delimiters and similar schema separators**. Even for JCR paths, Sonar prefers a named constant over `"/"` embedded in concatenation.
