# How to Create a `FormAction`

This is the single guide for adding a server-side Formidable action, whether the action lives inside `formidable-engine` or in another Jahia module.

Relevant runtime files:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormActionException.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmissionPipeline.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmitServlet.java`
- `formidable-engine/src/main/resources/META-INF/definitions.cnd`

## Runtime model

At submission time:

1. `FormSubmitServlet` receives the POST request.
2. `FormSubmissionPipeline` resolves the submitted form and its configured action nodes.
3. Each action node is matched to the first OSGi service implementing `FormAction` whose `getNodeType()` matches the node primary type.
4. `execute(...)` is called.

That means a new action always needs:

- a JCR node type extending `fmdbmix:formAction`
- a Java OSGi component implementing `FormAction`
- authoring support so contributors can configure the action on a form

## Where to define it

If the action belongs to another Jahia module:

- declare the node type in that module's own `definitions.cnd`
- add a Maven dependency on `formidable-engine`
- make sure the bundle imports `org.jahia.modules.formidable.engine.actions`

If the action belongs to this repository, the same rules apply, except the node type and handler live directly in `formidable-engine`.

No Formidable source change is required for external actions as long as the SPI contract is respected.

## Step 1: Declare the action node type

```cnd
<jnt = 'http://www.jahia.org/jahia/nt/1.0'>
<fmdbmix = 'http://www.jahia.org/jahia/fmdb/mix/1.0'>
<mymod = 'http://www.example.com/jahia/mymod/nt/1.0'>

[mymod:webhookAction] > jnt:content, fmdbmix:formAction, mix:title
 - endpointId (string) indexed=no
```

Rules:

- the type must extend `fmdbmix:formAction`
- `jnt:content` is the normal base type
- `mix:title` is recommended for author usability

## Step 2: Implement the Java handler

Example:

```java
package org.example.jahia.modules.mymod.actions;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.modules.formidable.engine.actions.SubmittedFile;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            JCRSessionWrapper session,
            Map<String, List<String>> parameters,
            List<SubmittedFile> files
    ) throws FormActionException {
        try {
            String endpointId = actionNode.getProperty("endpointId").getString();

            System.out.println("Forwarding submission to target " + endpointId + " with " + parameters.size() + " field(s).");
        } catch (RepositoryException e) {
            throw new FormActionException(
                    "Could not read the action configuration.",
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e
            );
        }
    }
}
```

## Step 3: Match `getNodeType()` exactly

This is the most important rule.

If your node type is:

```cnd
[mymod:webhookAction] > jnt:content, fmdbmix:formAction, mix:title
```

then your handler must return:

```java
@Override
public String getNodeType() {
    return "mymod:webhookAction";
}
```

If the string does not match exactly, the action will never be executed.

## Step 4: Understand the input you receive

Inside `execute(...)`:

- `actionNode` contains the current action configuration stored in JCR
- `parameters` contains the validated text fields as `Map<String, List<String>>`
- `req` is the current servlet request
- `session` is the current JCR session

Actions run in the submission pipeline, outside the Jahia page-rendering flow. No `RenderContext` is available in this SPI.

Example for reading action configuration:

```java
String targetId = actionNode.getProperty("targetId").getString();
```

Example for reading submitted values:

```java
List<String> emailValues = parameters.get("email");
String email = (emailValues != null && !emailValues.isEmpty()) ? emailValues.get(0) : "";
```

## Step 5: Access uploaded files if needed

If your action needs uploaded files, read them from the `files` argument passed by the submission pipeline:

```java
for (SubmittedFile file : files) {
    System.out.println(file.originalName() + " (" + file.mimeType() + ")");
}
```

The SPI-level file contract exposes:

- `fieldName`
- `originalName`
- `mimeType`
- `data`

## Step 6: Fail correctly with `FormActionException`

Throw `FormActionException` when the action must stop the pipeline and return an error to the client.

Examples:

```java
throw FormActionException.badRequest("Missing required field 'email'.");
```

```java
throw FormActionException.serverError("MailService is unavailable.");
```

```java
throw new FormActionException("Forward target returned an error.", 502);
```

## Step 7: Add authoring support

The runtime code is not enough by itself. Contributors still need to be able to create and configure the action node.

At minimum, authors must be able to:

1. create a node of your action type
2. edit its properties
3. configure it on the form's `actions` list

For an external action, this authoring support also belongs in that module.

## Security guidance

A custom action is trusted server-side code. Do not assume it is safe just because Formidable already parsed the form.

Recommended practices:

- validate action configuration read from JCR
- prefer logical identifiers over raw URLs or secrets stored in content
- keep outbound targets in operator-managed configuration when possible
- use strict allowlists for external integrations
- do not log sensitive submitted values
- return generic error messages for user-facing failures

## Troubleshooting

If your action does not run, check:

1. the class is annotated with `@Component(service = FormAction.class)`
2. the OSGi component is active
3. the node type extends `fmdbmix:formAction`
4. `getNodeType()` exactly matches the primary node type
5. the action node is actually configured on the form
6. the module imports the Formidable action API package correctly
7. no earlier action in the pipeline already failed

Useful runtime symptom:

- if the pipeline logs that no handler exists for your action type, the node was found but no active `FormAction` service matched it

## Related examples in this repository

Use these implementations as references:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/email/SendEmailNotificationFormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/forward/ForwardSubmissionFormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/storage/SaveToJcrFormAction.java`
