# Formidable jExperience Engine Architecture

## Purpose

`formidable-jexperience-engine` is the dedicated integration module between:

- `formidable-engine`, which owns the generic form submission pipeline and the `FormAction` SPI
- `jexperience`, which exposes Jahia/jExperience services such as `ContextServerService`

The module exists to keep all jExperience-specific logic out of the generic Formidable engine.

At the moment, the module covers three responsibilities:

1. Define the JCR types used by the integration
2. Expose a choicelist initializer to select compatible jExperience profile properties
3. Implement a `FormAction` that sends accepted form submissions to jExperience

## High-Level Flow

### 1. Editor-side configuration

The module contributes:

- a mixin, `fmdbmix:jExperienceProfileMapping`, which extends supported field node types
- an action node type, `fmdb:jExperienceAction`

The mixin adds:

- `jExperienceProfileProperty`: the target jExperience profile property to map to
- `jExperiencePrefillFromProfile`: a flag kept for future field prefilling support

The property chooser uses a custom `ModuleChoiceListInitializer` that:

- resolves the current field node from the Jahia editor context
- infers the field shape from the Formidable field node type
- loads jExperience profile properties for the current site
- filters the list to properties compatible with the field shape

### 2. Submission-side processing

When a form contains a `fmdb:jExperienceAction` node in its `actions` list:

- `formidable-engine` invokes the `FormAction` implementation from this module
- the action walks the form field tree under `fields`
- it collects submitted values for supported field types
- it collects configured profile mappings
- it sends one custom jExperience event through `ContextServerService`

### 3. jExperience event payload

The event currently contains:

- submission channel: `formidable`
- submission status: `accepted`
- the submitted field values
- the field-to-profile-property mapping information

Current behavior is deliberately simple:

- all collected supported fields are included in the event payload
- the previously discussed `jExperienceIncludeInEvent` switch is intentionally not active
- the idea is preserved as commented code, but not enabled

## Package Layout

```text
org.jahia.modules.formidable.jexperience.engine
org.jahia.modules.formidable.jexperience.engine.actions
org.jahia.modules.formidable.jexperience.engine.choicelist
```

The packages are split by role:

- `engine`: shared model and service classes
- `actions`: submission pipeline integration
- `choicelist`: content editor integration

## File-by-File Breakdown

### Build and module descriptor

#### `pom.xml`

Defines the module as a Java OSGi bundle.

Key points:

- parent: `formidable-modules`
- packaging: `bundle`
- runtime dependencies: `formidable-engine,jexperience`
- compile dependency on `formidable-engine`
- provided dependency on `org.jahia.modules:jexperience`
- OSGi export/import configuration through `maven-bundle-plugin`

This module is intentionally Java-only. It has no frontend or JavaScript build.

## Java sources

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceEventService.java`

Sends the custom jExperience submission event.

Responsibilities:

- check that `ContextServerService` is available
- resolve the current site key
- build the event source item
- build the event target item
- package field values and mapping metadata
- submit a `ContextRequest` with one `Event`

This class is the runtime bridge between Formidable submission data and jExperience.

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceFieldShape.java`

Small value object describing a field shape:

- value kind
- single or multi-valued

It is used both by the editor-side choicelist filtering and by the runtime action.

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceFieldValueKind.java`

Enum describing the currently supported logical value kinds:

- `STRING`
- `DATE`

This is a normalization layer between Formidable field node types and jExperience profile property types.

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceFormFieldSupport.java`

Shared helper for supported field introspection.

Responsibilities:

- define the field property names used by the integration
- walk the form field tree and collect supported nodes
- resolve a field shape from a Formidable field node type
- read the configured profile property
- read the `jExperiencePrefillFromProfile` flag

Notes:

- `jExperienceIncludeInEvent` is intentionally kept commented out
- supported node types are explicitly listed in `resolveFieldShape(...)`

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceProfilePropertyDescriptor.java`

Simple descriptor record used by the choicelist layer.

It carries:

- internal property name
- editor label
- logical value kind
- multi-value flag

This avoids exposing the raw jExperience `PropertyType` model to the rest of the module.

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceProfileSchemaService.java`

