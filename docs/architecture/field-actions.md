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
| Implemented in | Java (`FormAction` OSGi service) | Java (`FieldAction` OSGi service) |
| Contributor UI | the actions zone under the form, Content Editor for each action | a switch in the field's editor form, a zone under the field, Content Editor for each action |

Everything a third-party module needs to add its own field-action type is what it needs today to add an
action type — a CND, a service, resource bundles.

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
                             │ value length cap · rate limit per client
                             │ fid → form read in the VISITOR's live session (readable? a form? members-only → FMDB-009)
                             │ field found BY NAME under it (FieldActionCollector, walk cached per form and locale)
                             ▼
                        FieldActionDispatcher ── for each action of the field, in list order ──┐
                             │  the Java FieldAction registered for the node type  ───────────┼─▶ execute(node, request)
                             │  (none deployed for the type: an unavailable check)          │      a provider? → FieldActionGateway
                             │  verdict cache (action id + locale + trimmed value)  ◀────────┘      credential from .cfg, never in JCR
                             ▼
 ◀──── JSON {verdict: accept | advice | reject, messages: [{level, html, field}]}

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

**JavaScript is the visitor's page, not the action's code.** The JavaScript of the field actions is the
browser side — `useFieldActions`, the zone under the field — never the check itself, which is a Java
`FieldAction` a module registers. A server-side JavaScript path (a `hidden.execute` view rendered by the
engine and read as a JSON verdict) was built with the engine and withdrawn on 2026-09-25, before any release
(decision log): it answered a question nobody had asked, and it sidestepped the one still open with #164 —
form actions written in TypeScript — which waits for a server-extension SDK of the JavaScript modules. When
that SDK ships, form actions and field actions written in JavaScript will go through the same registry.

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

// The switch in the field's own Content Editor form — "this field has actions". Offered on every field
// type taking fmdbmix:submittableField (the fields with a value) and on nothing else.
[fmdbmix:fieldActions] mixin
 extends = fmdbmix:submittableField
 + actions (fmdb:fieldActionList) = fmdb:fieldActionList autocreated
```

Same shape as the form actions — one marker, one list, one property mixin — plus the switch, and the same
precedents, line for line: `fmdb:actionList`'s `+ * (fmdbmix:formAction) = fmdbmix:formAction version` for
the list; `fmdbmix:fixedMinDate … extends = fmdbmix:dateBounds` and
`fmdbmix:jExperienceProfileMapping … extends = fmdbmix:profileMappableField` for a mixin attached to a
marker with `extends`, which is also what gives this one its switch. The child under the field is named `actions`, as it is under the form: the
same word for the same object, the parent saying which.

**The switch.** Turning the fieldset on adds `fmdbmix:fieldActions` to the field on save, and adding a
mixin autocreates the child nodes it declares (Jackrabbit's `AddMixinOperation` walks the autocreated
node definitions): the list appears. Turning it off removes the mixin, and removing a mixin deletes the
child nodes it defines: the list and every action in it go — the switch's label says so.

**Which fields offer it.** The mixin extends `fmdbmix:submittableField`, the engine's positive marker of a
field with a value, declared as a supertype by every built-in and extended field type but the file input,
and by a third-party field the way it declares `fmdbmix:profileMappableField`. The first cut extended
`fmdbmix:formElement`, and that marker is wider than it reads: the fieldset takes it for its title and its
logic rules, the button through `fmdbmix:element` — both `fmdbmix:nonSubmittable`, both offered a switch
whose actions could never run, since the pipeline judges `formElement && !nonSubmittable` and `extends`
cannot name a subtraction. The pipeline keeps its own test: a field type that has not adopted the marker is
still submitted, it only lacks the switch. A file field is left out on purpose — what it submits is a file,
not a value a check judges — so the switch never invites an action that would never run. Two things hold the
split: `scripts/check-field-markers.mjs`, run by CI, refuses a type extending `fmdbmix:element` or
`fmdbmix:formElement` that declares none or several of `submittableField`, `nonSubmittable`, `fileField` —
so the next built-in field type cannot forget its marker in silence; and the Cypress spec 223 reads the
editor form of a text, a rating, a file, a button, a fieldset and a step, and finds the switch on the first
two only, wherever they sit.

**Why the mixin does not take `jmix:dynamicFieldset`**, although the fieldset is dynamic. That supertype
extends `jmix:templateMixin`, and the Content Editor gives no enable switch to a `jmix:templateMixin`:
`hasEnableSwitch = !nodeType.isNodeType("jmix:templateMixin")`, and a fieldset with neither a switch nor
one visible field is dropped from the form altogether (`EditorFormServiceImpl`, the two lines that close
the fieldset loop). This mixin carries **no property** — it only autocreates the list — so with that
supertype the editor rendered nothing at all and the feature could not be turned on by a contributor.
What makes a fieldset dynamic there is `extends`, nothing else. The options fieldsets keep
`jmix:dynamicFieldset` for the opposite reason: a choicelist activates them through the `addMixin` wiring
of their overrides, so they must not offer a switch of their own.

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
// A module of its own: the type takes the marker, carries its own settings, and a Java service
// bound to it judges the value. The four feedback settings arrive through
// `extends`, unasked. A type calling a provider takes fmdbmix:providerFieldAction too: the id of one
// of the providers the administrator declared, with its label and its choicelist, from the engine.
[myco:crmLookupAction] > jnt:content, fmdbmix:fieldAction, fmdbmix:providerFieldAction, mix:title
 - jcr:title (string) = resourceBundle('myco_crmLookupAction') autocreated i18n
```

The `jcr:title` line is what fills the action's title with the type's own label when a contributor
creates one, as every built-in form action does — without it the card and the content tree show a bare
system name.

**Ship an icon too**, at `src/main/resources/icons/myco_crmLookupAction.png`, 16×16. The platform's
fallback does not help here, whatever it looks like: `JCRContentUtils.getIcon` walks `getSupertypes()`
in order and takes the first one with a file, and `nt:base` — which ships one — comes long before
`fmdbmix:fieldAction` in that list. Measured: a sample type carrying the marker and no icon resolved to
`/modules/assets/icons/nt_base`, the generic sheet, and resolved to its own glyph the moment the file
existed. The engine's `icons/fmdbmix_fieldAction.png` is therefore the marker's own drawing, not a
fallback for the types that take it; the samples module ships the same drawing under its type's name,
which is the shape to copy.

