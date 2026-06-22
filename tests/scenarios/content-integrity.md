# Content Integrity Checks for Formidable

## Coverage summary

| Coverage | Implementation | Status |
|----------|----------------|--------|
| Step 1 — Java custom checks + module wiring | `jahia-test-module/formidable-test-module-samples-java`, `tests/cypress/support/constants.ts` | ✓ |
| Step 2 — Cypress scan helper | `tests/cypress/support/contentIntegrity.ts` | ✓ |
| Step 3 — Narrow subtree scans | form/results scoped scans in all integrity specs | ✓ |
| Step 4 — Scan in existing conditional-logic suite | `tests/cypress/e2e/logics/51-conditional-logic-copy-paste.cy.ts`, `52-conditional-logic-backend.cy.ts`, `53-conditional-logic-import.cy.ts` | ✓ |
| Step 5 — Full form + results/submissions green-path | `tests/cypress/e2e/integrity/60-content-integrity-full-form.cy.ts` | ✓ |
| Step 6 — Deletion coverage | `tests/cypress/e2e/integrity/64-content-integrity-clean-deletion.cy.ts` | ✓ |
| Step 7 — Targeted negative checks | `61`, `62`, `63`, `66` in `tests/cypress/e2e/integrity/` | ✓ |
| Step 8 — Semantic checks on saved payload | `65-content-integrity-submission-payload-semantic.cy.ts`, `FormSubmissionPayloadIntegrityCheck.java` | ✓ |
| Step 9 — Promote out of `todo/` | this document + scenario indexes | ✓ |

## Goal

Use Jahia's `content-integrity` module to validate Formidable-specific JCR invariants after authoring, submission, copy/paste, import/export, and deletion operations.

This document started as a design note and is now implemented as active regression coverage. It relies on:
- Formidable's current CND and runtime behavior in this repository
- the deployed `content-integrity` module surfaces available in the local test stack

Status at the time of writing:
- `content-integrity` is provisioned in the Cypress test stack via `tests/provisioning-manifest-build.yml` and `tests/provisioning-manifest-snapshot.yml`
- the test Java module contains custom Formidable checks under `jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/integrity/`
- the samples Java module is enabled on the test site through `FORMIDABLE_MODULE_IDS`
- integrity scans run from Cypress through the GraphQL API exposed by `content-integrity`

The module also exposes a GraphQL surface in the deployed artifact. For automation, that structured API is preferable to parsing human-readable Karaf output.

## What the module already gives us

Before adding any Formidable-specific check, confirm what the stock module already catches.

Useful embedded checks include:
- `ReferencesSanityCheck` for broken JCR references
- `ChildNodeDefinitionsSanityCheck` for children disallowed by CND
- `PropertyDefinitionsSanityCheck` for invalid properties
- `UndeclaredNodeTypesCheck` and `UndeployedModulesReferencesCheck` for missing node types or undeployed modules

Those generic checks stay enabled. Formidable custom checks only cover invariants that the stock module cannot express.

## Active regression coverage

### Existing specs enriched with integrity scans

| Spec | What is asserted |
|------|------------------|
| `tests/cypress/e2e/logics/51-conditional-logic-copy-paste.cy.ts` | full-form copy stays clean, single-field copy degrades cleanly |
| `tests/cypress/e2e/logics/52-conditional-logic-backend.cy.ts` | backend logic persistence stays clean |
| `tests/cypress/e2e/logics/53-conditional-logic-import.cy.ts` | imported logic rebinding stays clean |

### Dedicated integrity specs

