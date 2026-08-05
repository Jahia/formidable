# Specification — Form editing and results viewing permissions in Formidable

## 1. Context

Reference:

- Jahia Academy, "Enabling edit and view form permissions"  
  https://academy.jahia.com/documentation/forms/forms-3.3/end-user/managing-settings/enabling-edit-and-view-form-permissions

Goal: implement a permission model in Formidable similar to Jahia Forms, with a clear separation between:

- the right to edit a form;
- the right to view its submission results.

---

## 2. Functional summary from the Jahia documentation

The Jahia Forms documentation describes a simple and structured permission model:

- permissions are managed at the form level;
- there are at least two distinct capabilities:
  - editing the form;
  - viewing the form results;
- these rights can be granted to users and groups;
- a user allowed to edit a form does not necessarily have access to its results;
- a user allowed to view results can access the entire associated result set;
- a sharing link can be produced, but it does not bypass security: the recipient must already have viewing permission.

The key point is the clear separation between:

- the form definition content;
- the business content produced by submissions.

---

## 3. Current state in Formidable

### 3.1 Form model

The `fmdb:form` node type is defined in `formidable-elements/src/components/Form/definition.cnd`.

Today:

- `fmdb:form` inherits from `fmdbmix:component` which brings `jmix:accessControllableContent`;
- the form already carries functional mixins such as `fmdbmix:captcha` and `fmdbmix:requireAuthentication`.

### 3.2 Results model

The results storage types are defined in `formidable-engine/src/main/resources/META-INF/definitions.cnd`.

Current state:

- `fmdb:resultsFolder` is `jmix:accessControlled`;
- `fmdb:formResults` is now also `jmix:accessControlled`;
- `fmdb:formResults` references its form via the `parentForm` weakreference;
- submissions live under `submissions`, with auto-split by date.

### 3.3 Results creation

The results subtree is created in `SaveToJcrFormAction.java`:

- the site-level `formidable-results` node is created on demand;
- the `fmdb:formResults` node is created at the time of the first saved submission;
- results do not exist before the first entry.

### 3.4 Results viewing screen

The admin route is registered in `formidable-engine/src/javascript/init.tsx`.

- the screen already exists;
- actual security depends on JCR ACLs on the results subtree;
- GraphQL respects ACLs: inaccessible nodes are not returned in queries.

---

## 4. Chosen architecture

### 4.1 Fundamental principle

Use Jahia's native permissions UI (right-click → "Permissions" in jContent) as the **sole management interface**. No custom UI in Formidable.

The admin configures everything from the `fmdb:form` node. A propagation mechanism synchronises viewing rights toward `fmdb:formResults` in live.

### 4.2 Roles vs Permissions — Jahia recap

| Concept | Definition | Assignment |
|---------|-----------|------------|
| **Permission** | Atomic right (`jcr:read`, `jcr:write`, etc.) | Never assigned directly |
| **Role** | Named set of permissions | Assigned to a user/group on a node |

The Jahia UI allows: "Grant role X to user Y on this node".

### 4.3 Custom Formidable role

A single custom role declared by the module:

| Role | Context | Effect |
|------|---------|--------|
| `fmdb-results-reader` | Assigned on `fmdb:form` (LIVE permission) | Automatically propagated to `fmdb:formResults` → the user/group can view results |

Form editing uses native Jahia roles (`editor`, `contributor`). No custom role is needed for this capability.

The role is created programmatically at module activation by `FormResultsRoleInitializer`.

### 4.4 Management flow

```
1. Admin opens jContent → selects a fmdb:form → "Permissions"
2. In EDIT: uses native Jahia roles for editing (standard)
3. In LIVE: assigns the "fmdb-results-reader" role to a group
4. Admin publishes the form
5. A listener detects the publication (live workspace events)
6. The listener calls the ACL sync service
7. The service reads "fmdb-results-reader" assignments on the form (live)
8. The service replicates them on the corresponding fmdb:formResults (live)
9. JCR inheritance propagates to submissions, files, etc.
```

### 4.5 ACL synchronisation service

ACL synchronisation is handled by an **idempotent service** (`FormResultsAclSyncService`).

**Single responsibility**: read `fmdb-results-reader` ACEs from a `fmdb:form` in live, and replicate them on the corresponding `fmdb:formResults` in live.

**Idempotence**: the service can be called multiple times without side effects. It compares the current ACL state and writes only the differences.

**Primary trigger**: publication of the `fmdb:form`. This is the natural moment in the Jahia workflow: the admin modifies LIVE permissions in the EDIT workspace, then publishes to apply them. Publication validates the rights change.

**Secondary uses**: being idempotent and reusable, the service can also be called from:
- a migration/initialisation job (existing forms);
- a debug/repair endpoint (if needed).

**Important**: propagation is **unidirectional** (form → formResults). The admin never touches formResults ACLs directly.

### 4.6 `fmdb:formResults` lifecycle

The `fmdb:formResults` node is created only when needed, at the first submission (current behaviour via `SaveToJcrFormAction`).

At creation time, `SaveToJcrFormAction` calls the ACL sync service to immediately apply the rights published on the `fmdb:form`. This way, `formResults` is born with the correct ACLs from the start.

The publication listener never creates `formResults`. It synchronises ACLs only if the node already exists.

This avoids creating unnecessary administrative content (an empty `formResults`) just to set permissions on it.

---

## 5. Target content model

### CND