The samples module ships one: `fmdbsample:blockedWordsAction` (a `words` list, a value containing one is
refused), implemented by `BlockedWordsFieldAction` in Java — the shape to copy. It is a sample: it is installed
by the test provisioning manifest and reaches no product installation.

### The samples' second one — `fmdbsample:emailDomainAction`

**The engine ships no concrete field-action type at all.** It provides the mechanism — the markers, the list, the
dispatcher, the endpoint, the gateway — and a project decides which checks its forms deserve. What the samples
module ships is the shape to copy, and the second sample is the check a real project is most likely to want: does
the domain of the address exist? It asks the domain name system about the **domain** — never the address, so the
visitor's identity is not handed to a resolver — and it accepts as soon as a mail exchanger or an address record
answers. It needs no provider, no credential, no account and no configuration, which is what makes it a good
sample: it runs out of the box, in the playground and in the test suite.

**It refuses one thing only: a domain the resolver says does not exist.** That is the mistyped domain, which is
the case worth catching and the one that can be proved. Anything else is an unavailable check, which the
contributor's `whenUnavailable` decides. Two measurements are behind that rule, both made against the running
instance rather than assumed:

- asking for `MX`, `A` and `AAAA` **in one query** answers `DNS error` on resolvers that answer each of the
  three separately, so the lookup asks one question at a time;
- **Docker's embedded resolver returns nothing at all to a mail-exchanger question**, and most Jahia
  installations run behind one. An empty answer therefore proves nothing, and a first cut that read it as
  "this domain takes no mail" refused every address on such a host, `ada@jahia.com` included.

What it does not do is prove the mailbox exists, or even that the domain accepts mail. The help text says so,
and that is what a provider behind `FieldActionGateway` is for.

It carries one unit test in the samples module, which is the other half of what a module copying it inherits:
the seam is the lookup itself, so its rules are tested without a test of the network.

### Email verification behind a provider — the Experian and ZeroBounce samples

**Does the mailbox exist?** is the question a project asks once the domain check is not enough, and only a
provider that probes mailboxes can answer it. The samples answer it twice, against two providers — Experian
Email Validation v2 and ZeroBounce v2 — on one engine base, `EmailVerificationFieldAction`: each sample is a
type, `fmdbsample:experianEmailAction` and `fmdbsample:zeroBounceEmailAction`, both taking
`fmdbmix:providerFieldAction`, and one method, the call and the provider's own vocabulary turned into the
three verdicts. **Example implementations, not supported connectors**: the samples module reaches no product
installation, and a project copies the one it needs into a module of its own, with an account at that
provider. What the two agree on is what a form wants to know — does the address receive mail, and is it not
a trap or a throwaway — rather than what a mailing list wants, which is the providers' own recommendation.

**Experian** (`ExperianEmailFieldAction`): `POST email/validate/v2` under the provider's base URL, the address
in a JSON body, the token in the `Auth-Token` header the gateway injects; the answer's `result.confidence`:

