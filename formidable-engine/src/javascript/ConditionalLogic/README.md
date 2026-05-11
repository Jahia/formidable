# Conditional Logic Editor

This folder contains the custom Content Editor selector used to configure conditional field visibility for Formidable fields carrying the `fmdbmix:formLogicElement` mixin.

## Goal

The editor exposes one logical rule per multivalue entry of the `logics` property.

Each rule defines:

- the source field
- the comparison operator
- the comparison value or values

At runtime, Formidable evaluates all stored rules on the target field with `AND` semantics.

## Files

### `ConditionalLogicCmp.tsx`

This is the UI component registered as the `ConditionalLogic` selector type in `formidable-engine/src/javascript/init.tsx`.

Its responsibilities are intentionally narrow:

- load candidate source fields for the current edited node
- render the editor controls
- react to user changes
- serialize the edited rule back to JSON through `onChange`

The component uses Moonstone controls:

- `Dropdown` for source selection
- `Dropdown` for operator selection
- `Dropdown` for multivalue choice selection
- `Input type="date"` for date comparisons

The component does not implement tree traversal, parsing, or rule normalization directly. That logic lives in `ConditionalLogic.utils.ts`.

### `ConditionalLogic.types.ts`

This file centralizes the TypeScript types used by the feature:

- selector props
- GraphQL node shapes
- source field description
- logical rule structure
- supported operators and supported source field types

The purpose is to keep `ConditionalLogicCmp.tsx` readable and avoid repeating ad hoc inline types.

### `ConditionalLogic.utils.ts`

This file contains the non-visual logic:

- GraphQL queries used to resolve the current edited node and the parent form tree (using the shared `JCR_NODE_IDENTITY` fragment from `src/javascript/graphql/`)
- parsing of the stored JSON rule
- extraction of editor context values such as path, language, and workspace
- discovery of valid source fields located before the current target field
- mapping of source field types to supported operators
- normalization of the stored JSON payload

This separation matters because the selector has two different concerns:

1. editor rendering
2. form-structure analysis

Keeping them together made the main component harder to read and harder to evolve.

## Data flow

1. The editor opens on a field with the `logics` property.
2. `ConditionalLogicCmp.tsx` resolves the current node path from the selector context.
3. It queries the current node, then walks up to find the parent `fmdb:form`.
4. It queries the form tree under `fields`.
5. It flattens the form structure in display order.
6. It keeps only supported source field types that appear before the current field.
7. It excludes source fields already used by sibling `logics` entries (read from `formik.values`).
8. If no source fields are available (none exist or all are taken), a text message is shown instead of the dropdowns.
9. The user selects:
   - a source field
   - an operator
   - zero, one, or multiple values depending on the source type
10. The selector serializes the rule as JSON and writes it back through `onChange`.

## Supported source field types in V1

- `fmdb:select`
- `fmdb:radio`
- `fmdb:checkbox`
- `fmdb:inputDate`

## Supported operators in V1

### Select and radio

- `in`
- `notIn`

### Checkbox

Single checkbox:

- `isChecked`
- `isUnchecked`

Checkbox group:

- `containsAny`
- `containsAll`

### Date

- `before`
- `after`
- `on`
- `between`

## Stored rule format

Each `logics` entry is stored as JSON. Example:

```json
{
  "sourceFieldId": "4028c1e2934f2f9201934f6ac4f00041",
  "sourceFieldName": "iAm",
  "sourceFieldType": "fmdb:radio",
  "operator": "in",
  "values": ["individual", "professional"]
}
```

Date example:

```json
{
  "sourceFieldId": "4028c1e2934f2f9201934f6ac4f00077",
  "sourceFieldName": "startDate",
  "sourceFieldType": "fmdb:inputDate",
  "operator": "between",
  "values": ["2026-01-01", "2026-12-31"]
}
```

## Why the source-field lookup is done in TSX

This implementation intentionally keeps the source discovery logic in the editor layer instead of using a Jahia Java choicelist initializer.

Reason:

- the selector needs dynamic behavior driven by the current edited field
- the available operators depend on the chosen source type
- the value control depends on the chosen source field content
- keeping all of that close to the selector avoids splitting the feature across unrelated stacks

## Runtime counterpart

This folder only covers the Content Editor side.

The runtime visibility evaluation is implemented in `formidable-elements`:

- server wrappers add field metadata and serialized logics
- `Form.client.tsx` listens to form changes
- `conditionalLogic.ts` evaluates the rules and hides or shows the target wrappers

## Maintenance note

If new source field types are added later, update the following places together:

1. supported source types in `ConditionalLogic.types.ts` / `ConditionalLogic.utils.ts`
2. operator mapping in `ConditionalLogic.utils.ts`
3. value editor behavior in `ConditionalLogicCmp.tsx`
4. runtime evaluation in `formidable-elements/src/utils/conditionalLogic.ts`
