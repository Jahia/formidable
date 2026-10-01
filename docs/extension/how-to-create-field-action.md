# How to Create a `FieldAction`

This is the single guide for adding a server-side check of one field's value, whether the check lives in another
Jahia module or in this repository. It is the twin of [How to create a form action](how-to-create-form-action.md):
the same mechanics — a node type, an OSGi service bound to it by name, a card in the form's authoring zone — for a
check that judges one value instead of acting on a whole submission.

Relevant runtime files:

- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FieldAction.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FieldActionRequest.java`, `FieldActionResult.java`
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/ProviderFieldAction.java`, `EmailVerificationFieldAction.java`, `FieldActionGateway.java` — for a check that calls an external service
- `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/FieldActionDispatcher.java`
- `formidable-engine/src/main/resources/META-INF/definitions.cnd` — `fmdbmix:fieldAction`, `fmdbmix:fieldActionFeedback`

The design, and why it is shaped this way, is in [Field actions](../architecture/field-actions.md).

## Runtime model

A field action runs twice from one node:

1. **While the visitor fills the form**: the browser asks the pre-check endpoint (`/modules/formidable-engine/field-action`)
   on blur or just before the submission, as the contributor set, and shows the verdict under the field.
2. **At submission**: the pipeline judges the value again, before any form action, and refuses the submission
   (`FMDB-015`) on a blocking refusal. The browser's answer is never trusted.

Both go through `FieldActionDispatcher`, which matches each field-action node to the first OSGi service
implementing `FieldAction` whose `getNodeType()` equals the node's primary type, and calls `execute(...)`. A verdict
cache per action, language and value makes the second run free when the first one already answered.

What the engine does for you: the switch in the field's editor, the list of actions, the endpoint and its rate
limit, the second run, the message under the field, the cache, the calls to a provider. What you write:

- a JCR node type taking `fmdbmix:fieldAction`
- a Java OSGi component implementing `FieldAction`
- its label, tooltip and icon, so that contributors can find it in the chooser

## Where to define it

If the action belongs to another Jahia module:

- declare the node type in that module's own `definitions.cnd`
- add a Maven dependency on `formidable-engine` (compile-time: the SPI)
- make sure the bundle imports `org.jahia.modules.formidable.engine.api`
- declare the deploy-time dependency too — without it the CND cannot resolve `fmdbmix:fieldAction`:
  `<jahia-depends>formidable-engine</jahia-depends>` in the pom's `<properties>`

A field action is Java: the dispatcher runs OSGi services only, so a JavaScript module cannot implement one. It can
still ship the node type's authoring card (see the end of this page).

If the action belongs to this repository, it is a sample: the engine ships the mechanism and no concrete check.
The samples live in `jahia-test-module/formidable-test-module-samples-java`.

## Step 1: Declare the field action node type

```cnd
<jnt = 'http://www.jahia.org/jahia/nt/1.0'>
<fmdbmix = 'http://www.jahia.org/jahia/fmdb/mix/1.0'>
<myco = 'http://www.example.com/jahia/myco/nt/1.0'>

[myco:blockedWordsAction] > jnt:content, fmdbmix:fieldAction, mix:title
 - jcr:title (string) = resourceBundle('myco_blockedWordsAction') autocreated i18n
 - words (string) multiple indexed=no
```

Rules:

- the type must extend `fmdbmix:fieldAction` — the list under a field (`fmdb:fieldActionList`) accepts nothing else
- `jnt:content` is the normal base type, `mix:title` with a default title keeps the card readable
- the four contributor settings — the message the visitor reads, when to check (`blur` or `submit`), whether a
  refusal blocks or only warns, what an unanswered check means — come with the marker through
  `fmdbmix:fieldActionFeedback`: do not redeclare them
- your own properties are what the contributor configures on the check: a threshold, a list of words, a country
- a check that calls an external service takes nothing more: the service is the module's own, read from the
  module's configuration, and the contributor has none to pick (Step 5)

Ship the type's label and tooltip in your resource bundle (`myco_blockedWordsAction`,
`myco_blockedWordsAction.ui.tooltip`) and a 16×16 icon at `src/main/resources/icons/myco_blockedWordsAction.png`:
the Content Editor's icon fallback stops at `nt:base` before it reaches the marker, which is a mixin.

## Step 2: Implement the Java service

```java
package com.example.jahia.myco.actions;

import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import java.util.Locale;

@Component(service = FieldAction.class)
public class BlockedWordsFieldAction implements FieldAction {

    @Override
    public String getNodeType() {
        return "myco:blockedWordsAction";
    }

    @Override
    public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        String value = request.value() == null ? "" : request.value().toLowerCase(Locale.ROOT);
        try {
            if (!actionNode.hasProperty("words")) {
                return FieldActionResult.accept();
            }
            for (JCRValueWrapper word : actionNode.getProperty("words").getValues()) {
                String blocked = word.getString().trim().toLowerCase(Locale.ROOT);
                if (!blocked.isEmpty() && value.contains(blocked)) {
                    return FieldActionResult.reject("contains a blocked word");
                }
            }
            return FieldActionResult.accept();
        } catch (RepositoryException e) {
            return FieldActionResult.unavailable("words unreadable");
        }
    }
}
```