| Experian answers | The action answers |
|---|---|
| `verified` — the mailbox exists, is reachable and receives mail | accept |
| `undeliverable`, `unreachable`, `illegitimate`, `disposable` — the band Experian documents as "reject" | reject: the contributor's message under the field |
| `unknown`, a timeout on the domain, an accept-all domain, a relay denied, a blank confidence — Experian could not conclude | unavailable: the contributor's `whenUnavailable` decides |
| 401 (token refused), 403 (no credits left), 408 (the provider's own timeout), 429, 5xx, an answer that is not the documented JSON | unavailable, never a refusal |

**ZeroBounce** (`ZeroBounceEmailFieldAction`): `GET v2/validate?email=…&ip_address=` under the base URL — the
provider reads its key off the URL, so its line ends in `|query` and the gateway appends `api_key` — and the
answer's `status` and `sub_status`:

| ZeroBounce answers | The action answers |
|---|---|
| `valid` | accept |
| `invalid`, `spamtrap`, `abuse` | reject |
| `do_not_mail` for a role or group address (`role_based`, `role_based_catch_all`) — it receives mail, whatever a mailing list should do with it | accept |
| `do_not_mail` for any other reason — `disposable`, `toxic`, `global_suppression`, `possible_trap`, a forwarding domain | reject |
| `catch-all`, `unknown`, a status the sample has never heard of | unavailable: `whenUnavailable` decides |
| a 200 carrying `error` — a refused key, an account out of credits, which is how the provider says it — or a status that is not 200, or not its JSON | unavailable, never a refusal |

**What they need.** One provider line each in the engine's configuration —
`fieldActionProviders=experian|Experian Email Validation|https://api.experianaperture.io|Auth-Token|<token>` and
`zerobounce|ZeroBounce|https://api.zerobounce.net|api_key|<key>|query` — where the credential stays: the classes
never see it. Outbound HTTPS from the Jahia server to the provider. And an account: Experian's documentation
offers a fourteen-day trial with two thousand validations, in Australia, Canada, New Zealand and the United
States only; ZeroBounce a free tier of a hundred validations a month, and sandbox addresses
(`valid@example.com`, `invalid@example.com`, `disposable@example.com`…) that answer without spending a credit.

**What to weigh before switching one on.** The **address leaves the platform**, where the domain check sends
the domain only — a project owes its visitors a word about it, and the samples' help texts say so; with
ZeroBounce it travels on the URL, as the provider documents its GET. **Every answer is chargeable**: a value that
is not an address is accepted without a call, the trigger `submit` keeps it to one call per submission attempt,
the verdict cache makes the pipeline's re-check free — and the rate limit is the only bound on a pre-check that a
captcha does not cover. The gateway's timeouts (five seconds to connect, ten in all) sit under the providers' own
defaults, so a slow lookup is an unavailable check, not a wait.

**Their doubles, `ExperianStubServlet` and `ZeroBounceStubServlet`.** The samples module also registers, at
`/modules/formidable-samples/experian-stub` and `/modules/formidable-samples/zerobounce-stub`, two servlets on
one base (`ProviderStubServlet`) that answer as the providers do — the same operation, the same credential (a
wrong one refused the way each provider refuses it: a 401, a 200 carrying `error`), the same JSON — with the
verdict decided by the address instead of a mailbox lookup: `@undeliverable.test`, `@unknown.test`,
`@timeout.test` for Experian, `@invalid.test`, `@disposable.test`, `@catchall.test`, `info@` for ZeroBounce,
every other address verified or valid. A provider over plain HTTP is a development setting, declared under
`devFieldActionProviders` behind `enableDevFieldActionProviders=true` — the mirror of the forward targets'
`devForwardTargets`:
`experian-stub|Experian (stub)|http://localhost:8080/modules/formidable-samples/experian-stub|Auth-Token|stub-token`
and `zerobounce-stub|ZeroBounce (stub)|http://localhost:8080/modules/formidable-samples/zerobounce-stub|api_key|stub-token|query`.
Spec 74 drives both samples through them, which makes it the one thing exercising `FieldActionGateway` end to
end — the provider lines, the credential injected as a header or on the URL, the path under the base URL, the
reading of the answer — and what a developer points a local instance at to try a sample without an account.

## Execution

### A field action in Java

```java
@Component(service = FieldAction.class)
public class CrmLookupAction extends ProviderFieldAction {          // the engine reads the provider id off the node
    @Reference private FieldActionGateway gateway;

    @Override public String getNodeType() { return "myco:crmLookupAction"; }
    @Override protected FieldActionGateway gateway() { return gateway; }

    @Override
    protected FieldActionResult ask(String providerId, FieldActionRequest request) throws IOException {   // an IOException is an unavailable check
        FieldActionGateway.Response response = gateway().post(providerId, "customers/lookup",
                "{\"number\":" + JSONObject.quote(request.value()) + "}");
        if (response.status() != 200) return FieldActionResult.unavailable("provider " + response.status());
        return json(response).map(body -> body.optBoolean("known") ? FieldActionResult.accept() : FieldActionResult.reject("unknown"))
                .orElse(FieldActionResult.unavailable("not the provider's JSON"));
    }
}
```

`org.jahia.modules.formidable.engine.api` exports the contract: `FieldAction` (`getNodeType()`,
`execute(actionNode, request)`), `FieldActionRequest` (form UUID, field name, value, locale),
`FieldActionResult` (`accept()`, `reject(detail)`, `unavailable(detail)`) and `FieldActionGateway` — and,
for an action behind a provider, the shape every one shares: `ProviderFieldAction` reads the provider id off
the node (`fmdbmix:providerFieldAction`), accepts without a call a value the action does not judge
(`concerns`, a blank value by default), hands the id and the request to `ask`, and turns every way the call
can fail — no answer, an id the configuration no longer declares, a node naming none — into an unavailable
check; `json(response)` reads an answer as one object or not at all. `EmailVerificationFieldAction` is the
email specialisation: only a value that reads as an address (`EmailAddress.domainOf`) reaches `verify(providerId,
address)`, the one method left to write. The samples' Experian and ZeroBounce checks are that method each. The
action node is read in `live` in a system session; an exception escaping `execute` counts as unavailable
and is logged.

## The browser — `useFieldActions`

The page never runs a field action; it asks. Everything it needs is on the element wrapper that
`LogicAwareRender` renders around every element of every container — a field from any module included,
since fields only ever render inside a Formidable container — and in one island prop. No field view
knows about the feature, and the library exports nothing for the markup (the `fieldActionAttributes`
helper of the draft is gone, see the decision log).

**The marker.** The wrapper carries `data-fmdb-field-action="blur"` when any action of the field's list
runs as the visitor leaves the field (the CND default when the property is absent), `"submit"` when every
one waits for the submission, and nothing when the field has no action — no switch, no list, an empty
one — so the client never asks about a field with nothing to run. It is computed server-side
(`fieldActionsOf`, `formidable-elements/src/utils/fieldActions.server.ts`) and rendered on every
surface; the hook is off in edit mode, as the conditional logic is. The field's controls are the
wrapper's named controls (`name` equal to `data-fmdb-node-name`), whatever their type.

**The endpoint** reaches the island as `fieldActionUrl`, computed by the form's server view next to
`submitActionUrl`: the context path, the form's UUID and — always — the language, since the engine renders
the contributor's message in it.

**As the visitor leaves a field** checked at blur (`focusout` for a text-like control, `change` for a
select, a radio or a box: one of the two, never both), the hook reads the values the field would submit
— one per selected option or checked box, blank ones dropped, a repeat asked once — and posts one request
per value, `{field, value, trigger: "blur"}`, JSON, with credentials (an `XMLHttpRequest`, as the
submission: CSRFGuard integrates with it). While a request is in flight the wrapper carries
`fmdb-field-action-pending` and `aria-busy="true"`. The answers of one field are merged — any `reject`
refuses, else any message advises, else the field is accepted — and an answer to a superseded check (the
visitor left the field again meanwhile) is dropped.

- **Refused**: the contributor's message, HTML rendered and escaped by the engine, under the field in the
  constraint messages' own element (`div.fmdb-validation-error`, `role="status"`, referenced by the
  controls' `aria-describedby`), the controls marked `fmdb-invalid` / `aria-invalid`, and every control's
  `customValidity` set to the message's text — so the browser's own constraint validation refuses the
  submission until the value changes: typing lifts it (an `input` listener in the capture phase, before
  the constraint client's, which only clears a valid control), and the next leave asks again.
- **Advised** (a warn-only action): the message in `div.fmdb-validation-warning`, the twin of the error —
  same anchoring, same `aria-describedby`, no invalid state; it stays until the next answer.
- **Accepted**: the field's messages gone, its validity cleared.
- **Unanswered** — a network error, a timeout (ten seconds), any status but a 2xx: 401 members only, 404
  unknown field or endpoint off, 429 rate-limited, 5xx — nothing is shown and nothing blocked, one console
  warning per status. The pre-check is a courtesy; the pipeline judges the value at submission whatever
  the browser saw.

**Before the submission is sent** — after the constraints hold, so a value the browser already refuses is
never sent to a provider — `settleFieldActions(form)` asks about every field carrying the marker that
logic does not hold hidden, blur and submit alike, with `trigger: "submit"`, in parallel: the
blur-checked values again (free: the engine's verdict cache, keyed on action, locale and value, answers
them) and the submit-only ones for the first time; a check still in flight is superseded, so the answer
is awaited, never raced. Any refusal: the messages shown, the refused control brought on screen and
focused, no request. Otherwise the submission goes, warnings shown; a check that could not be asked
blocks nothing. No spinner meanwhile — a spinner would hide the form the messages land on — but the
field being asked about says so (below), and a second click is ignored until the answer.

**While a check runs**, at blur or before a submission or the next step, the field says what the form is
waiting for: its wrapper carries `fmdb-field-action-pending` and `aria-busy="true"`, and under the field a
line `span.fmdb-field-action-checking` (`role="status"`: a turning glyph and "Checking…", small and muted
by default, every value a variable) is drawn where a message would be and removed with the answer. A
visitor who clicks Submit while a slow check runs sees which field holds the form.

**In a multi-step form** — the strategy for the asynchronous checks, decided 2026-09-25. Leaving a step
(Next) validates its constraints, then settles the step's own fields with the submit trigger: a
blur-checked value is asked again (free), a value still being checked is awaited rather than raced, and
an action set to run "at submission" runs now, when the visitor leaves the field's step — the editor's
label says so — which costs exactly what one run at submission does in the normal flow and spares the
visitor being sent back from the last step to the first. A refusal keeps the visitor on the step, the
message under the field, the field focused; Next ignores a second click while it settles. Submit
settles the whole form, as above. And whichever way a refusal reaches a control that is not on screen —
the settle, the pipeline's `FMDB-015` — the island brings its step on screen first and focuses it on the
next run, once the step's display has changed (`stepIndexOf`, an effect of the island's state).

**A refusal at submission** (`FMDB-015`, or `FMDB-017` for too many answers on one field): the response's
`messages` are anchored exactly as the pre-check's — the error under its field, the focus moved, the
form kept with what the visitor typed. The focus is an effect of the island, run once the loading state
has cleared and the step holding the control is on screen: the spinner hides the form (`display: none`)
while the request runs, a step other than the current one is hidden too, and a hidden control cannot
take the focus — a call made from the request's own code, deferred or not, could not be timed against
React's commit (measured: the deferred call landed on the still-hidden form). The submission hook hands
the control over through its `onRefused` callback; the island owns the reveal. For `FMDB-015` that is all the page says: a field action's refusal
is a validation failure and reads like one, no global error box, no code on screen. `FMDB-017` keeps the
form's global error under the anchored message. A message whose field is not in the form falls back to
the global error: a refusal the visitor cannot see is worse than a generic one.

**Reset** clears every verdict — the browser restores the values, not a `customValidity`, nor what was
drawn. **Edit mode**: the hook is off (`enabled = !isEditMode && !!fieldActionUrl`); the Page Builder form
never calls the endpoint. **Hydration**: the listeners attach on mount; a field left before that is
simply not pre-checked, and the pipeline judges it.

**The zone** under the field while authoring is rendered by the same wrapper as soon as the switch is on,
inside the field's Page Builder box, by two views on the same chrome as the form-actions zone
(`design/AuthoringActionsZone`, `design/AuthoringActionCard`).
`FieldActionList/hidden.authoring` draws the header (the list's icon and count), the ordered cards, the
call-out of a list still empty, and the create button — the list's module declares `fmdbmix:fieldAction`
to jContent, so one button, then the chooser listing every deployed field-action type.
`FieldAction/hidden.authoring`, on the mixin at priority -1 so a module's own card wins, draws the type's
label, tooltip and icon from its module through `ActionSummaryService`, the title, the key parameter, and
three badges reading `trigger`, `severity` and `whenUnavailable` (the CND defaults for a node saved
without them); a type shipping no icon is drawn with the marker's glyph rather than the platform's generic
sheet. Both answer nothing outside edit mode. The hooks and variables are in `docs/styling/`.

## Providers, configuration and secrets — the `FieldActionGateway`

A provider's credential goes neither in the JavaScript nor in the repository. As forward targets are
declared, providers are declared in `org.jahia.modules.formidable.cfg`:

```
# --- FIELD ACTIONS ---
# Each entry: id|Label|https://base-url|Credential-Header-Name|credential (the last two together, or neither)
fieldActionProviders=                      # id|Label|https://base-url|Credential-name|credential[|header|query]
enableDevFieldActionProviders=false        # plain HTTP on localhost or host.docker.internal: a provider's double
devFieldActionProviders=
fieldActionHttpConnectTimeoutSeconds=5
fieldActionHttpRequestTimeoutSeconds=10
fieldActionVerdictCacheTtlSeconds=300      # 0 disables the cache
fieldActionRateLimitPerMinute=30           # 0 disables the endpoint
fieldActionMaxValueLength=512
fieldActionMaxValuesPerField=50            # DISTINCT values of ONE field judged at submission
```

`FormidableConfigService` parses them (`FieldActionProvider`, whose `toString` masks the credential;
`FieldActionSettings`, everything the field actions read, in one piece; an optional sixth part says where the
credential goes, `header` by default or `query` for a provider that reads its key off the URL, appended to the
target as a parameter of that name and then sent in no header), the HTTPS-only rule of the
forward targets applied to the base URL — and their development rule, plain HTTP on localhost or
host.docker.internal, to `devFieldActionProviders` behind `enableDevFieldActionProviders`, where a provider's
double is declared (the samples' Experian stub); a development id never shadows a standard one, and the list is
ignored whole without the switch. `FormidableFieldActionProvidersInitializer` feeds the
`formidableFieldActionProviders` choicelist (label shown, id stored). `FieldActionGatewayImpl` is the
exported service: it resolves the provider, refuses a path that is absolute, carries a scheme, climbs with
`..` or holds a control character, appends it to the base URL (a base without a trailing slash keeps its
last segment), injects the credential header, applies the timeouts, caps the response body at a million
characters, and never puts the credential in a log line or in the object it returns.

## Security and trust

- **The endpoint is the only entry**, with the submission's posture: `origin: hosted` scope, CSRFGuard
  whitelist, the form read in the visitor's own `live` session (what the caller cannot read is not found),
  a guest refused on a members-only form (`FMDB-009`). Only the walk of a form the visitor has read, and
  the action nodes themselves, are read in a system session — the submitter has no reason to have read
  access to the action nodes.
- **The captcha is not re-checked at the pre-check** (a token is single-use); the pre-check of a
  captcha-protected form is bounded by the rate limit, the cap and the cache, or switched off.
- **The credential never leaves the engine**: `.cfg` → gateway; the JavaScript sees a provider *id*; the
  repository stores a provider *id*.
- **SSRF**: the gateway only ever calls the configured base URLs; the path is relative-only, on the same
  host and scheme.
- **Abuse of a paid API**: rate limit per client on the pre-check, a cap on the distinct values judged per
  field at submission (`fieldActionMaxValuesPerField`, `FMDB-017` past it, checked over the whole submission
  before anything runs), verdict cache, blocking actions only in the pipeline, `0` to switch the endpoint
  off. The submission path needs its own bound because it judges every value of a field, and nothing else
  limits how many values one field name may carry.
- **The visitor's value never reaches a log line above DEBUG**, whichever way it arrives: an action's
  `detail` or the message of a throwable — `NumberFormatException: For input string: "…"`
  carries it by construction, so the exception's type is logged and the throwable itself goes to DEBUG.
- **Information disclosure**: the visitor gets the contributor's message and a verdict, never the provider's
  response, the action node's identifier or its node type; `detail` stays in the logs. The candidate value
  is never in a URL, nor in a log line at INFO — a view's output, which may echo it in breach of the
  contract, is logged at DEBUG only.
- **The view's output is the verdict object and nothing else**: a lenient reader would let an echoed value
  retire the check onto the `whenUnavailable` default; the strict one fails deterministically.
- **The verdict cache is keyed on the locale** as well as the action and the value: a pre-check in a locale
  where the check passes cannot seed the accept the submission finds in another.
- **The pipeline is the authority, over every non-blank value of every answered, visible field.** The
  pre-check is a courtesy; the submit-time re-check is what protects the data — a browser that skips the
  pre-check meets the same actions. Without a dispatcher the step says so in the logs rather than pass in
  silence.

## Performance

A blur-time call costs one servlet hit, one read of the form in the visitor's session, one action run — a
provider call at most; the walk of the published form that finds the fields runs once a minute per form
and locale (`FieldActionsCache`), not once a call. The verdict cache makes submit free for values already
checked in the same locale; the rate limit bounds a hostile client. The pipeline re-check adds one provider
call per blocking action and non-blank value never pre-checked.

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
| 2026-09-22 | **The endpoint reads the form as the pipeline does** — visitor session, `FMDB-004` for what the caller cannot read, `FMDB-009` for a guest on a members-only form (review of #344) | The first cut resolved the form in a system session, so any published form on the platform, members-only pages included, had its actions runnable by anyone holding the public fid; and no authentication check existed while the pipeline had one. The pipeline's posture, step for step, is the only defensible one |
| 2026-09-22 | **The view's output is exactly one JSON object** — no tolerance for surrounding markup (review of #344) | The lenient reader took the widest span between braces: a view echoing the value let a `{` in the value make the output unparseable, hence unavailable, hence accepted by the CND default. Strict parsing fails on every value, deterministically, where the author sees it |
| 2026-09-22 | **Every non-blank value is judged; the locale is in the cache key; the response names no action node** (review of #344) | The authority must cover what is stored: all values, not the first. A cached accept in one locale must not answer another, since the locale is part of the request. A node UUID and a vendor namespace in the response disclose the checks behind a form the caller may not read |
| 2026-09-22 | **The engine ships no concrete field-action type** (HDU: « je préfère l'avoir en sample et ne pas fournir d'action field par défaut ») | The email domain check was written as a built-in of the engine and moved to the samples module before it shipped. The engine owns the mechanism; which checks a form deserves is a project's decision, and a built-in would have made one for every installation. The samples module is where a third party reads the shape to copy, and it is installed by the test provisioning manifest only |
| 2026-09-22 | **An action judges one value per call**, a multi-valued field one value at a time (HDU, asked whether the values should arrive as an array) | They already are an array where they are parsed; what carries one value is what an action receives. Three reasons to keep it: the pre-check has only one value to offer, since the browser asks while the visitor fills the form and not once it is complete; the verdict cache is keyed by value, which is what lets it be shared between the two entry points and between visitors, where a whole-set key would share nothing; and a third-party action stays "one value in, one verdict out", the same code serving a text field and a group of checkboxes. The cost is one provider call per value, which `fieldActionMaxValuesPerField` bounds |
| 2026-09-22 | **The rendered view's body is stripped of the platform's `jahia:temp` markers** before the strict reader sees it (review of #344) | `URLFilter` wraps a `module`-configuration fragment and `StaticAssetsFilter`, which unwraps it, does not run there. The lenient reader survived it by accident; the strict one would have answered UNAVAILABLE for every JavaScript action, accepted by the CND default. The pattern is copied from `StaticAssetsFilter` rather than the class called: that class drags the rendering stack into the tests for one regular expression |
| 2026-09-22 | **A rejection message interpolates `${value}` and nothing else** (review of #344) | The pre-check knows one field, the submission knows them all; interpolating the others would render the same message complete at submission and full of holes at blur. One contract for both, enforced by the code rather than by advice |
| 2026-09-22 | **The values judged per field are capped** (`fieldActionMaxValuesPerField`, default 50; reviews of #344) | "Every value is judged" turned one submission into one provider call per value, and nothing bounds how many values a field name carries. Distinct values miss the verdict cache and evict everyone else's. Three corrections followed in the same wave: the count is of **distinct** values, since that is what a provider call costs; the check covers **the whole submission before anything runs**, so no field is billed for another to cancel it; and a field whose actions only warn is neither judged nor counted, since `blockingOnly` skips it anyway. The default is above a plausible option count — an "interests" group with thirty boxes is an ordinary form — and the refusal has its own code with a message naming the field, rather than a bare size error the visitor cannot act on |
| 2026-09-22 | **`fmdbmix:fieldActions` drops the `jmix:dynamicFieldset` supertype** (found on the local instance: the switch was nowhere in the Content Editor) | `jmix:dynamicFieldset` extends `jmix:templateMixin`, which the editor reads as "no enable switch"; with no property of its own the fieldset was then not rendered at all, so the feature had no way in. `extends = fmdbmix:formElement` alone makes it dynamic AND switchable. Measured on `forms.editForm`: `visible: false, hasEnableSwitch: false` before, both true after |
| 2026-09-22 | **The walk of the form is cached per form and locale for sixty seconds**, not keyed off the form's `jcr:lastModified` (review of #344) | A change to an action or a field touches that node's `jcr:lastModified`, not the form root's: the core `LastModifiedListener` writes the first node up the hierarchy that carries `mix:lastModified`, which a `jnt:content` action node is itself (jahia-impl 8.2.4 sources, `updateLastModifiedProperties`), so the root's date would serve stale actions after a republish. A short TTL is exact within the minute and needs no invalidation; the pipeline walks fresh every time |
| 2026-09-25 | **The switch stays a dynamic-fieldset switch at the end of the `content` section** (HDU) — no `enabled` boolean, no FIELD ACTIONS section | The alternative, the `fmdbmix:jExperienceSensitiveField` shape (a `jmix:templateMixin` with an `enabled` boolean, always shown, placeable in a section of its own) would have changed what "off" means — the list kept, the checks paused — and needed a listener to create the list lazily. "On, the list exists; off, the list is deleted" is simpler and is what the label says |
| 2026-09-25 | **`fmdbmix:fieldActions` extends a new positive marker, `fmdbmix:submittableField`**, not `fmdbmix:formElement` (HDU: « pourquoi fieldset porte le switch ? ») | `fmdbmix:formElement` reaches the fieldset (title + logic) and the button (through `fmdbmix:element`), both non-submittable: a switch whose actions never run. The engine had only the negative marker, and `extends` cannot say "formElement minus nonSubmittable". The positive marker is the `profileMappableField` pattern — the same sixteen field types declare it, a third-party field opts in from its own CND — and the pipeline keeps its `!nonSubmittable` test so no existing field type stops being submitted. A supertype added to a type is seen by existing nodes without a migration |
| 2026-09-22 | **The marker and the zone live on the element wrapper**, not in the field views and not in a library helper (HDU, PR 2 handoff) | `LogicAwareRender` already wraps every element of every container with the node name, id, type and the logic state; adding `data-fmdb-field-action` and the zone there touches one file, no field view, and covers a field from any module without it calling anything — fields only ever render inside a Formidable container. The draft's `fieldActionAttributes(currentNode)` library export is dropped: a helper every view would have had to remember to spread |
| 2026-09-22 | **The warning hook is `fmdb-validation-warning`**, not the `fmdb-form-warning` of issue #341 (HDU) | The twin of `fmdb-validation-error`: the pair sits in one row of the styling documentation, and a stylesheet that finds one finds the other |
| 2026-09-25 | **The provider-backed sample is written against a real provider, Experian, as an example implementation**, with a double of the provider in the samples module and a development provider list in the configuration (HDU: a customer asks for the Experian API; « précise dans la doc que c'est un exemple d'implémentation ») | A sample against an invented provider proves the gateway against nothing; against a named one, the contract is the provider's own documentation and a project copies the class as is. The double is a servlet because a static file refuses a POST (405, measured) and the test suite has no network; it lives in the samples module, next to the class it doubles. A provider over plain HTTP was refused by the HTTPS rule, rightly — the forward targets had solved the same need with a development list behind a switch, so the providers get the same pair, `enableDevFieldActionProviders` and `devFieldActionProviders`, rather than a relaxation of the rule |
| 2026-09-25 | **Every provider-backed check stays a sample; what they share moves into the engine** (HDU: « met tout en module sample, une implémentation ZeroBounce et une autre pour l'autre, mutualise au mieux et mets ce qui est commun dans le moteur ») — `fmdbmix:providerFieldAction`, `ProviderFieldAction`, `EmailVerificationFieldAction`, `EmailAddress`, the `query` placement of a credential | A second provider showed what a first one cannot: the node read, the "not an address" rule, the outage rule and the JSON reading were the same forty lines twice, and the domain check carried a third copy of the address parsing. The engine ships the shape and no concrete check, which keeps the 2026-09-22 decision; a sample is a type and one method. ZeroBounce reads its key off the URL, which no header could carry: a sixth part on the provider line rather than a credential the action would have to see. And the providers' "do not mail" bands are read for a form — a role address receives mail — rather than for a mailing list |
| 2026-09-25 | **The JavaScript way of writing a field action is withdrawn** (HDU: « quand je pensais au rendu js je pensais au useFieldAction… pas à l'implémentation back en js ») — the dispatcher runs Java services only; the library's `readFieldActionRequest` and `fieldActionResult`, the samples' `minimumWordsAction` and spec 73's JavaScript check go with it | Built on a misreading of the brief — « le code de l'action écrit en JS » meant the visitor's page — it answered a question nobody had asked, sidestepped the one left open with #164 (form actions in TypeScript, waiting for a server-extension SDK of the JavaScript modules), and tied the engine to `jsm-raw-html`, an internal of that engine. Withdrawn before any release; the rows above stay as the record of what was measured on the way |
| 2026-09-22 | **A refusal at submission (`FMDB-015`) shows the contributor's message under the field and nothing else** (HDU) | A field action's refusal is a validation failure, so it reads like one: the message anchored on the field, the focus moved, the form kept with what the visitor typed — no global error box, no error code on screen. Every other rejection keeps today's global message; `FMDB-017` keeps it under the anchored message, since its cause is not one value to correct |
| 2026-09-25 | **No spinner while the field actions settle before the submission**; a second click meanwhile is ignored | The spinner hides the form (the accepted submission replaces it), and the messages the settle may produce land on that very form: a refused value would have flashed the form away and back. The pending state on the fields checked is the feedback, and a guard in the submission hook keeps a second click from starting a second settle |
| 2026-09-25 | **The pre-check leaves alone a field conditional logic holds hidden** (`isAskable`) | The pipeline skips hidden fields, so asking about one would spend a provider call on a value that is never judged, and show a message under a field the visitor cannot see. Was an open question of the engine PR |
| 2026-09-25 | **The library helpers hand the verdict over in the engine's raw-html element; the reader decodes entities only once the raw body has failed to read** (review of #346, two rounds) | `renderToString` escapes the text a component returns: every JavaScript field action answered as a plain string reached the reader as `&quot;`-quoted JSON, UNAVAILABLE, accepted by the CND default — and nothing had run that chain end to end. The element is what the engine emits verbatim. The first cut decoded every body: a raw body whose `detail` held entity text (`provider said &quot;no&quot;`) then read as malformed — or as a second `verdict` — and a refusal ended accepted; decoding after a failed raw read keeps both paths exact. The samples' JavaScript action and spec 73 hold the chain |
| 2026-09-25 | **`jsm-raw-html` is the engine's internal element; the engine strips its tags itself as a belt** (review of #346) | The JavaScript modules engine's source says the element should not be used in userland, and the published library now depends on it: should it be renamed or dropped, the tags would reach the reader as markup and every JavaScript action would go UNAVAILABLE, accepted, silently. `RenderServiceViewRenderer.body` strips the tags too, so the verdict reads either way; the library test pins today's strip. Whether the element may be relied on, or a supported way to return raw text exists, is a question for the JavaScript modules team — open below |
| 2026-09-25 | **Next moves from the step the visitor is on once the answer lands, not from the one the click saw** (review of #346) | The validation now waits on a provider; Previous stays enabled meanwhile. Read from the click's render, the move jumped a step when the visitor had gone back, or when logic had revealed one. The current step and the visible steps are refs updated with the state, and a move whose step is gone is dropped |
| 2026-09-25 | **The field actions lift their validity from a control barred from constraint validation without comparing** (review of #346) | A disabled control — how logic hides a field — reports no validation message, so the ownership check could not see its own text and dropped the entry while the refusal stayed: nothing could lift it once the field showed again. Barred, it is lifted; validating, only when the text is still the hook's |
| 2026-09-25 | **The field actions lift only the validity they set** (review of #346) | `setCustomValidity("")` over a validity another client set — a required checkbox group's "select at least one" — let an empty group through the browser. What the hook wrote is remembered per control and cleared only while it is still there; the constraint client's own errors are cleared only once the control is valid, as it does itself |
| 2026-09-25 | **A field's anchor control is the one the visitor sees; the named ones carry the value** (review of #346) | A range field's named control is a hidden mirror of the slider: the focus, the ARIA and the message go to the slider, the validity to both. For every other field the two coincide |
| 2026-09-25 | **Next settles the step; \"at submission\" means \"when the visitor leaves the field's step\" in a multi-step form; a refusal anywhere brings its step on screen** (HDU, review of #346, option (b) of the two offered) | Three ordinary paths left a refused field invisible in a hidden step: a blur answer landing after a synchronous Next, a submit-triggered action of an early step asked only at the final settle, the pipeline's FMDB-015 on a hidden control. The strict reading of \"at submission\" — asked at the final Submit only — would have bounced the visitor from the last step to the first; asked at Next it costs the same in the normal flow. Rule kept from `anchorFieldMessages`: a refusal the visitor cannot see is worse than a generic one |
| 2026-09-25 | **A field being checked says so under it** — `span.fmdb-field-action-checking`, glyph and \"Checking…\", `role=\"status\"`, small and muted by default, every value a variable (HDU) | Submit and Next now wait for the checks; the 60% fade of the controls said nothing about which field the form was waiting for, and a spinner over the form would hide the field the answer lands on |
| 2026-09-25 | **A refusal at submission is focused by the island once the loading state has cleared, and the message of an earlier attempt goes as the next one leaves** (review of #346) | The spinner hides the form while the request runs, so a call made from the request code focused a hidden control; and a stale "An error occurred" stayed above an anchored refusal, which the page promised to show alone |

## Open questions

- Credentials as `${env:…}` references in `.cfg`: depends on the platform's configuration interpolation
  being enabled; to be verified, otherwise the file holds the literal.
- A `warn` refusal from a `submit`-triggered action: shown once and the submission proceeds, or a confirm
  step? v1: shown, proceeds.
- **A rule about the whole set of a multi-valued field has no home**: "at most three topics", "these two
  options cannot be picked together". An action is handed one value and never its siblings, on purpose (see
  the decision log). Such a check would need either a kind of its own or a second value in the request
  carrying the field's other values — and, for the pre-check, a browser that sends the set rather than the
  one value it just changed. Not built, and not implied by anything shipped.
- A captcha-protected form's pre-check is an uncaptcha'd oracle onto the provider, bounded by the rate limit
  only. A per-action "at submission only" setting (no pre-check, the pipeline alone runs it, behind the
  captcha) would let a contributor close it for one paid check without switching the endpoint off for the
  whole platform. Not built: the trigger `submit` still pre-checks, on purpose, so the visitor sees the
  refusal before the page reloads.

## Roadmap

1. **Engine** — CND, `FieldAction` and `FieldActionGateway` API, configuration keys and provider list,
   dispatcher, endpoint, pipeline step 11b, `FMDB-015`/`FMDB-016`, `messages[]`, unit tests; the samples
   module's `fmdbsample:blockedWordsAction`. **Shipped 2026-09-21** (this page's pull request).
2. **Library** — nothing, in the end: the helpers of a `hidden.execute` view shipped on 2026-09-25 and were
   withdrawn the same day with the JavaScript path (decision log).
3. **Elements** — the marker and the zone on the element wrapper, `useFieldActions` (blur/submit → endpoint →
   `setCustomValidity` + the field-error rendering, `settleFieldActions(form)` before the XHR, `FMDB-015`
   anchored), the `FieldActionList` and `FieldAction` `hidden.authoring` views, the styling hooks
   `fmdb-validation-warning` and `fmdb-field-action-pending` in `docs/styling/`, the Cypress specs 72 and 73,
   a field action in the playground. **Shipped 2026-09-25** (the browser pull request).
4. **Samples** — four, all Java: blocked words and the email domain check, **shipped** with the engine; Experian
   and ZeroBounce on the engine's `EmailVerificationFieldAction`, with their doubles and the development provider
   list they need, **shipped 2026-09-25** (the samples pull request), driven by spec 74 — the first thing exercising
   the gateway end to end. The JavaScript sample of the browser pull request went with the JavaScript path.

## Sources

- Engine, the contract a module compiles against: [`FieldAction`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FieldAction.java), [`FieldActionRequest`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FieldActionRequest.java),
  [`FieldActionResult`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FieldActionResult.java), [`FieldActionGateway`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/FieldActionGateway.java),
  [`ProviderFieldAction`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/ProviderFieldAction.java), [`EmailVerificationFieldAction`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/EmailVerificationFieldAction.java),
  [`EmailAddress`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/api/EmailAddress.java).
- Engine, the mechanism: [`FieldActionServlet`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/FieldActionServlet.java) (the pre-check endpoint),
  [`FieldActionDispatcher`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/FieldActionDispatcher.java) (the run of a field's actions, Java service or unavailable),
  [`FieldActionRuntime`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/FieldActionRuntime.java) (the registered services, the shared cache),
  [`FieldActionCollector`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/FieldActionCollector.java) (the walk of a form), [`ResolvedFieldAction`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/ResolvedFieldAction.java),
  [`VerdictCache`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/VerdictCache.java), [`FieldActionsCache`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/FieldActionsCache.java), [`RateLimiter`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/RateLimiter.java),
  [`FieldActionGatewayImpl`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/field/FieldActionGatewayImpl.java) (the calls to a provider);
  [`FormSubmissionPipeline`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmissionPipeline.java) (step 11b, `runFieldActions`), [`FormFieldMetadataCollector`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormFieldMetadataCollector.java)
  (`Result.fieldActions`), [`FormSubmitServlet`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/servlet/FormSubmitServlet.java) (`messages`, `RESERVED_KEYS`);
  [`FormidableConfig`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/config/FormidableConfig.java) and [`FormidableConfigService`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/config/FormidableConfigService.java) (`FieldActionProvider`, `FieldActionSettings`),
  [`FormidableFieldActionProvidersInitializer`](../../formidable-engine/src/main/java/org/jahia/modules/formidable/engine/choicelist/FormidableFieldActionProvidersInitializer.java);
  [`definitions.cnd`](../../formidable-engine/src/main/resources/META-INF/definitions.cnd), [`org.jahia.modules.formidable.cfg`](../../formidable-engine/src/main/resources/META-INF/configurations/org.jahia.modules.formidable.cfg),
  `org.jahia.bundles.api.authorization-formidable-engine.yml`, `org.jahia.modules.jahiacsrfguard-formidable.cfg`.
- Samples, the shape to copy: [`BlockedWordsFieldAction`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/BlockedWordsFieldAction.java), [`EmailDomainFieldAction`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/EmailDomainFieldAction.java),
  [`ExperianEmailFieldAction`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ExperianEmailFieldAction.java), [`ZeroBounceEmailFieldAction`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ZeroBounceEmailFieldAction.java) and their doubles
  [`ProviderStubServlet`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ProviderStubServlet.java), [`ExperianStubServlet`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ExperianStubServlet.java), [`ZeroBounceStubServlet`](../../jahia-test-module/formidable-test-module-samples-java/src/main/java/org/jahia/test/modules/formidable/samples/actions/field/ZeroBounceStubServlet.java);
  their [CND](../../jahia-test-module/formidable-test-module-samples-java/src/main/resources/META-INF/definitions.cnd), labels and icons.
- Browser: [`useFieldActions`](../../formidable-elements/src/hooks/useFieldActions.ts), [`fieldActionMessages`](../../formidable-elements/src/utils/fieldActionMessages.ts),
  [`LogicAwareRender`](../../formidable-elements/src/components/FormContainer/LogicAwareRender.tsx) (the marker and the zone), the `hidden.authoring` views of
  [`FieldActionList`](../../formidable-elements/src/components/FieldActionList/hidden.authoring.server.tsx) and [`FieldAction`](../../formidable-elements/src/components/FieldAction/hidden.authoring.server.tsx).
- Tests: [spec 72](../../tests/cypress/e2e/actions/72-field-actions-zone-in-edit-mode.cy.ts) (the zone), [spec 73](../../tests/cypress/e2e/actions/73-field-actions-live.cy.ts) (the visitor's page),
  [spec 74](../../tests/cypress/e2e/actions/74-field-actions-behind-a-provider.cy.ts) (the providers through their doubles), [spec 223](../../tests/cypress/e2e/fields/223-field-actions-switch-per-field-type.cy.ts) (the switch per field type).
- [Form submission flow](form-submission-flow.md), [CND module ownership](cnd-module-ownership.md),
  [Custom validation](custom-validation.md), [Field actions: providers and limits](../administration/field-actions.md),
  the extension guide's [field action case](../extension/how-to-extend-views-and-elements-from-third-party-module.md#case-5-add-a-field-action-type), issue #341.
- Platform: `RenderService.render(Resource, RenderContext)`, `AggregateCacheFilter` (expiration lookup
  order: request attribute, node, view), `CacheFilter` (caches only `expiration > 0`),
  `javascript-modules-engine 1.3.0-SNAPSHOT` manifest (no own package exported).
