# Add a Custom Formidable Action From Another Jahia Module

This document explains how to contribute a new Formidable form action from a module other than `formidable-engine`.

The extension model is intentionally simple:

- the form submission servlet collects every OSGi service implementing `FormAction`
- each action handler declares the JCR node type it supports through `getNodeType()`
- the form submission pipeline looks up the handler matching the action node primary type

Relevant runtime references:

- `FormAction` interface: [formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormAction.java](../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormAction.java)
- dynamic OSGi binding: [formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmitServlet.java](../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmitServlet.java)
- action mixin: [formidable-engine/src/main/resources/META-INF/definitions.cnd](../formidable-engine/src/main/resources/META-INF/definitions.cnd)
- form action list definition: [formidable-elements/src/components/Form/definition.cnd](../formidable-elements/src/components/Form/definition.cnd)

## Overview

To add a custom action from another module, you need four things:

1. A dependency on the Formidable engine module so your code can import `FormAction`.
2. A new JCR node type extending `fmdbmix:formAction`.
3. An OSGi component implementing `FormAction`.
4. Editor integration so contributors can create and configure your action node inside a form's `actions` list.

## How the runtime dispatch works

At submission time:

1. `FormSubmitServlet` receives the POST request.
2. `FormSubmissionPipeline` resolves the form and its child action nodes.
3. For each action node, the pipeline reads the node primary type.
4. It finds the first registered `FormAction` whose `getNodeType()` returns the same type string.
5. It calls `execute(...)`.

That means your external module does not need to modify Formidable internals as long as:

- your action node type is a child compatible with `fmdb:actionList`
- your OSGi service is visible and active
- `getNodeType()` exactly matches your node type name

## Step 1: Add a dependency on Formidable

Your module must depend on the module exposing the `FormAction` API.

In practice this means:

- your Maven module must depend on `formidable-engine`
- your OSGi bundle must import the `org.jahia.modules.formidable.engine.actions` package

The exact dependency declaration depends on how your Jahia modules are structured, but the important point is that your code must compile against:

```java
org.jahia.modules.formidable.engine.actions.FormAction
org.jahia.modules.formidable.engine.actions.FormActionException
```

## Step 2: Declare a new action node type

Create a CND definition in your own module.

Example:

```cnd
<jnt = 'http://www.jahia.org/jahia/nt/1.0'>
<fmdbmix = 'http://www.jahia.org/jahia/fmdb/mix/1.0'>
<mymod = 'http://www.example.com/jahia/mymod/nt/1.0'>

[mymod:webhookAction] > jnt:content, fmdbmix:formAction, mix:title
 - endpointId (string) indexed=no
 - apiKey (string) indexed=no
 - enabled (boolean) = true
```

Requirements:

- the type must extend `fmdbmix:formAction`
- the type should usually extend `jnt:content`
- `mix:title` is recommended for editor usability

Why this works:

- `fmdb:actionList` accepts children of type `fmdbmix:formAction`
- your concrete action type becomes valid inside a form's `actions` node

## Step 3: Implement the OSGi action handler

Create a Java class in your external module:

```java
package org.example.jahia.modules.mymod.actions;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Component(service = FormAction.class)
public class WebhookFormAction implements FormAction {

    @Override
    public String getNodeType() {
        return "mymod:webhookAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {
        try {
            String endpointId = actionNode.getProperty("endpointId").getString();
            boolean enabled = !actionNode.hasProperty("enabled") || actionNode.getProperty("enabled").getBoolean();

            if (!enabled) {
                return;
            }

            // Do your action-specific logic here.
            // parameters contains the server-validated text fields.
            // Uploaded files are available through the request attribute
            // "formidable.parsedFiles" if your action needs them.

        } catch (Exception e) {
            throw new FormActionException("Webhook action failed: " + e.getMessage(), 500);
        }
    }
}
```

Important rules:

- `getNodeType()` must return the exact primary node type string
- throw `FormActionException` when the action must stop the submission pipeline
- do not assume browser-side validation was the only protection; rely on the already parsed and validated input you receive

## Step 4: Understand what input your action receives

Inside `execute(...)`:

- `actionNode` contains your action configuration stored in JCR
- `parameters` contains validated text fields as `Map<String, List<String>>`
- `req` is the current servlet request
- `session` is the current JCR session

If your action needs uploaded files, read them from the request attribute populated by Formidable before action dispatch:

```java
@SuppressWarnings("unchecked")
List<FormDataParser.FormFile> files =
        (List<FormDataParser.FormFile>) req.getAttribute(FormDataParser.PARSED_FILES_ATTR);
```

Do this only if your module explicitly depends on the Formidable parser API and you accept that coupling.

## Step 5: Add editor integration

The runtime part is not enough by itself. Contributors still need a way to create your action node in Jahia.

Typical options:

- provide a content editor definition for your node type
- expose the type in the relevant creation menu
- add a custom chooser or selector if one of your properties must be selected from a controlled list

At minimum, the contributor must be able to:

1. create a node of type `mymod:webhookAction`
2. place it under the form's `actions` list
3. edit its properties

Without editor integration, the action can still work at runtime, but authors will not be able to use it conveniently.

## Step 6: Add module dependencies carefully

A custom action runs inside the submission pipeline, so treat it as server-side integration code.

Common dependencies you may need:

- `HttpClient` or another outbound client
- Jahia services such as `MailService`, custom OSGi services, or repository services
- your own configuration service

Prefer constructor or OSGi `@Reference` injection rather than static lookups.

## Security recommendations

A custom action is part of the trusted server-side pipeline. Do not assume it is safe just because Formidable parsed the form.

Recommended practices:

- never trust raw JCR configuration without validating it
- prefer identifiers resolved server-side over free-form URLs or secrets stored in content
- keep outbound network destinations in operator-managed configuration
- use strict allowlists for any integration target
- set explicit connect and read timeouts for external calls
- do not log sensitive submitted values
- return `FormActionException` with a generic message for user-facing failures

If your action sends data externally:

- avoid storing a raw destination URL in the JCR node
- store a logical `targetId` instead
- resolve `targetId` to a canonical URL in operator configuration

## Minimal example flow

Example end-to-end design:

1. External module defines `[mymod:webhookAction] > jnt:content, fmdbmix:formAction, mix:title`
2. External module registers `WebhookFormAction implements FormAction`
3. `WebhookFormAction#getNodeType()` returns `mymod:webhookAction`
4. Contributor creates a `mymod:webhookAction` node under the form's `actions`
5. Formidable receives a submission
6. The pipeline sees `mymod:webhookAction`
7. The matching OSGi service executes

No Formidable source code changes are required for that dispatch model.

## Troubleshooting

If your action is not executed, check these points first:

1. Your OSGi component is active.
2. `getNodeType()` exactly matches the JCR primary type.
3. The action node is really under the form's `actions` child node.
4. The node type extends `fmdbmix:formAction`.
5. Your bundle imports the Formidable action API package correctly.
6. No earlier action in the pipeline throws `FormActionException`.

Useful runtime symptom:

- if the pipeline logs that no handler exists for your action type, your node type was found but no active `FormAction` service matched it

## Suggested project structure

Example external module layout:

```text
my-custom-form-actions/
├── pom.xml
├── src/main/java/org/example/jahia/modules/mymod/actions/
│   └── WebhookFormAction.java
└── src/main/resources/
    └── META-INF/
        └── definitions.cnd
```

## Summary

To add a custom Formidable action from another module:

1. Define a new action node type extending `fmdbmix:formAction`.
2. Implement and register an OSGi `FormAction`.
3. Return the same node type from `getNodeType()`.
4. Add editor support so contributors can create and configure the node.

That is the supported extension pattern exposed by the current Formidable runtime.
