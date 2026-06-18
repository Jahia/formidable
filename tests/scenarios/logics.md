# Conditional Logic – Active Coverage

This document describes the conditional logic scenarios covered by the automated test suite.

## Covered by Cypress specs

| Spec | Section | Description |
|------|---------|-------------|
| `50-conditional-logic-selector-type.cy.ts` | 7 | Content Editor selectorType UI (source filtering, operator switching, date inputs, exclusion of used sources, reload) |
| `51-conditional-logic-copy-paste.cy.ts` | 3 | Form duplication via GraphQL `copyNode` (weakref rebinding, duplicate source names, single-field degradation) |
| `52-conditional-logic-backend.cy.ts` | 1, 2 | Backend synchronization (logicsSrc creation/update/removal, rename resilience, logicId/sourceNodeId persistence) |
| `53-conditional-logic-import.cy.ts` | 4.1 | Import via GraphQL `importContent` (weakref rebinding to imported nodes, duplicate source names) |

## Covered by Java unit tests

Location: `formidable-engine/src/test/java/org/jahia/modules/formidable/engine/logic/`

- `ConditionalLogicRule.parse` — rule parsing and normalization
- `ConditionalLogicEvaluator` — all operators (in/notIn, isChecked/isUnchecked, containsAny/containsAll, before/after/on/between), transitive visibility, AND logic
- `FormLogicSyncService` — logicId normalization, sourceNodeId normalization, weakref preservation, duplicate-parent visibility, degraded fallback to sourceFieldName

## Test fixture (Cypress)

Fields created for conditional logic tests:
1. `role` — `fmdb:select` with choices: `admin`, `editor`, `viewer`
2. `accept-terms` — `fmdb:checkbox` (single) with choice: `accepted` / `I accept`
3. `start-date` — `fmdb:inputDate`
4. `notes` — `fmdb:textarea`
5. `nickname` — `fmdb:inputText`

## Coverage boundary

- The real jContent clipboard UI flow (copy/paste from the UI toolbar) is **not** covered — only GraphQL `copyNode`.
- Frontend runtime visibility (show/hide in preview/live) is **not** covered by Cypress yet.
- Server-side submission with conditional logic (hidden fields skip validation) is **not** covered by Cypress yet — only by Java unit tests via `ConditionalLogicEvaluator`.

## CI pickup

- Java tests: `mvn clean install`
- Cypress specs: picked up automatically by the integration test job

