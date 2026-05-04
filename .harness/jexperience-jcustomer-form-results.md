# Implementation of Form Result Persistence in jExperience / jCustomer

## Context

The work identified on the `save2jCustomer` branch corresponds to commit `58c7edb` from **April 30, 2026**:

- `feat: add formidable-jexperience-engine integration module`

The goal was not to add local submission persistence inside Formidable itself. The work was to connect Formidable to **jExperience / jCustomer** through a dedicated Java module and a `FormAction`.

## What was added

### 1. New Maven / OSGi sub-module

Created module:

- `formidable-jexperience-engine`

and registered it in the root `pom.xml`.

This module is responsible for:

- keeping all jExperience-specific logic out of `formidable-engine`
- exposing a dedicated Jahia `FormAction`
- exposing a choicelist initializer to map Formidable fields to jExperience profile properties

## 2. New Formidable action type

Added the JCR type:

- `fmdb:jExperienceAction`

When this action is referenced in the `actions` list of an `fmdb:form`, the server-side pipeline in `formidable-engine` executes:

- `SendToJExperienceFormAction`

This action:

- resolves the parent `fmdb:form`
- traverses the `fields` tree
- collects submitted values for supported fields
- collects configured mappings to profile properties
- sends a custom event to jExperience

## 3. Field-level mapping mixin

Added the mixin:

- `fmdbmix:jExperienceProfileMapping`

It extends the supported field types directly and adds:

- `jExperienceProfileProperty`
- `jExperiencePrefillFromProfile`

The prefill flag was **modeled**, but it is **not functionally wired yet**.

An older event-filtering switch (`jExperienceIncludeInEvent`) was left commented out and is not active.

## 4. Currently supported field types

Support was centralized in `JExperienceFormFieldSupport`.

Supported types:

- `fmdb:inputText`
- `fmdb:textarea`
- `fmdb:radio`
- `fmdb:inputEmail`
- `fmdb:select`
- `fmdb:checkbox`
- `fmdb:inputDate`
- `fmdb:inputDatetimeLocal`

Functional normalization:

- `STRING` values
- `DATE` values
- single-value or multi-value handling depending on the field

## 5. Dynamic jExperience profile property choicelist

Added:

- `FormidableJExperienceProfilePropertiesInitializer`
- `JExperienceProfileSchemaService`

Editor-side behavior:

- resolve the current field node from the Jahia context
- infer its functional shape (logical type + single/multi-valued)
- load the profile property schema through `ContextServerService`
- filter out incompatible, hidden, read-only, or protected properties
- show only compatible properties in the selector

This prevents exposing the full jExperience schema without filtering.

## 6. Sending form results to jExperience / jCustomer

Added:

- `JExperienceEventService`

The implementation sends a custom Unomi/jExperience event of type:

- `formidableSubmissionAccepted`

The payload contains:

- `submissionChannel = "formidable"`
- `submissionStatus = "accepted"`
- `fields`: collected submitted values
- `profileMappings`: a `fieldName -> profileProperty` map

The service also builds:

- a `source` item of type `page`
- a `target` item of type `form`

and then calls `ContextServerService.executeContextRequest(...)`.

## Important clarification about "persistence"

What was actually implemented:

- **sending accepted form submission data to jExperience/jCustomer as a custom event**

What was **not** directly implemented in this work:

- **directly writing submitted values into the jCustomer profile**
- **automatic synchronization of profile properties from the configured mappings**
- **effective field prefilling from the profile**

In other words, the profile mappings are included in the emitted event, but it is still up to the jExperience/jCustomer side (rules, downstream processing, enrichment) to consume that event and apply profile updates if needed.

## Main files

- `pom.xml`
- `formidable-jexperience-engine/pom.xml`
- `formidable-jexperience-engine/src/main/resources/META-INF/definitions.cnd`
- `formidable-jexperience-engine/src/main/java/org/jahia/modules/formidable/jexperience/engine/actions/SendToJExperienceFormAction.java`
- `formidable-jexperience-engine/src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceEventService.java`
- `formidable-jexperience-engine/src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceFormFieldSupport.java`
- `formidable-jexperience-engine/src/main/java/org/jahia/modules/formidable/jexperience/engine/JExperienceProfileSchemaService.java`
- `formidable-jexperience-engine/src/main/java/org/jahia/modules/formidable/jexperience/engine/choicelist/FormidableJExperienceProfilePropertiesInitializer.java`
- `formidable-jexperience-engine/docs/architecture.md`

## Actual project state

As it stands, the module establishes the integration foundation correctly:

- JCR typing
- editor configuration
- submission data collection
- event emission to jExperience

The part that still remains, if that was the intended product outcome, is the actual write-back into the jCustomer profile.