```cnd
// formidable-elements/src/components/Form/definition.cnd
// To verify: jmix:accessControllableContent (already present via fmdbmix:component)
// may suffice. If the Permissions UI does not appear, add jmix:accessControlled.
[fmdb:form] > jnt:content, fmdbmix:component, ...

// formidable-engine/src/main/resources/META-INF/definitions.cnd
[fmdb:formResults] > jnt:content, jmix:accessControlled
```

### Role

Created programmatically by `FormResultsRoleInitializer` at module activation:

- Node type: `jnt:role`
- Path: `/roles/fmdb-results-reader`
- Role group: `live-role`
- Permission: `jcr:read_live` via `jnt:externalPermissions`

---

## 6. Implementation

### Step 1 — CND prerequisites

- Verify whether `jmix:accessControllableContent` (already on `fmdb:form` via `fmdbmix:component`) is sufficient for jContent's Permissions UI. If not, add `jmix:accessControlled`.
- Add `jmix:accessControlled` to `fmdb:formResults`. ✅ Done.
- Create the `fmdb-results-reader` role programmatically. ✅ Done (`FormResultsRoleInitializer`).

### Step 2 — ACL sync service

Create `FormResultsAclSyncService` (static utility class, idempotent): ✅ Done.

- Input: a `fmdb:form` node (live).
- Reads ACEs carrying the `fmdb-results-reader` role.
- Resolves the corresponding `fmdb:formResults` (by scanning `formidable-results` children matching `parentForm`).
- Synchronises ACEs on `fmdb:formResults`: adds new ones, removes obsolete ones.

### Step 3 — Publication listener

`FormPublicationAclSyncListener` — a `DefaultEventListener` in the live workspace. ✅ Done.

Listens for:
- `NODE_ADDED` on `fmdb:form` — first publication
- `NODE_ADDED` / `PROPERTY_CHANGED` on `jnt:ace` — ACL changes under a form
- `NODE_REMOVED` on `jnt:ace` — ACL entry removed

Deduplicates calls per form identifier within a single event batch.

### Step 4 — Migration of existing forms

A resync job for forms already in production:

1. Scan all `fmdb:form` nodes across sites.
2. For each, call the ACL sync service.
3. Apply ACLs if `formResults` exists.

This job can be triggered manually (Groovy script) or automatically on first startup after module upgrade. **Not yet implemented.**

### Step 5 — Results route filtering

Nothing to do: GraphQL respects JCR ACLs. If a user does not have `jcr:read` on a `fmdb:formResults`, the node does not appear in `GET_FORM_RESULTS_LIST`. The results screen only shows accessible forms.

---

## 7. What was dropped from the initial version of this spec

| Dropped | Reason |
|---------|--------|
| Custom Permissions UI | Jahia's native UI is sufficient |
| `FormPermissionsService` with grant/revoke | Not needed — the admin uses the native UI |
| REST servlet `/permissions` | Not needed |
| "Permissions" panel in Content Editor | Not needed |
| `manageFormPermissions` as a separate capability | Handled by standard Jahia roles (admin) |
| `fmdb-editor` role | Form editing uses native Jahia roles (`editor`, `contributor`). No added value from a custom role. |

---

## 8. Caveats

1. **`jmix:accessControlled` vs `jmix:accessControllableContent`** — `fmdb:form` already inherits `jmix:accessControllableContent` via `fmdbmix:component`. **To test**: verify that jContent's "Permissions" UI appears with this mixin alone. Do not freeze the spec until this point is proven.

2. **Existing data migration** — Existing `fmdb:formResults` nodes will not have ACLs. The default behaviour (inheritance from `fmdb:resultsFolder` which is `jmix:accessControlled`) remains healthy. A resync job (step 4) is needed for production forms.

3. **Performance** — ACLs are set only on `fmdb:formResults`, not on each submission. JCR inheritance propagates to the subtree.

4. **Form deletion** — If the form is deleted, the `formResults` remains in live with its data and ACLs (ACLs are a copy, not a pointer). This is intentional: submission data is never destroyed automatically. Note: in Jahia, deleting published content goes through `jmix:markedForDeletion` (marking in EDIT) then unpublication — this is not a standard JCR `NODE_REMOVED`. The ACL listener will not be triggered, which is the expected behaviour. A cleanup mechanism for orphaned `formResults` can be added later if needed.

5. **`fmdb:resultsFolder` permissions** — This node is already `jmix:accessControlled`. It serves as the "default permission" for site admins.

6. **Two ACL sync moments** — ACLs are synchronised at two moments: (1) at form publication, if `formResults` already exists; (2) at `formResults` creation during the first submission. The sync service is the same in both cases.

7. **Publication = rights validation** — ACL propagation happens only at publication. If an admin modifies permissions without publishing, results are unaffected. This is consistent with the standard Jahia workflow: publication validates the change.

8. **`jmix:markedForDeletion` and unpublication** — In Jahia, deleting published content does not generate a `NODE_REMOVED` in live through standard means. Jahia sets the `jmix:markedForDeletion` mixin on the node in EDIT, then unpublication removes the node from live. This mechanism is distinct from standard JCR events. The `FormPublicationAclSyncListener` listens for `NODE_REMOVED` only for `jnt:ace` (removal of an individual ACE), not for form deletion.

9. **ACL inheritance is broken on `fmdb:formResults`** — Every `formResults` node has `j:inherit = false` on its `j:acl`. This blocks the site-level `reader` role from granting access. The sync service enforces this invariant on every call, even on existing ACLs, to guard against accidental restoration.

10. **Non-regression test scenarios** — See `tests/scenarios/permissions.md` for detailed test cases covering access control, deletion rights, and inheritance repair.

