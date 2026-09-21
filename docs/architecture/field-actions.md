# Field actions

> Decided with the developer on 2026-09-21 (issue #341), from the draft of 2026-09-11. The engine half is
> shipped by the pull request that adds this page; the library helpers, the editor zone and the built-in
> email check follow (see "Roadmap").

## Overview

A **field action** is a piece of content attached to one form field that judges the field's candidate value
against something the browser cannot know — an external service (an email deliverability provider, a postal
address service, a customer directory), a repository lookup, a business rule. It runs **server-side, at the
visitor's request while the form is being filled** (on leaving the field, or at submission, as the contributor
set), and **again when the submission is parsed**, before any form action runs. Its refusal reaches the visitor
as a message anchored on the field, in the visitor's language, written by the contributor.

It is the form action's twin, one level down:

| | Form action | Field action |
|---|---|---|
| Attached to | the form (`actions` list) | one field (`actions` list under the field) |
| Runs | after the submission is accepted, once | before it is accepted — at blur or submit from the browser, and in the pipeline |
| Side effects | expected (store, email, forward) | forbidden — a field action only answers |
| Declared by | a node type taking `fmdbmix:formAction` | a node type taking `fmdbmix:fieldAction` |
| Implemented in | Java (`FormAction` OSGi service) | Java (`FieldAction` OSGi service) or **JavaScript** (a `hidden.execute` server view) |
| Contributor UI | the actions zone under the form, Content Editor for each action | a switch in the field's editor form, a zone under the field, Content Editor for each action |

Everything a third-party module needs to add its own field-action type is what it needs today to add an
action type — a CND, a view or a service, resource bundles.

## The problem it solves

Formidable validates the shape of a value (required, pattern, length, bounds, type semantics) in the
browser and again in the pipeline (`FieldValidator`, step 9). Nothing let a project say "and this email
must actually exist" or "this customer number must be known to our CRM". The only hook was a custom form
action, and an action is the wrong tool: it runs after the submission is accepted, behind the store and
the emails, with no way to tell the visitor *which* value was wrong — the form shows its global error. A
refusal there is a stored, emailed, then refused submission.

## Architecture

```
 browser                        formidable-engine (Java, generic)                     the action's code
 ───────                        ─────────────────────────────────                     ─────────────────
 blur / submit ──POST──▶ FieldActionServlet  /modules/formidable-engine/field-action
   {field, value, trigger}   │ security-filter scope formidable-field-action (origin: hosted)
                             │ fid → form in live, field found BY NAME under it (FieldActionCollector)
                             │ value length cap · rate limit per client · verdict cache
                             ▼
                        FieldActionDispatcher ── for each action of the field, in list order ──┐
                             │  1. a Java FieldAction registered for the node type?  ──────────┼─▶ execute(node, request)
                             │  2. else render the node's `hidden.execute` view      ──────────┼─▶ hidden.execute.server.tsx
                             │     (RenderService, expiration=0, request attribute)           │      server.osgi.getService(FieldActionGateway)
                             │  verdict cache (action id + trimmed value, TTL)       ◀────────┘      credential from .cfg, never in JS or JCR
                             ▼
 ◀──── JSON {verdict: accept | advice | reject, messages: [{level, html, field, actionId, actionType}]}

 submit ──POST──▶ FormSubmitServlet → pipeline step 11b runFieldActions → the same dispatcher, the same cache
                                                     → FMDB-015 + messages[] on a blocking refusal
```

**The server runs the action, twice, from one code.** The browser never runs a field action itself: it
asks. On blur or submit of a field carrying actions, the page calls the pre-check endpoint, which runs the
field's actions and answers a verdict and the messages; the page shows them under the field. Then the
pipeline runs the *blocking* actions again at submission — the pre-check is a courtesy for the visitor,
the pipeline is the authority, and a browser that skipped the pre-check meets the same actions there. The
shared verdict cache makes the honest browser's second run free. Three reasons hold this shape: the
provider's credential never leaves the server; a value the browser was told was fine is checked again
where the data is accepted; and a JavaScript module cannot expose an endpoint of its own.