This is the samples' [`BlockedWordsFieldAction`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/BlockedWordsFieldAction.java),
the shape to copy for a check that calls nothing.

## Step 3: Match `getNodeType()` exactly

As for a form action, the binding is the string: `getNodeType()` must return the primary type of the node,
`myco:blockedWordsAction` for the CND above. A type no active service matches is an **unavailable** check — the
contributor's *If the check cannot run* setting decides, `accept` by default — never an error for the visitor.

## Step 4: Understand the input you receive

Inside `execute(...)`:

- `actionNode` is the field-action node, read in `live` in a system session: its properties are what the
  contributor configured
- `request.value()` is **one** candidate value, as the browser sent it, untrimmed. A field answered several times
  over — a group of checkboxes, a multiple select — is judged one value at a time, your action run once per
  value; it never sees the siblings
- `request.fieldName()` is the field's node name, `request.formId()` the form's UUID, `request.locale()` the
  visitor's locale, for a check whose answer depends on it

No servlet request, no JCR session of the visitor and no `RenderContext`: the same code answers the pre-check and
the submission, so it may rely on nothing only one of them has.

## Step 5: Call an external service through the gateway

A check that asks an external service — a mailbox check, a CRM lookup — knows its service: your module ships its
configuration, and the engine's `FieldActionGateway` makes the call. Three pieces:

1. **The module's configuration file**, `src/main/resources/META-INF/configurations/<your PID>.cfg`, its first line
   `# default configuration` so that Jahia copies it to `karaf/etc` once and never overwrites the administrator's
   edits. It holds the service's URL, its credential — the private property `.credential`, empty in the shipped
   file: the administrator sets it — and, for a double of the service on a developer's machine, `development=true`,
   honoured only while the administrator switches `enableDevFieldActionEndpoints` on in
   `org.jahia.modules.formidable.fieldActions.cfg`:

   ```properties
   # default configuration - deployed once, then kept as edited.
   url=https://api.example.com/v1
   .credential=
   development=false
   ```

   The leading dot matters: Declarative Services publishes a component's configuration with the service it
   registers — readable by anyone listing services — except the names starting with a dot. The annotation method is
   `_credential()`, which DS reads as `.credential`. Declare the configuration's `@ObjectClassDefinition` in your
   own bundle: bnd generates the metatype only from a definition it finds there. Name it `Formidable — Field
   actions — <your check>`, as the engine names its own entries: the configuration manager then lists every field
   action together, and the service name tells the rows apart.

2. **The configuration read into an endpoint**, with `@Designate` and `@Activate`/`@Modified` on your component,
   through the base's `configure(name, url, credential, development, credentialName, credentialIn)` — it checks the
   URL (HTTPS without a query, or plain HTTP on localhost or host.docker.internal with `development=true`) and the
   credential, and logs once, naming the service, never the credential, when the
   configuration describes nothing usable yet: the check is then unavailable, never a refusal. The credential's
   name and where it goes — a header or a query parameter — are the service's contract, written in your code.

3. **The call**, through one of the engine's bases rather than `FieldAction` directly:
   - [`ProviderFieldAction`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/ProviderFieldAction.java)
     serves the endpoint `configure` read, skips a blank value (`concerns`, which you may narrow), and turns every way
     the call can fail — not configured, unreachable, timed out, a URL the endpoint rule refuses, a development
     endpoint while the administrator's switch is off — into an unavailable check. You write `ask(endpoint, request)`
     and `gateway()`;
   - [`EmailVerificationFieldAction`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/EmailVerificationFieldAction.java)
     narrows it to email addresses — a value that is not one is accepted without a call. You write
     `verify(endpoint, address)`.

The gateway appends your relative path to the endpoint's base URL, injects the credential, applies the engine's
timeouts, caps the answer and never logs the credential ([Field actions: services and limits](../administration/field-actions.md)).

```java
@Component(service = FieldAction.class, configurationPid = MailboxFieldAction.PID)
@Designate(ocd = MailboxFieldAction.Config.class)
public class MailboxFieldAction extends EmailVerificationFieldAction {

    static final String PID = "com.myco.mailbox";

    @ObjectClassDefinition(name = "Formidable — Field actions — Mailbox check (My company)")
    public @interface Config {
        String url() default "https://api.example.com/v1";
        /** The file's .credential: a private property, never published with the service. */
        @AttributeDefinition(type = AttributeType.PASSWORD) String _credential() default "";
        boolean development() default false;
    }

    @Reference
    private FieldActionGateway gateway;

    @Activate
    @Modified
    public void activate(Config config) {
        configure("Mailbox service", config.url(), config._credential(), config.development(),
                "X-Api-Key", FieldActionGateway.Endpoint.CREDENTIAL_IN_HEADER);
    }

    @Override
    public String getNodeType() {
        return "myco:mailboxAction";
    }

    @Override
    protected FieldActionGateway gateway() {
        return gateway;
    }

    @Override
    protected FieldActionResult verify(FieldActionGateway.Endpoint endpoint, String address) throws IOException {
        FieldActionGateway.Response response = gateway().get(endpoint, "verify?email=" + URLEncoder.encode(address, UTF_8));
        if (response.status() != 200) {
            return FieldActionResult.unavailable("service " + response.status());
        }
        return json(response)
                .map(body -> body.optBoolean("deliverable") ? FieldActionResult.accept() : FieldActionResult.reject("undeliverable"))
                .orElse(FieldActionResult.unavailable("not the service's JSON"));
    }
}
```

