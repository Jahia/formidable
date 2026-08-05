# `fmdb-results-reader` Role: XML Import Proposal

## Problem

The current implementation creates the `fmdb-results-reader` role programmatically in
`FormResultsRoleInitializer`.

That works, but it has two drawbacks:

1. The role definition is not declarative.
2. It is harder to compare with Jahia conventions used by other modules such as
   `forms-core`, which declares roles through `src/main/import/roles.xml`.

The question is whether `fmdb-results-reader` should be moved from a Java initializer
to a standard Jahia XML role import.

## Why this is not a trivial switch

In Formidable, `fmdb-results-reader` is currently created under:

```text
/roles/reader/fmdb-results-reader
```

This is intentional. The role is modeled as a child of Jahia's built-in `reader`
role so it inherits read semantics without inventing low-level permission paths.

Moving to XML is only acceptable if the imported role preserves the effective runtime
behavior:

- same functional visibility in Jahia Permissions UI
- same effective read access on `fmdb:formResults`
- no accidental broadening of permissions
- no dependency on unverified permission paths

## Constraints to preserve

Any XML-based replacement must preserve these invariants:

1. The role remains read-only.
2. The role is usable in the LIVE permissions section.
3. ACL propagation logic in `FormResultsAclSyncService` continues to work unchanged.
4. The role does not rely on guessed repository-permission node paths.
5. The resulting role is visible and assignable from jContent Permissions.

## Candidate XML spec to test

Create:

```text
formidable-engine/src/main/import/roles.xml
```

Proposed content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<roles jcr:primaryType="jnt:roles"
       xmlns:jcr="http://www.jcp.org/jcr/1.0"
       xmlns:j="http://www.jahia.org/jahia/1.0"
       xmlns:jnt="http://www.jahia.org/jahia/nt/1.0">
    <reader jcr:primaryType="jnt:role">
        <fmdb-results-reader j:hidden="false"
                             j:nodeTypes="jnt:virtualsite"
                             j:permissionNames=""
                             j:privilegedAccess="false"
                             j:roleGroup="live-role"
                             jcr:primaryType="jnt:role">
            <j:translation_en jcr:language="en"
                              jcr:primaryType="jnt:translation"
                              jcr:title="Form Results Reader"/>
            <j:translation_fr jcr:language="fr"
                              jcr:primaryType="jnt:translation"
                              jcr:title="Lecteur des résultats de formulaire"/>
        </fmdb-results-reader>
    </reader>
</roles>
```

## Important note on the candidate spec

The XML above is a **proposal to validate**, not an accepted final definition.

The risky part is this assumption:

- nesting `fmdb-results-reader` under `<reader>` in `roles.xml` will reproduce the
  same runtime role shape and inheritance as creating `/roles/reader/fmdb-results-reader`
  programmatically

That assumption must be verified in a running Jahia.

## Validation checklist

After deploying the XML version, verify all of the following:

1. The role exists at the expected JCR path.
2. The role appears in jContent Permissions.
3. The role is assignable on `fmdb:form` in LIVE.
4. Publishing the form still propagates ACEs to `fmdb:formResults`.
5. A user with only `fmdb-results-reader` can read results.
6. The same user cannot delete results.
7. No unexpected read access is granted outside the intended results flow.

## Migration strategy

If the XML import passes validation:

1. Add `src/main/import/roles.xml`.
2. Remove `FormResultsRoleInitializer`.
3. Update `docs/results-permissions.md` to state that the role is imported, not
   created programmatically.
4. Re-run the manual permissions scenarios.

If the XML import does **not** preserve the expected role shape or behavior:

- keep the Java initializer
- document that this role must remain programmatic because inheritance under
  `/roles/reader` is part of the contract

## Recommended decision rule

Use XML if and only if it reproduces the current runtime role semantics exactly.

If there is any doubt about inherited behavior, path shape, or effective permissions,
the current programmatic initializer is the safer implementation.