**Why a view is the JavaScript entry point.** A Jahia JavaScript module cannot expose an HTTP endpoint,
and the JavaScript modules engine exports none of its packages (its manifest lists 166 exported packages,
none under `org.jahia.modules.javascript`, measured on `javascript-modules-engine 1.3.0-SNAPSHOT`), so a
Java module cannot link against `GraalVMEngine` to call a registered function. What Java *can* do with a
JavaScript module is what the platform does: render one of its views through `RenderService.render`. A
view named `hidden.execute` registered on the field-action type is therefore the contract: the engine
renders it with the candidate value in a request attribute, the view answers a small JSON body. Request
attributes are already how the engine talks to the JavaScript views (`CaptchaRenderFilter`), and
`server.osgi.getService(<class name>)` already how the views call back into Java. The registry-based SDK
that would have offered a direct call (javascript-modules#686) was closed unmerged; should one ship, the
dispatcher gains a second lookup and the view stays valid.

## JCR definitions

The split follows [CND module ownership](cnd-module-ownership.md): **markers the engine keys on live in
the engine and carry no property; what the contributor configures is a property mixin attached through
a marker with `extends`; concrete types live with the code that implements them.**

### `formidable-engine` — `META-INF/definitions.cnd`

```cnd
// Marker: what the node IS. Concrete field-action types take it as a supertype, whatever module
// declares them, exactly as action types take fmdbmix:formAction. A field action answers; it never writes.
[fmdbmix:fieldAction] mixin

// What the contributor sets on every field action, whatever its type — attached through the marker with
// `extends`, so a third-party type gets it with nothing to opt into.
[fmdbmix:fieldActionFeedback] mixin
 extends = fmdbmix:fieldAction
 - rejectionMessage (string, richtext[…]) = resourceBundle('fmdbmix_fieldActionFeedback.rejectionMessage.default') autocreated i18n indexed=no
 - trigger (string, choicelist[resourceBundle]) = 'blur' autocreated indexed=no < 'blur', 'submit'
 - severity (string, choicelist[resourceBundle]) = 'block' autocreated indexed=no < 'block', 'warn'
 - whenUnavailable (string, choicelist[resourceBundle]) = 'accept' autocreated indexed=no < 'accept', 'reject'

// The per-field list, fmdb:actionList in miniature. Orderable: the first blocking refusal wins.
[fmdb:fieldActionList] > jnt:content, jmix:list, mix:title, jmix:systemNameReadonly
 orderable
 - jcr:title (string) = resourceBundle('fmdb_fieldActionList') autocreated i18n
 + * (fmdbmix:fieldAction) = fmdbmix:fieldAction version

// The switch in the field's own Content Editor form — "this field has actions".
[fmdbmix:fieldActions] > jmix:dynamicFieldset mixin
 extends = fmdbmix:formElement
 + actions (fmdb:fieldActionList) = fmdb:fieldActionList autocreated
```

Same shape as the form actions — one marker, one list, one property mixin — plus the switch, and the same
precedents, line for line: `fmdb:actionList`'s `+ * (fmdbmix:formAction) = fmdbmix:formAction version` for
the list; `fmdbmix:manualOptions > jmix:dynamicFieldset … extends = fmdbmix:optionsSource` for a dynamic
fieldset the Content Editor toggles into a mixin; `fmdbmix:fixedMinDate … extends = fmdbmix:dateBounds` and
`fmdbmix:jExperienceProfileMapping … extends = fmdbmix:profileMappableField` for a property mixin attached
to a marker with `extends`. The child under the field is named `actions`, as it is under the form: the
same word for the same object, the parent saying which.

**The switch.** Turning the fieldset on adds `fmdbmix:fieldActions` to the field on save, and adding a
mixin autocreates the child nodes it declares (Jackrabbit's `AddMixinOperation` walks the autocreated
node definitions): the list appears. Turning it off removes the mixin, and removing a mixin deletes the
child nodes it defines: the list and every action in it go — the switch's label says so.
`fmdbmix:formElement` itself is untouched.

**The four settings** are the contributor's calls, per form, hence properties, not facts of the type:
when the browser asks (`blur` for a cheap check, `submit` for a paid one — the pipeline runs the blocking
ones again regardless), whether a refusal blocks the submission or only warns, and what an unanswered
check means (`accept`: a provider outage never blocks visitors; `reject`: a form that must not pass
unchecked). A node saved outside the editor may lack the mixin or a property: the engine reads the CND
defaults (`ResolvedFieldAction`).

### A field-action type — in the module that implements it

The concrete type is declared next to its code, like a field type next to its view. The engine never
names the concrete type: it reaches it through the markers.

```cnd
// A module of its own: the type takes the marker, carries its own settings, and either a Java service
// or a hidden.execute view bound to it judges the value. The four feedback settings arrive through
// `extends`, unasked.
[myco:crmLookupAction] > jnt:content, fmdbmix:fieldAction, mix:title
 - providerId (string, choicelist[formidableFieldActionProviders]) mandatory indexed=no
```

The samples module ships one: `fmdbsample:blockedWordsAction` (a `words` list, a value containing one is
refused), implemented by `BlockedWordsFieldAction` in Java — the shape to copy.

## Execution

### A field action in Java

```java
@Component(service = FieldAction.class)
public class CrmLookupAction implements FieldAction {
    @Override public String getNodeType() { return "myco:crmLookupAction"; }

    @Override
    public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        String providerId = actionNode.getProperty("providerId").getString();
        FieldActionGateway.Response response = gateway.post(providerId, "customers/lookup",
                "{\"number\":" + JSONObject.quote(request.value()) + "}");            // IOException → the engine reads it as unavailable
        if (response.status() != 200) return FieldActionResult.unavailable("provider " + response.status());
        return new JSONObject(response.body()).optBoolean("known") ? FieldActionResult.accept() : FieldActionResult.reject("unknown");
    }
}
```

`org.jahia.modules.formidable.engine.api` exports the contract: `FieldAction` (`getNodeType()`,
`execute(actionNode, request)`), `FieldActionRequest` (form UUID, field name, value, locale),
`FieldActionResult` (`accept()`, `reject(detail)`, `unavailable(detail)`) and `FieldActionGateway`. The
action node is read in `live` in a system session; an exception escaping `execute` counts as unavailable
and is logged.

### A field action in JavaScript — the `hidden.execute` view

```tsx
jahiaComponent(
  { componentType: "view", nodeType: "myco:crmLookupAction", name: "hidden.execute",
    properties: { "cache.expiration": "0" } },                              // never cached: a verdict is about one value
  ({ providerId }, { renderContext }) => {
    const raw = renderContext.getRequest().getAttribute("formidable.fieldAction");   // null on a direct URL hit: the view is not an endpoint
    if (!raw) return null;
    const request = JSON.parse(raw) as { formId: string; fieldName: string; value: string; locale: string };
    const gateway = server.osgi.getService("org.jahia.modules.formidable.engine.api.FieldActionGateway");
    const response = gateway.post(providerId, "customers/lookup", JSON.stringify({ number: request.value }));
    if (response.status() !== 200) return `{"verdict":"unavailable","detail":"provider ${response.status()}"}`;
    return JSON.parse(response.body()).known ? `{"verdict":"accept"}` : `{"verdict":"reject","detail":"unknown"}`;
  },
);
```

- **Input**: the request attribute `formidable.fieldAction`, a JSON object `{formId, fieldName, value,
  locale}` the engine sets before rendering; absent on a direct hit of the view through the render servlet,
  which is why the view answers nothing then. The value never travels in a URL.
- **Output**: the view's body is one JSON object `{"verdict": "accept" | "reject" | "unavailable",
  "detail"?: string}` — whitespace or markup the render chain adds around it is tolerated; anything else is
  read as unavailable, the text kept for the logs. The **visitor-facing text is not the view's business**:
  the engine reads the contributor's `rejectionMessage` on the node in the visitor's locale, interpolates
  and escapes it.
- **Caching**: the view declares `cache.expiration=0` and the engine sets the `expiration` request
  attribute to `0` before rendering, which has priority over the view (`AggregateCacheFilter`); the cache
  layer stores nothing whose expiration is not `> 0`. Belt and braces, because a cached verdict keyed on
  the node would answer every visitor with the first one's result.
- **Side effects**: none. The engine renders the view in the visitor's request, the node read in `live`.

`@jahia/formidable-library` will carry `readFieldActionRequest(renderContext)` and `fieldActionResult`
helpers for this contract (roadmap, step 2); the raw attribute and the JSON string above are the contract
itself.

### The dispatcher

`FieldActionDispatcher` runs a field's actions in list order. The trigger filter (a blur pre-check runs
the blur actions, a submit runs them all) and the pipeline's `blockingOnly` rule (a warning action had
its say at the pre-check) decide whether an action runs; a Java `FieldAction` registered for the node
type is executed, failing that the view is rendered and its verdict parsed. `ACCEPT` moves on. `REJECT`
becomes one `FieldActionMessage` — level `error` when the action blocks, `warning` otherwise; the
contributor's `rejectionMessage` in the visitor's locale, `${value}` and the other submitted values
interpolated through `TemplateInterpolator` with `FieldEscaper.html`, the rich text itself trusted as the
form's responses are; the field name, the action's id and type — and ends the run when the action blocks,
since the first blocking refusal wins. `UNAVAILABLE` is what the contributor's `whenUnavailable` says.

The **verdict cache** (`VerdictCache`) keeps `ACCEPT` and `REJECT` per action id and trimmed value for
`fieldActionVerdictCacheTtlSeconds`, bounded at ten thousand entries; `UNAVAILABLE` is a moment's truth
and is not kept. `FieldActionRuntime`, one OSGi component, holds the registered Java actions, the cache,
the endpoint's rate limiter and the dispatcher built on them, and both servlets reference it — so the
verdict the pre-check gave is the one the pipeline finds.

### The endpoint — `POST /modules/formidable-engine/field-action?fid=<form UUID>&lang=<lang>`

Body `{"field": "<field node name>", "value": "<candidate>", "trigger": "blur" | "submit"}`, answer
`{"verdict": "accept" | "advice" | "reject", "messages": [{"level": "error" | "warning", "html": "…",
"field": "…", "actionId": "…", "actionType": "…"}]}` — `advice` when only warnings came back. Registered as
`FormSubmitServlet` is (HTTP whiteboard, `alias=/formidable-engine/field-action`), gated as it is: its own
security-filter scope `formidable-field-action` (`auto_apply: origin: hosted`), one more pattern in the
CSRFGuard whitelist, `PermissionService.hasPermission({api: "formidable-field-action"})` before anything
is read. Then, in order:

1. `fieldActionRateLimitPerMinute = 0` switches the endpoint off — a 404 without a code; the field actions
   then run at submission only.
2. `fid` UUID-validated, `lang` a valid language tag — `FMDB-002` as the pipeline answers.
3. The body read up to the value cap plus what the syntax may add, refused unread beyond — `FMDB-003`; a
   body that is not a JSON object, or a `field` that is not a node name, `FMDB-002`; a `value` longer than
   `fieldActionMaxValueLength`, `FMDB-003`.
4. Rate limit per client address (`fieldActionRateLimitPerMinute`, default 30) — `FMDB-016`, 429. The
   endpoint is an open door to a possibly paid service for anyone on the site; the limit and the cache are
   what make it affordable.
5. The form resolved in `live` by its UUID, in a system session, and the field found **by node name under
   it** (`FieldActionCollector.collect`) — never `getNodeByIdentifier` on a client-supplied id, so the
   endpoint cannot be pointed at an arbitrary node. No such form, not a form, or no such field with
   actions: `FMDB-004`.
6. A blank value is accepted without running anything — an unanswered field says nothing to check, and
   the pipeline skips it too. Otherwise the dispatcher runs the field's actions for the declared trigger.

### The pipeline — step 11b `runFieldActions`

Between `validateRequired` (11) and `dispatchActions` (12): for every field whose metadata carries
actions (`FormFieldMetadataCollector.Result.fieldActions`, read in the same walk as the constraints, so
the repository is read once), whose submitted value is not blank and that the logic evaluator does not
hold hidden, the dispatcher runs the **blocking** actions with every trigger — a multi-valued field is
judged on its first non-blank value. The first refusal is `SubmissionException(FMDB_015, 422)` carrying
the messages, which the servlet writes in a `messages` array next to `errorCode`, so the browser anchors
them on the field exactly as the pre-check did. `messages` joins the servlet's reserved keys: an enricher
cannot take it. Warning actions do not run here: they warned.

## Providers, configuration and secrets — the `FieldActionGateway`

A provider's credential goes neither in the JavaScript nor in the repository. As forward targets are
declared, providers are declared in `org.jahia.modules.formidable.cfg`:

```
# --- FIELD ACTIONS ---
# Each entry: id|Label|https://base-url|Credential-Header-Name|credential (the last two together, or neither)
fieldActionProviders=
fieldActionHttpConnectTimeoutSeconds=5
fieldActionHttpRequestTimeoutSeconds=10
fieldActionVerdictCacheTtlSeconds=300      # 0 disables the cache
fieldActionRateLimitPerMinute=30           # 0 disables the endpoint
fieldActionMaxValueLength=512
```

`FormidableConfigService` parses them (`FieldActionProvider`, whose `toString` masks the credential;
`FieldActionSettings`, everything the field actions read, in one piece), the HTTPS-only rule of the
forward targets applied to the base URL. `FormidableFieldActionProvidersInitializer` feeds the
`formidableFieldActionProviders` choicelist (label shown, id stored). `FieldActionGatewayImpl` is the
exported service: it resolves the provider, refuses a path that is absolute, carries a scheme, climbs with
`..` or holds a control character, appends it to the base URL (a base without a trailing slash keeps its
last segment), injects the credential header, applies the timeouts, caps the response body at a million
characters, and never puts the credential in a log line or in the object it returns.

## Security and trust

- **The endpoint is the only entry**, with the submission's posture: `origin: hosted` scope, CSRFGuard
  whitelist, guest-readable published content only. A `hidden.execute` view is renderable through the
  platform's render servlet by anyone who can read the node — which is why the view answers nothing without
  the engine's request attribute, which no URL can set.
- **The credential never leaves the engine**: `.cfg` → gateway; the JavaScript sees a provider *id*; the
  repository stores a provider *id*.
- **SSRF**: the gateway only ever calls the configured base URLs; the path is relative-only, on the same
  host and scheme.
- **Abuse of a paid API**: rate limit per client, verdict cache, blocking actions only in the pipeline,
  `0` to switch the endpoint off.
- **Information disclosure**: the visitor gets the contributor's message and a verdict, never the provider's
  response; `detail` stays in the logs. The candidate value is never in a URL, nor in a log line at INFO.
- **The pipeline is the authority.** The pre-check is a courtesy; the submit-time re-check is what protects
  the data — a browser that skips the pre-check meets the same actions.

## Performance

A blur-time call costs one servlet hit, one walk of the published form in a system session, one action
run — a provider call at most. The verdict cache makes submit free for values already checked; the rate
limit bounds a hostile client. The pipeline re-check adds one provider call per blocking action whose
value was never pre-checked.

## Decision log

| Date | Decision | Why |
|---|---|---|
| 2026-09-11 | **View, not direct polyglot call** for a JavaScript action | The JS engine exports no package, so `GraalVMEngine.doWithContext` is unreachable without reflection against a private API; the render service is the platform's sanctioned way in, and it gives the view the `server` helpers for free |
| 2026-09-11 | **Verdict + contributor message, not view-authored text** | One place for the visitor's words (the node, i18n, in the Content Editor), one escaping path (the engine), and an action whose author never touches copy |
| 2026-09-11 | **One marker, one property mixin — not a marker per behaviour** | Whether a refusal blocks and what an outage means are the contributor's calls, per form, so they are properties with the safe defaults (`block`, `accept`), not facts of the type. An earlier draft had nine value-kind markers and two behaviour markers: dropped |
| 2026-09-11 | **A dynamic-fieldset switch, not a create-on-first-add list** (HDU) | The feature is turned on in the field's own form, where every other setting of the field is, and the list exists exactly when the switch is on — `autocreated` on the mixin does the creation, the platform's `removeMixin` the deletion. The alternative, a hidden `actions` child on `fmdbmix:formElement` created by the zone's first click, touched the engine marker and left the feature discoverable only from the Page Builder |
| 2026-09-11 | **Re-run in the pipeline, not a signed proof from the pre-check** | A proof token is more machinery and a replay surface; a cached re-check is one lookup |
| 2026-09-21 | **The vocabulary is `fieldAction`, mirroring `formAction`** (HDU) — `fmdbmix:fieldAction`, `fmdb:fieldActionList`, `fmdbmix:fieldActions`, `fmdbmix:fieldActionFeedback`, `FieldAction`, the child named `actions` | More generic than "validator" — a field action may one day do more than validate — and it reads beside the form actions without a second word. It also dissolves the collision with the engine's `FieldValidator` class, the format validation of step 9: nothing new is called a validator |
| 2026-09-21 | **The view is named `hidden.execute`**, not `hidden.validate` (HDU) | The action's code, whatever it does |
| 2026-09-21 | **The server runs the action, twice, from one code** (HDU asked whether the front proxies the back: yes) | The browser only asks; the pre-check endpoint and the pipeline's step 11b share the dispatcher and the verdict cache. Three reasons: the credential stays server-side, the pipeline is the authority, a JavaScript module has no endpoint of its own |
| 2026-09-21 | **Field actions stand alone; `messages[]` is a servlet key** written by the pipeline for both outcomes, the enricher SPI unchanged | The draft made the "action outcomes" design (per-action visitor messages, gating actions) a prerequisite, its `messages` array being the substrate. The refusal needs only the array: the field actions add it, minimal, and action outcomes will write into it. The response-enrichment SPI shipped meanwhile covers accepted submissions only and runs after the actions — a rejection's messages are the pipeline's, not an enrichment's, so `messages` is reserved like `success` and `errorCode` |
| 2026-09-21 | **No dependency on the TypeScript form-actions registry** (#164) | Its SDK (javascript-modules#686) was closed unmerged on 2026-07-22; no registry entry point exists or is scheduled. The view is the entry point, not a workaround |
| 2026-09-21 | **The bundle's default message never falls back to the server's locale** | `ResourceBundle` would serve a French server's `_fr` bundle to an English visitor when no `_en` file exists; the lookup uses the no-fallback control so the base bundle answers. Found by the unit test on a French machine |

## Open questions

- Credentials as `${env:…}` references in `.cfg`: depends on the platform's configuration interpolation
  being enabled; to be verified, otherwise the file holds the literal.
- Whether the pre-check should also run for a field hidden by conditional logic at the moment of the call
  (today: yes, the browser decides what it asks about; the pipeline skips hidden fields).
- A `warn` refusal from a `submit`-triggered action: shown once and the submission proceeds, or a confirm
  step? v1: shown, proceeds.

## Roadmap

1. **Engine** — CND, `FieldAction` and `FieldActionGateway` API, configuration keys and provider list,
   dispatcher, endpoint, pipeline step 11b, `FMDB-015`/`FMDB-016`, `messages[]`, unit tests; the samples
   module's `fmdbsample:blockedWordsAction`. **Shipped 2026-09-21** (this page's pull request).
2. **Library** — `readFieldActionRequest`, `fieldActionResult`, `fieldActionAttributes` (spreads
   `data-fmdb-field-action="blur" | "submit"` on the control), `useFieldActions` (blur/submit → endpoint →
   `setCustomValidity` + the field-error rendering, `settleFieldActions(form)` before the XHR).
3. **Elements** — the zone under the field in edit mode (`FieldActionList` and `FieldAction`
   `hidden.authoring` views, replicas of the actions zone, `AddContentButtons` offering every deployed
   type through `ActionSummaryService.describeType`), the form island wiring, the styling hooks
   `fmdb-form-warning` and `fmdb-field-action-pending` in `docs/styling/`.
4. **Built-in and samples** — `fmdb:emailDeliverabilityAction` next to the email input, a Cypress spec on
   the sample action against the endpoint and the pipeline, the extension how-to case "Adding a field
   action type", the `.cfg` keys in `docs/administration/`.

## Sources

- `formidable-engine/…/api/FieldAction.java`, `FieldActionRequest.java`, `FieldActionResult.java`,
  `FieldActionGateway.java`; `…/fieldactions/` (`FieldActionDispatcher`, `FieldActionCollector`,
  `FieldActionRuntime`, `FieldActionServlet`, `FieldActionGatewayImpl`, `RenderServiceViewRenderer`,
  `ResolvedFieldAction`, `VerdictCache`, `RateLimiter`); `servlet/FormSubmissionPipeline.java` (step 11b),
  `servlet/FormFieldMetadataCollector.java` (`Result.fieldActions`), `servlet/FormSubmitServlet.java`
  (`messages`, `RESERVED_KEYS`); `config/FormidableConfig.java`, `config/FormidableConfigService.java`
  (`FieldActionProvider`, `FieldActionSettings`); `choicelist/FormidableFieldActionProvidersInitializer.java`;
  `META-INF/definitions.cnd`, `META-INF/configurations/org.jahia.modules.formidable.cfg`,
  `org.jahia.bundles.api.authorization-formidable-engine.yml`, `org.jahia.modules.jahiacsrfguard-formidable.cfg`.
- `jahia-test-module/formidable-test-module-samples-java/…/actions/BlockedWordsFieldAction.java` and its CND.
- [Form submission flow](form-submission-flow.md), [CND module ownership](cnd-module-ownership.md),
  [Custom validation](custom-validation.md), the extension guide's action case, issue #341.
- Platform: `RenderService.render(Resource, RenderContext)`, `AggregateCacheFilter` (expiration lookup
  order: request attribute, node, view), `CacheFilter` (caches only `expiration > 0`),
  `javascript-modules-engine 1.3.0-SNAPSHOT` manifest (no own package exported).
