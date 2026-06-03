# How to Extend Formidable Views and Add New Form Elements from a Third-Party Module

This guide explains how to extend Formidable rendering from another Jahia module.

It covers two different cases:

1. adding a new view for an existing Formidable node type
2. adding a new custom form element type

These two cases do not follow the same rendering contract.

## Module dependencies

A third-party module that extends Formidable rendering should depend on:

- `formidable-elements`
- `formidable-engine`

Reason:

- `formidable-elements` provides the rendered node types and default views
- `formidable-engine` owns runtime mixins such as `fmdbmix:formElement` and `fmdbmix:formContainer`

In practice, external CND definitions often reuse both layers, directly or indirectly.

## Two rendering contracts

There are two important categories of rendered nodes:

### 1. `fmdbmix:formContainer`

A form container is a node that renders child form nodes.

Current examples in Formidable:

- `fmdb:fieldList`
- `fmdb:fieldset`
- `fmdb:step`

Containers must not render their children with a plain manual loop unless they deliberately reimplement Formidable's logic contract.

They should delegate child rendering to the built-in `hidden.logic` view on `fmdbmix:formContainer`.

### 2. `fmdbmix:element`

A form element is a leaf field such as a text input, checkbox, select, or textarea.

Leaf elements do not render child nodes, so they do not need the `hidden.logic` indirection.

They can render their own HTML directly.

## Why containers must use `hidden.logic`

Formidable executes conditional visibility logic at render boundaries, not inside every individual container implementation.

The shared container view:

- wraps each child with `LogicAwareRender`
- serializes logic metadata into `data-fmdb-*` attributes
- applies initial hidden state when a node has logic attached
- preserves explicit `j:view` overrides on child nodes
- applies step-specific fallback behavior for multi-step forms
- hides later steps on first render when the form uses step navigation

That is why `fmdb:form` itself now delegates the rendering of its `fields` child node through `hidden.logic`, and why `fmdb:fieldset` and `fmdb:step` do the same.

If an external container bypasses this shared view, the usual regressions are:

- conditional logic does not hide the child field on first render
- step fallback view resolution is lost
- multi-step initial visibility becomes inconsistent
- child-level `j:view` overrides are easier to break accidentally

## Case 1: add a new view for an existing container

Example: add a `twoColumns` view for `fmdb:fieldset`.

The key point is this:

- your custom container view can control layout
- but child rendering should still go through `hidden.logic`

Example:

```tsx
import {
  AddResources,
  buildModuleFileUrl,
  jahiaComponent,
  Render,
} from "@jahia/javascript-modules-library";
import classes from "./twoColumns.module.css";

interface FieldsetProps {
  "jcr:title"?: string;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "fmdb:fieldset",
    name: "twoColumns",
    displayName: "Fieldset - Two columns",
  },
  ({ "jcr:title": title }: FieldsetProps, { currentNode }) => (
    <>
      <AddResources type="css" resources={buildModuleFileUrl("dist/assets/style.css")} />
      <fieldset>
        {title && <legend>{title}</legend>}

        <Render
          node={currentNode}
          view="hidden.logic"
          parameters={{
            className: classes.grid,
            childClassName: classes.item,
          }}
        />
      </fieldset>
    </>
  ),
);
```

What this does:

- your view owns the wrapper markup and layout
- `hidden.logic` still owns the rendering contract for child nodes

## Case 2: add a new custom container type

If your module introduces a new container type, that type should opt into the same contract by using `fmdbmix:formContainer`.

Example CND:

```cnd
<jnt = 'http://www.jahia.org/jahia/nt/1.0'>
<fmdbmix = 'http://www.jahia.org/jahia/fmdb/mix/1.0'>
<mymod = 'http://www.example.com/jahia/mymod/nt/1.0'>

[mymod:panel] > jnt:content, fmdbmix:formElement, fmdbmix:formContainer, fmdbmix:nonSubmittable
 + * (fmdbmix:element) = fmdbmix:element
 + * (fmdbmix:formContent) = fmdbmix:formContent
```

Its view should follow the same pattern as `fieldset`:

```tsx
import { jahiaComponent, Render } from "@jahia/javascript-modules-library";

jahiaComponent(
  {
    componentType: "view",
    nodeType: "mymod:panel",
    name: "default",
  },
  (_props, { currentNode }) => (
    <section className="my-panel">
      <Render
        node={currentNode}
        view="hidden.logic"
        parameters={{
          className: "my-panel-content",
          childClassName: "my-panel-item",
        }}
      />
    </section>
  ),
);
```

## Case 3: add a new leaf form element

For a leaf field, there is nothing special to do for `hidden.logic`.

Why:

- a leaf field does not render children
- when that field participates in conditional logic, it is the parent container that wraps it through `LogicAwareRender`
- the field view itself should stay focused on its own markup

In most cases, a custom leaf field should extend `fmdbmix:element`.

Example CND:

```cnd
<jnt = 'http://www.jahia.org/jahia/nt/1.0'>
<fmdbmix = 'http://www.jahia.org/jahia/fmdb/mix/1.0'>
<mymod = 'http://www.example.com/jahia/mymod/nt/1.0'>

[mymod:inputTextLike] > jnt:content, fmdbmix:element
 - required (boolean) = false
 - placeholder (string) i18n indexed=no
 - defaultValue (string) i18n indexed=no
```

Example server view:

```tsx
import { jahiaComponent } from "@jahia/javascript-modules-library";

interface InputTextLikeProps {
  "jcr:title"?: string;
  placeholder?: string;
  defaultValue?: string;
  required?: boolean;
}

jahiaComponent(
  {
    componentType: "view",
    nodeType: "mymod:inputTextLike",
    name: "default",
  },
  (
    {
      "jcr:title": label,
      placeholder,
      defaultValue,
      required,
    }: InputTextLikeProps,
    { currentNode },
  ) => {
    const inputId = `input-${currentNode.getIdentifier()}`;
    const inputName = currentNode.getName();

    return (
      <div className="fmdb-form-group">
        {label && (
          <label htmlFor={inputId} className="fmdb-form-label">
            {label}
          </label>
        )}

        <input
          type="text"
          id={inputId}
          name={inputName}
          className="fmdb-form-control"
          placeholder={placeholder}
          defaultValue={defaultValue}
          required={required}
        />
      </div>
    );
  },
);
```

This is intentionally similar to Formidable's built-in `fmdb:inputText`.

## HTML conventions for custom fields

External form fields should follow the same conventions as built-in fields:

- use `currentNode.getName()` as the HTML `name`
- use `input-${currentNode.getIdentifier()}` as the HTML `id`
- keep Formidable CSS hooks such as `fmdb-form-group`, `fmdb-form-label`, and `fmdb-form-control` when possible

Those conventions keep the field compatible with:

- form submission naming
- built-in styling
- Cypress selectors
- label/input linkage

## When to add more engine mixins

`fmdbmix:element` is enough for a simple text-like field.

If your field also needs engine-side behavior, add the matching semantic mixin from `formidable-engine`.

Examples:

- `fmdbmix:emailField`
- `fmdbmix:fileField`
- `fmdbmix:dateField`
- `fmdbmix:datetimeLocalField`
- `fmdbmix:colorField`
- `fmdbmix:choiceField`

This lets the submission pipeline react to semantics instead of hard-coding your concrete node type name.

## Rule of thumb

If your custom view renders child form nodes, treat it as a `formContainer` and delegate child rendering to `hidden.logic`.

If your custom view renders a single field and no child form nodes, treat it as a leaf `formElement` and render the HTML directly.