The samples' [`ExperianEmailFieldAction`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ExperianEmailFieldAction.java)
and [`ZeroBounceEmailFieldAction`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ZeroBounceEmailFieldAction.java)
are that one method each, against two real providers — example implementations, not supported connectors.

## Step 6: Answer with the right verdict

A field action answers, it never throws at the visitor and never writes to the repository:

- `FieldActionResult.accept()` — the value passes
- `FieldActionResult.reject(detail)` — the value is refused: the contributor's message is shown under the field,
  and the submission is blocked or only warned, as the contributor set
- `FieldActionResult.unavailable(detail)` — the check could not conclude: the contributor's *If the check cannot
  run* setting decides

Three rules the samples follow:

- `reject` only what you are sure of — a mailbox the provider says cannot receive mail, a word the contributor
  listed. Anything the service could not conclude is `unavailable`, never a refusal
- the `detail` is a word for the logs; the visitor reads the contributor's message, which the engine interpolates
  (`${value}`) and escapes — do not build a visitor text
- an exception escaping `execute` counts as `unavailable` and is logged by its type only: catch what you can name,
  and never put the value in an exception message you let escape

## Step 7: Let contributors add it

Nothing to register. Once the module is deployed, a contributor switches **Enable field actions** on in a field's
editor — offered on every field whose value is text (`fmdbmix:submittableField`), third-party fields included, not
on a file field — and adds
the check from the field's authoring zone in the Page Builder: the chooser lists every deployed type extending
`fmdbmix:fieldAction`, with your label, tooltip and icon.

## Security guidance

A field action is trusted server-side code reached from an endpoint anonymous visitors can call.

- keep every URL and credential in your module's configuration file, set by the administrator, never in content or code
- a value is untrusted input: bound what you do with it, and never interpolate it into a path without encoding it
  (the gateway refuses an absolute path, a scheme, `..` or a control character, not a badly encoded query)
- every call may be paid: prefer the `submit` trigger for a costly check, and remember the rate limit, the value
  length bound and the verdict cache are the only bounds on the pre-check
- do not log the value; the dispatcher logs the action, the field and the verdict

## Troubleshooting

If your check never refuses anything, check:

1. the class is annotated with `@Component(service = FieldAction.class)` and the component is active
2. the node type extends `fmdbmix:fieldAction`
3. `getNodeType()` exactly matches the primary node type
4. the form is published: the dispatcher reads the action in `live`
5. for a check calling a service, its module's configuration has a credential and a URL the endpoint rule accepts
   (the log says so once when it does not), and the service answers

Useful runtime symptom: the engine logs `Field action <id> (<type>) could not run on field '<field>' … the value is
accepted, as the contributor set` when the check was unavailable — no matching service, an exception, a service
not configured or that did not answer.

## Testing it

- **Unit**: drive `execute` with a mocked node, or — for a check calling a service — `judge(request)`, the seam
  `ProviderFieldAction` offers, with an endpoint and a fake gateway answering what the service documents. The
  samples' tests (`ExperianEmailFieldActionTest`, `ZeroBounceEmailFieldActionTest`) are the shape.
- **End to end**: register a double of the service in your module — a small servlet answering as the service
  does, as the samples' [`ZeroBounceStubServlet`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ZeroBounceStubServlet.java)
  does on their [`ProviderStubServlet`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ProviderStubServlet.java)
  — and point your configuration at it with `development=true`, the engine's `enableDevFieldActionEndpoints` on. Spec 74 of this repository drives the two samples
  that way.

## Related examples in this repository

- `jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/BlockedWordsFieldAction.java` — a check that calls nothing
- `jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/EmailDomainFieldAction.java` — a check that asks the domain name system, no provider
- `jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ExperianEmailFieldAction.java`, `ZeroBounceEmailFieldAction.java` — mailbox checks behind a provider

## What the Page Builder shows about your action

The field's authoring zone lists its checks as the form's zone lists its actions, with the same card: the type
icon, the title, the action's key parameter and, smaller, the type description, resolved in the editor's UI
language from your declarations — the icon and tooltip of Step 1, and the first telling property your type
declares after `jcr:title` (see [How to create a form action](how-to-create-form-action.md#what-the-page-builder-shows-about-your-action)
for the rule). A type shipping no icon is drawn with the marker's glyph on the card. The card never shows the
feedback settings: they are read in the action's editor.

To shape your own card, register a `hidden.authoring` view on your type in a JavaScript module: Formidable's card
view sits at priority -1 on the marker, so yours wins at the default priority.
