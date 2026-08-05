# Current Conditional Logic Lifecycle

This note describes how conditional logic works **today** in Formidable:

- when a rule is created
- when a rule is edited
- when a source field is renamed
- when the form is rendered
- when content is copied, pasted, or imported

It intentionally describes the **current model**, not the proposed `fieldKey` / `sourceFieldKey` design.

## 1. Rule creation

When a contributor creates a conditional logic rule in the Content Editor:

1. the React editor creates a `logicId`
2. the rule is stored as JSON inside the target field's `logics` property
3. the JSON mainly contains:
   - `logicId`
   - `sourceFieldName`
   - `sourceFieldType`
   - `operator`
   - `value` or `values`

### Simplified shape

```text
Target field
└── JCR property: logics
    └── [
          {
            logicId: "rfsyyuu0",
            sourceFieldName: "select-an-option",
            sourceFieldType: "fmdb:select",
            operator: "in",
            values: ["care-coordinator"]
          }
        ]
```

After the `logics` property is saved, the Java sync listener runs and synchronizes the technical weakref structure.

It creates:

```text
Target field
└── logicsSrc
    └── rfsyyuu0
        └── logicNodeSource --> weakref to the actual source field node
```

### Meaning

- the JSON stores the business definition of the rule
- `logicsSrc/{logicId}` stores the technical link to the actual source node

## 2. Rule editing

When the contributor edits an existing rule:

- operator may change
- values may change
- source field may change

In all of these cases, the current model generally keeps the same `logicId`.

Then:

1. the JSON is rewritten
2. the sync runs again
3. `logicsSrc/{logicId}` is updated if needed

### Simplified view

```text
Same rule
logicId = "rfsyyuu0"

Before:
rfsyyuu0 -> source A

After edit:
rfsyyuu0 -> source B
```

### Meaning

`logicId` identifies the **rule**, not the source field.

## 3. Source field rename

If the source field is renamed after the rule already exists:

- the JSON may still contain the old `sourceFieldName`
- but `logicsSrc/{logicId}/logicNodeSource` still points to the correct source node

### Simplified view

```text
Rule JSON
logicId = rfsyyuu0
sourceFieldName = "old-name"

logicsSrc/rfsyyuu0
└── logicNodeSource --> actual renamed node "new-name"
```

### Meaning

This is one of the main reasons why `logicId` exists:

- the rule keeps a stable identity
- the weakref keeps the real node link stable across renames

## 4. Where the current model is weak

The weak point is the **initial creation** of the rule.

Before the weakref becomes the reliable source of truth, the backend still has to resolve the source field from the JSON payload.

Today, that payload mainly carries:

- `logicId`
- `sourceFieldName`

So if the form contains two different source fields with the same visible label, or even the same technical name in different branches, the first sync can bind the rule to the wrong source.

### Example

```text
Termination
└── select-an-option

Reduction
└── select-an-option
```

And the rule initially says only:

```text
sourceFieldName = "select-an-option"
```

At that moment, the backend may resolve the wrong source node when creating the weakref.

## 5. Form rendering

When the public form is rendered:

1. the server reads the logic JSON
2. it reads `logicsSrc/{logicId}`
3. it resolves the actual source node through the weakref
4. it injects the source node identity into the rendered output
5. the browser runtime uses that metadata to show or hide fields

### Simplified flow

```text
logics JSON + logicsSrc weakref
        ↓
server render
        ↓
HTML with logic metadata
        ↓
browser runtime
        ↓
show / hide fields
```

### Meaning

At runtime, the system already relies on the resolved weakref, not only on the source field name.

## 6. Copy / paste / import

When a form subtree is copied, pasted, or imported:

- new JCR nodes are created
- JCR UUIDs may change
- existing weakrefs may become broken
- or weakrefs may point outside the copied subtree

To handle that, the module already has a duplication/import cleanup listener.

The listener runs on:

- `IMPORT`
- `WORKSPACE_COPY`

Its job is roughly:

1. find the copied/imported form or logic-aware node
2. inspect `logicsSrc`
3. remove broken or out-of-scope weakrefs
4. resynchronize the logic again from the JSON payload

### Simplified flow

```text
Copy / import
    ↓
new nodes created
    ↓
old weakrefs may be broken
    ↓
cleanup listener
    ↓
rebuild from sourceFieldName
```

## 7. Why copy/import is fragile today

The rebuild step after duplication/import still depends on `sourceFieldName`.

That means:

- if field names are unique, rebuild can work
- if duplicate names exist, rebuild can become ambiguous

So the weakness is not `logicId` itself.

The weakness is that the source field does not yet have its own stable business identity in the stored JSON.

## 8. Summary

Today the lifecycle is:

```text
Create
React -> generates logicId + JSON with sourceFieldName
      -> Java sync creates logicsSrc/{logicId} -> weakref

Edit
Same logicId
JSON updated
weakref updated

Rename source field
logicId unchanged
weakref still points to the correct node

Render
server resolves weakref
browser uses resolved source metadata

Copy / paste / import
weakrefs may break
cleanup listener rebuilds links
rebuild still depends on sourceFieldName
=> ambiguous if duplicate names exist
```

## Final takeaway

In the current model:

- `logicId` is useful and correctly identifies the rule
- `logicsSrc/{logicId}` is the technical bridge between the rule JSON and the real source node
- the fragile part is the source identification during initial binding and rebuild after duplication/import

That is the exact gap the proposed `fieldKey` / `sourceFieldKey` design is meant to close.