Loads and normalizes jExperience profile properties for one site.

Responsibilities:

- use `ContextServerService`
- call `/cxs/profiles/properties/targets/profiles`
- deserialize the response as `PropertyType[]`
- remove unsupported, hidden, read-only, or protected properties
- map the remaining properties into `JExperienceProfilePropertyDescriptor`

This service is the data source behind the profile property choicelist.

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/actions/SendToJExperienceFormAction.java`

Implements the `FormAction` contract for `fmdb:jExperienceAction`.

Responsibilities:

- resolve the parent `fmdb:form`
- traverse supported field nodes
- read submitted values from the multipart parameter map
- collect configured profile mappings
- send the resulting payload to `JExperienceEventService`

Current behavior:

- all supported submitted fields are added to the event payload
- profile mappings are only included when `jExperienceProfileProperty` is configured

### `src/main/java/org/jahia/modules/formidable/jexperience/engine/choicelist/FormidableJExperienceProfilePropertiesInitializer.java`

Jahia editor choicelist initializer for `jExperienceProfileProperty`.

Responsibilities:

- resolve the current field node from the Jahia editor context
- infer the field shape from the field node type
- fetch profile properties for the current site
- filter the list to compatible properties only
- return `ChoiceListValue` entries for the editor

This is what makes the field property chooser context-sensitive instead of returning the whole profile schema.

## JCR definitions and i18n

### `src/main/resources/META-INF/definitions.cnd`

Declares the JCR types provided by the module.

It currently defines:

- `fmdbmix:jExperienceProfileMapping`
- `fmdb:jExperienceAction`

#### `fmdbmix:jExperienceProfileMapping`

This mixin extends each supported concrete field type directly:

- `fmdb:inputText`
- `fmdb:textarea`
- `fmdb:radio`
- `fmdb:inputEmail`
- `fmdb:select`
- `fmdb:checkbox`
- `fmdb:inputDate`
- `fmdb:inputDatetimeLocal`

This design is intentional. In this project, we do not rely on chaining one mixin from another mixin such as `fmdbmix:element`.

The mixin contributes:

- `jExperienceProfileProperty`
- `jExperiencePrefillFromProfile`

The previously discussed `jExperienceIncludeInEvent` property is left commented out on purpose.

#### `fmdb:jExperienceAction`

Defines the action node type stored under the form `actions` list.

This node type is what binds the generic Formidable submission pipeline to the jExperience-specific `FormAction`.

### `src/main/resources/resources/formidable-jexperience-engine.properties`

English labels and tooltips for:

- `fmdbmix:jExperienceProfileMapping`
- `jExperienceProfileProperty`
- `jExperiencePrefillFromProfile`
- `fmdb:jExperienceAction`

### `src/main/resources/resources/formidable-jexperience-engine_fr.properties`

French labels and tooltips for the same JCR types and properties.

## Architectural Boundaries

### What belongs in this module

- jExperience service calls
- jExperience-specific `FormAction` implementations
- jExperience-specific field metadata
- jExperience-specific choicelist initializers
- jExperience-specific JCR types and i18n

### What should stay in `formidable-engine`

- generic multipart parsing
- generic form validation
- generic form submission orchestration
- generic `FormAction` SPI
- non-jExperience actions

## Current Limitations

The module intentionally stops short of a few features:

- `jExperiencePrefillFromProfile` is modeled in JCR and helper code, but not yet wired into server-side field rendering
- `jExperienceIncludeInEvent` is intentionally disabled for now; the event currently includes all supported fields
- the custom event is submitted to jExperience, but there is no rule-generation layer like the older `JExperienceMapping` example

## Extension Points

If the integration grows, the next logical additions are:

1. server-side prefill support for `jExperiencePrefillFromProfile`
2. optional reactivation of `jExperienceIncludeInEvent`
3. richer type support beyond `STRING` and `DATE`
4. rule-generation or profile-update behavior if the project needs a closer match to legacy jExperience mapping modules

## Generated Files

The following files are build outputs and are not part of the source architecture:

- `.flattened-pom.xml`
- everything under `target/`

They should not be edited manually.
