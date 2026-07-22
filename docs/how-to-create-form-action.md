# How to Create a `FormAction`

This is the single guide for adding a server-side Formidable action, whether the action lives inside `formidable-engine`, in a JavaScript module (TypeScript), or in another Jahia module.

Relevant runtime files:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FormAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FormActionException.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FormActionSupport.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmissionPipeline.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmitServlet.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/JsFormActionDispatcher.java`
- `formidable-engine/src/main/resources/META-INF/definitions.cnd`
- `formidable-elements/src/server/registerFormAction.ts`

## Runtime model

At submission time:

1. `FormSubmitServlet` receives the POST request.
2. `FormSubmissionPipeline` resolves the submitted form and its configured action nodes.
3. Each action node is matched to the first OSGi service implementing `FormAction` whose `getNodeType()` matches the node primary type. When no Java service matches, the pipeline falls back to JavaScript handlers registered under the `formidable-form-action` registry type (see the TypeScript section below). A Java `FormAction` always wins over a JS handler for the same node type.
4. `execute(...)` is called.

That means a new action always needs:

- a JCR node type extending `fmdbmix:formAction`
- a handler: either a Java OSGi component implementing `FormAction`, or a TypeScript handler in a JavaScript module
- authoring support so contributors can configure the action on a form

## Where to define it

If the action belongs to another Jahia module:

- declare the node type in that module's own `definitions.cnd`
- add a Maven dependency on `formidable-engine`
- make sure the bundle imports `org.jahia.modules.formidable.engine.api`

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

import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
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

    private static final Logger logger = LoggerFactory.getLogger(WebhookFormAction.class);

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

            logger.info("Forwarding submission to target '{}' with {} field(s).", endpointId, parameters.size());
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
    logger.debug("Received uploaded file '{}' ({})", file.originalName(), file.mimeType());
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

> **Caveat — Jahia MailService:** `MailService.sendMessage()` queues the message through
> an Apache Camel route. SMTP delivery failures on the asynchronous delivery path are
> logged by Jahia/Camel and do not propagate back to the caller. A `try/catch` around
> `sendMessage()` only handles synchronous failures raised while invoking the mail
> service. To guard against a missing SMTP configuration, check `mailService.isEnabled()`
> before calling `sendMessage()`.

## Step 7: Add authoring support

The runtime code is not enough by itself. Contributors still need to be able to create and configure the action node.

At minimum, authors must be able to:

1. create a node of your action type
2. edit its properties
3. create or place it under the form's `actions` child node (`fmdb:actionList`)

For an external action, this authoring support also belongs in that module.

## TypeScript form actions

Formidable's built-in email and forward actions are themselves TypeScript actions (see `formidable-elements/src/server/actions/`). A JavaScript module can contribute an action without any Java code.

Requirements:

- `javascript-modules-engine` ≥ 1.3.0 deployed (the version exporting the `org.jahia.modules.javascript.modules.engine.sdk` package). On older engines, formidable still deploys and Java actions keep working; node types with only a JS handler fail with `FMDB-008`.
- The action node type declared in the JS module's CND, extending `fmdbmix:formAction` (same rules as Step 1).

### With the formidable-elements wrapper

Inside this repository, use `registerFormAction` from `formidable-elements/src/server/registerFormAction.ts` in a `*.server.ts` file (the vite `server.inputGlob` must include plain `.ts`):

```ts
import { registerFormAction, FormActionError } from "../registerFormAction";

registerFormAction({ nodeType: "mymod:webhookAction" }, ({ actionNode, parameters, files }) => {
	// Side effect here. Throw FormActionError to fail the submission:
	// throw FormActionError.badRequest("Missing required field 'email'.");
});
```

The handler context mirrors `FormAction#execute`:

- `actionNode` — the action configuration node, from a live system session bound to the submission locale (i18n properties resolve directly)
- `parameters` — validated text fields as `Record<string, string[]>`
- `files` — validated uploads (`fieldName()`, `originalName()`, `mimeType()`, `data()`)
- `javaParameters` / `javaFiles` — the raw Java collections, to pass to `FormActionSupport`
- `session`, `request` — escape hatches

Constraints:

- Handlers run synchronously on the submission thread — no promises, no `fetch`. Outbound HTTP must go through Java (see `FormActionSupport`).
- Never iterate `files[i].data()` (a Java `byte[]`) in JS; hand it back to Java APIs.
- Never cache Java objects between invocations — GraalVM contexts are recycled on module (un)deploy.

### The raw registry contract (external JS modules)

`registerFormAction` is not published as an npm package yet. An external JavaScript module registers directly (see `jahia-test-module/formidable-test-module-samples-tsx/src/server/formActions.server.tsx` for a working sample):

```ts
server.registry.add("formidable-form-action", "mymod:webhookAction", {
	nodeType: "mymod:webhookAction",
	execute: (actionNode, request, session, parameters, files) =>
		// Return {ok: true} on success, never throw across the boundary:
		({ ok: false, status: 502, message: "Webhook endpoint unreachable." }),
});
```

The contract, decoded by `JsFormActionDispatcher` (keep both sides in sync):

- entry fields: `nodeType` (string), `execute` (function)
- `execute` arguments: the exact `FormAction#execute` arguments as host objects
- return value: `{ ok: true }` or `{ ok: false, status: number, message: string }`; anything else (including a thrown error) fails closed as HTTP 500

### `FormActionSupport`

Security-sensitive helpers stay in Java and are exposed as an OSGi service:

```ts
const support = server.osgi.getService("org.jahia.modules.formidable.engine.api.FormActionSupport");
support.forwardSubmission(targetId, javaParameters, javaFiles); // SSRF-checked, URL never exposed
support.buildEmailAttachments(javaFiles, maxBytes); // RFC 6266 names, size caps
support.getUploadMaxFileSizeBytes();
```

A failed `FormActionSupport` call throws a Java `FormActionException`; the `registerFormAction` wrapper preserves its HTTP status automatically.

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

- Java: `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/storage/SaveToJcrFormAction.java`
- Java (third-party sample): `jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/LogSubmissionFormAction.java`
- TypeScript: `formidable-elements/src/server/actions/emailNotification.server.ts`, `emailContent.server.ts`, `forward.server.ts`
- TypeScript (raw contract sample): `jahia-test-module/formidable-test-module-samples-tsx/src/server/formActions.server.tsx`