| Spec | Intent |
|------|--------|
| `60-content-integrity-full-form.cy.ts` | green-path full form with many element types + `save2jcrAction`, then live results scan |
| `61-content-integrity-negative-detection.cy.ts` | detect missing `submissions` under `fmdb:formResults` |
| `62-content-integrity-conditional-logic-negative.cy.ts` | detect missing `logicsSrc/<logicId>` for an existing logic rule |
| `63-content-integrity-submission-deletion.cy.ts` | detect missing `data` under `fmdb:formSubmission` |
| `64-content-integrity-clean-deletion.cy.ts` | verify clean deletion leaves no residual violation |
| `65-content-integrity-submission-payload-semantic.cy.ts` | detect undeclared payload keys and undeclared file folders |
| `66-content-integrity-reference-targets.cy.ts` | detect wrong-type `fmdb:formReference/j:node` and wrong-type `fmdb:formResults/parentForm` |

## Formidable-specific invariants covered

### Reference integrity

| Check | Covered by |
|-------|------------|
| `fmdb:formReference/j:node` resolves to an existing `fmdb:form` | `66-content-integrity-reference-targets.cy.ts` |
| `fmdb:logicSrc/logicNodeSource` resolves to an existing in-scope source field | `51`, `52`, `53`, `62` |
| `fmdb:formResults/parentForm` resolves to an existing `fmdb:form` | `60`, `66` |

### Structure integrity

| Check | Covered by |
|-------|------------|
| Every `fmdb:formSubmission` has a `data` child of type `fmdb:submissionData` | `60`, `63` |
| Every `fmdb:formResults` has a `submissions` child | `60`, `61` |
| Every `logics` JSON entry has a matching `logicsSrc/<logicId>` child | `51`, `52`, `53`, `62` |
| No orphan `logicsSrc/<logicId>` child exists without a matching JSON rule | Java check present; not isolated in a dedicated Cypress corruption case |

### Semantic integrity

| Check | Covered by |
|-------|------------|
| `logicNodeSource` stays within the same copied/imported form subtree when that is expected | `51`, `53` |
| A copied single field with out-of-scope logic refs is degraded cleanly | `51` |
| Submission `files/` subfolders correspond to declared file fields only | `65` |
| Submission `data` properties do not contain unexpected field names | `65` |

## Important constraints from the local CND

These constraints shape the checks and avoid false positives:

- `fmdb:form/actions` is an autocreated child node of type `fmdb:actionList`; it is not a weakref container.
- `fmdb:formSubmission/data` is `autocreated`, so missing `data` indicates corruption, import damage, or invalid manual manipulation.
- `fmdb:formResults/submissions` is `autocreated`.
- `fmdb:submissions` explicitly allows direct `fmdb:formSubmission` children as a resilience fallback, in addition to split folders. A check must not require date-split subfolders to exist.
- `logicsSrc` exists as a hidden child node in the model, but runtime behavior and existing tests expect it to be meaningful only when logic rules exist.

## Automation approach

### Java side

Preferred location:

`jahia-test-module/formidable-test-module-samples-java/`

Reason:
- keeps the checks out of production runtime
- co-locates them with existing Formidable test support code
- allows the checks to evolve with test scenarios

Current status:
- done
- the checks compile against the deployed module version `3.34`

### Cypress side

Implementation choice:
- use the `content-integrity` GraphQL API from Cypress
- keep Karaf commands useful for manual debugging only

Reason:
- GraphQL returns structured data that can be asserted directly
- Karaf output is human-oriented and fragile to parse

Current helper:
- `tests/cypress/support/contentIntegrity.ts`
- responsibilities:
  - start a content-integrity scan
  - load enabled checks when `checksToRun` is not provided
  - poll until completion
  - treat scans without `resultsID` as clean only when logs explicitly confirm `No error found`
  - load detailed results only when violations exist

## Remaining limits

What this suite proves well today:
- normal Formidable authoring and submission scenarios stay clean under `content-integrity`
- custom checks detect targeted corruption on the main Formidable-specific invariants
- deletion can be validated both as a clean path and as a corruption-detection path

What it still does not isolate with a dedicated Cypress case:
- orphan `logicsSrc` child without matching JSON rule
- deleted-target weakref cases specifically, as opposed to wrong-type target cases

Those are lower priority than the current active coverage because the main branches are already exercised.
