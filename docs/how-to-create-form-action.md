# How to Create a `FormAction`

This document explains how to add a new server-side Formidable action in English, with concrete code samples taken from the current extension model in this repository.

Relevant source files:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/FormActionException.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmitServlet.java`
- `formidable-engine/src/main/resources/META-INF/definitions.cnd`

## What a `FormAction` is

A `FormAction` is a pluggable server-side handler executed after a form submission reaches the Formidable submission pipeline.

At runtime:

1. `FormSubmitServlet` receives the POST request.
2. The submission pipeline resolves the configured action nodes for the submitted form.
3. Each action node is matched to an OSGi service implementing `FormAction`.
4. The match is done through `getNodeType()`.
5. `execute(...)` is called.

That means a new action requires two things:

- a JCR node type representing the action configuration
- a Java OSGi component implementing `FormAction`

## Step 1: Declare a new action node type

Add a new node type in `formidable-engine/src/main/resources/META-INF/definitions.cnd` if the action belongs to this module.

Minimal example:

```cnd
<jnt = 'http://www.jahia.org/jahia/nt/1.0'>
<fmdb = 'http://www.jahia.org/jahia/fmdb/nt/1.0'>
<fmdbmix = 'http://www.jahia.org/jahia/fmdb/mix/1.0'>

[fmdb:sampleAuditAction] > jnt:content, fmdbmix:formAction, mix:title
 - messagePrefix (string) indexed=no
```

Rules:

- the type must extend `fmdbmix:formAction`
- `jnt:content` is the standard base type here
- `mix:title` is recommended so editors can label the node

## Step 2: Implement the Java action

Create a class implementing `FormAction` and register it as an OSGi service.

Example:

```java
package org.jahia.modules.formidable.engine.actions.sample;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@Component(service = FormAction.class)
public class SampleAuditFormAction implements FormAction {

    private static final Logger logger = LoggerFactory.getLogger(SampleAuditFormAction.class);

    @Override
    public String getNodeType() {
        return "fmdb:sampleAuditAction";
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
            String prefix = actionNode.hasProperty("messagePrefix")
                    ? actionNode.getProperty("messagePrefix").getString()
                    : "Form submission";

            logger.info("{} received from IP {} with {} field(s)",
                    prefix,
                    req.getRemoteAddr(),
                    parameters.size());

        } catch (RepositoryException e) {
            throw new FormActionException(
                    "Could not read the sample action configuration.",
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e
            );
        }
    }
}
```

## Step 3: Match `getNodeType()` exactly

This is the most important rule.

If your CND type is:

```cnd
[fmdb:sampleAuditAction] > jnt:content, fmdbmix:formAction, mix:title
```

then your Java code must return:

```java
@Override
public String getNodeType() {
    return "fmdb:sampleAuditAction";
}
```

If the string does not match exactly, the action will never be executed.

## Step 4: Read action configuration from `actionNode`

The `actionNode` parameter contains the JCR node for the current action instance.

Example:

```java
String targetId = actionNode.getProperty("targetId").getString();
```

Use this for action-specific configuration such as:

- recipient addresses
- feature flags
- template text
- target identifiers

## Step 5: Read submitted form values from `parameters`

`parameters` contains the submitted text values as:

```java
Map<String, List<String>> parameters
```

Examples:

```java
List<String> firstNameValues = parameters.get("firstname");
String firstName = (firstNameValues != null && !firstNameValues.isEmpty())
        ? firstNameValues.get(0)
        : "";
```

Loop over all values:

```java
for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
    String fieldName = entry.getKey();
    List<String> values = entry.getValue();
    // do something with the submitted values
}
```

## Step 6: Access uploaded files if needed

If your action needs uploaded files, read them from the request attribute populated by the parser.

Example:

```java
import org.jahia.modules.formidable.engine.actions.FormDataParser;

@SuppressWarnings("unchecked")
List<FormDataParser.FormFile> files =
        (List<FormDataParser.FormFile>) req.getAttribute(FormDataParser.PARSED_FILES_ATTR);

if (files == null) {
    throw FormActionException.serverError("Uploaded files are unavailable.");
}
```

Each file gives you:

- field name
- original file name
- storage name
- mime type
- binary data

## Step 7: Fail correctly with `FormActionException`

Throw `FormActionException` when the action must stop the pipeline and return an HTTP error to the client.

Examples:

```java
throw FormActionException.badRequest("Missing required field 'email'.");
```

```java
throw FormActionException.serverError("MailService is unavailable.");
```

Or explicitly:

```java
throw new FormActionException("Forward target returned an error.", 502);
```

## Step 8: Register the action in authoring

The runtime code is not enough by itself. Editors still need to be able to create the action node.

In practice, make sure authors can:

1. create a node of your action type
2. configure its properties
3. reference or place it where the form pipeline expects it

If you are adding the action inside this module, that usually means:

- defining the CND type
- exposing the type in the relevant editor UI
- making sure contributors can configure the node properties

## Complete example

### CND

```cnd
[fmdb:sampleAuditAction] > jnt:content, fmdbmix:formAction, mix:title
 - messagePrefix (string) indexed=no
```

### Java

```java
@Component(service = FormAction.class)
public class SampleAuditFormAction implements FormAction {

    @Override
    public String getNodeType() {
        return "fmdb:sampleAuditAction";
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
            String prefix = actionNode.hasProperty("messagePrefix")
                    ? actionNode.getProperty("messagePrefix").getString()
                    : "Submission";

            System.out.println(prefix + ": " + parameters);
        } catch (RepositoryException e) {
            throw FormActionException.serverError("Failed to execute sample audit action.");
        }
    }
}
```

## Troubleshooting

If your action does not run, check:

1. the class is annotated with `@Component(service = FormAction.class)`
2. the OSGi component is active
3. the node type extends `fmdbmix:formAction`
4. `getNodeType()` exactly matches the primary node type
5. the action node is actually configured on the form

## Related examples in this repository

Use these implementations as references:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/email/SendEmailNotificationFormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/forward/ForwardSubmissionFormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/storage/SaveToJcrFormAction.java`
