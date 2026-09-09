# jExperience Integration

> **Status: design specification, not implemented yet.** Decisions dated 2026-09-09; the
> implementation lands on the `feat/jexperience-integration` branch and this document is
> updated as each phase ships. Targets: Formidable 0.5.x, jExperience 4.x (the OSGi ranges stay
> open to 3.4+), jCustomer 3.x.

## Overview

When jExperience is installed and configured on a site, a Formidable form gains three
capabilities without anyone touching jExperience by hand:

- **A submission event.** Each accepted submission of a *tracked* form is sent to jCustomer as
  the standard `form` event, server-side, with the submitted values. It feeds analytics, goals
  and segments exactly as a Forms submission does today.
- **A form mapping.** Fields the author maps to profile properties produce a standard jCustomer
  form-mapping rule, created and kept in sync at publication. Marketers see it in the
  jExperience **Form mappings** screen.
- **Prefill.** A mapped field can be pre-filled from the visitor's profile at render time, on
  the server, so the value is present at first paint.

Non-goals for this iteration, each recorded with its reason in the [decision log](#decision-log):

- A custom event type of Formidable's own. Standard rules never fire on it.
- A form-level switch that tracks every submission regardless of jCustomer rules. Recorded as an
  option, not built.
- A form-level mapping table in the editor. Recorded as a later option.
- Client-side prefill through the tracker, kept as plan B if server-side prefill measures too
  costly.

---

## Three things, one event

The author's mental model has three independent intents. jExperience implements them as one
event with two consumers, and that coupling drives every decision below.

| | Lives in | What it is |
|---|---|---|
| **The `form` event** | jCustomer | The trace of one submission: `eventType form`, target `itemType form` with the form's identifier as `itemId`, the page as source, every submitted value under `flattenedProperties.fields`. Persisted in jCustomer's event store, hence the statistics and dashboards. |
| **The mapping rule** | jCustomer | A rule tagged `formMappingRule` whose condition is a `formEventCondition` on that identifier and whose actions copy event fields into profile properties. Nothing about it lives in JCR. It is a *consumer* of the event: no event, no profile update. |
| **Prefill** | Jahia | Reading the profile properties back into the form. It needs a mapped property to know what to read, but a mapping does not imply prefill. |

**What "tracked" means, verified in the legacy stack.** With Forms today, no `form` event is
sent unless some jCustomer rule references the form: a mapping rule, a goal on the form event,
or a past-event rule behind a segment. The tracker builds its list of forms to watch from the
`trackedConditions` of the page's context response, and the Forms callback checks that list
before sending. A form nobody referenced has no submission statistics at all.

Formidable keeps that rule: **a submission is sent only when the form is referenced in
jCustomer.** Formidable's own mapping rule counts as a reference, so mapping a field is enough
to start tracking, as it is with Forms.

---

## Architecture

The mechanism fits in one sentence: **Formidable asks the jExperience module's Java service,
and that service talks to jCustomer** with its own credentials and the visitor's cookies. It
happens at three moments: when an author publishes a form, when a visitor submits one, and when
a page with a prefilled field is rendered. Everything else in this document is what each moment
carries, and what the server checks before sending.

```
Visitor's browser          Formidable                jExperience module          jCustomer
(web client)               (server, Jahia)           (server, Jahia module)      (server, CDP)

  pages, submissions ──►     one Java service ──►      HTTPS, credentials, ──►
                                                       visitor cookies

  wem.js tracker ─ ─ ─ ─ ─ ─ page views, on its own, unchanged by this work ─ ─ ─ ─ ─ ─ ─ ─►
```

Formidable never talks to jCustomer from the browser, and never holds jCustomer credentials:
the jExperience module owns the connection, the peer header, the profile and session cookies,
and the bot filter.

One new Java module, `formidable-jexperience-engine`, holds every jExperience-specific piece.
Two small, additive changes land in the existing modules: a post-submission observer SPI in the
engine, and three render-side hooks in the elements module. Nothing in Formidable depends on
jExperience; the new module depends on both.

| Piece | Module | Role |
|---|---|---|
| `fmdbmix:profileMappableField` | formidable-engine, CND | Marker mixin, no properties: "this field can take part in a profile mapping". Declared as a supertype by every mappable field type in the elements and extended-inputs modules, and by third-party fields that want the feature. |
| `fmdbmix:jExperienceProfileMapping` | jexperience-engine, CND | Property mixin that `extends` the marker, so it reaches every field claiming it without naming a field type or depending on the extended inputs: profile property (choicelist), prefill toggle, write strategy. Surfaced as a "jExperience" section in the field's editor form through a Content Editor form override. |
| `ProfilePropertiesChoiceListInitializer` | jexperience-engine | Lists profile properties compatible with the field's shape, filtered on system tags. Nothing reusable exists today: jExperience ships no choicelist initializer, its own screens and the Forms bridge each fetch and filter the property types in the browser. The generic half is written so it can be lifted into jExperience later. |
| `MappingRuleSyncListener` | jexperience-engine | Live-workspace publication listener that upserts or deletes the form's mapping rule. Same pattern as `FormPublicationAclSyncListener`. |
| `JExperienceSubmissionListener` | jexperience-engine | Implements the new observer SPI: gates, tracked-conditions check, `form` event. |
| `ProfilePrefillService` + `PrefillRenderFilter` | jexperience-engine | One context request per form render, memoised per request and cached briefly per profile; the filter makes the prefilled field fragment uncacheable. |
| `FormSubmissionListener` SPI | formidable-engine, `api` package | Called by the pipeline after all actions succeeded, with the form node, request, validated parameters, files, locale and page reference. Observers never fail the submission. |
| `pid` routing param, tracking header, prefill attribute | formidable-elements | The SSR writes the page id into the form's action URL; the island sends the tracker state and the page URL in one header; field views read the prefill value the filter exposes. |

---

## Data flows

Four moments, on the same four lanes every time. Only the first lane runs in the visitor's or
author's browser; Formidable, the jExperience module and jCustomer are all server-side, the
first two inside Jahia, the third a separate service. A step marked *via jExperience* goes
through the module's service; a step marked *response* is an answer.

### Editing: choosing a profile property for a field

```
1  Author's browser    ──►  Formidable            opens a field, section "jExperience"
2  Formidable          ──►  jExperience module    which profile properties fit this field?
3  jExperience module  ──►  jCustomer             GET /cxs/profiles/properties/targets/profiles
4  jCustomer           ──►  Formidable            property types, filtered by field shape and tags   (response)
5  Formidable          ──►  Author's browser      dropdown of compatible properties                   (response)
6  Author's browser    ──►  Formidable            saves property, prefill toggle and strategy
```

The dropdown is server-fed. The editor never needs a jExperience permission or the proxy,
because the choicelist initializer calls jCustomer with the module's admin client.

### Publishing: keeping the mapping rule in sync

```
1  Author's browser    ──►  Formidable            publishes the form
2  Formidable                                     listener: mapped fields → rule, compared with the existing one
3  Formidable          ──►  jCustomer             POST the rule — or DELETE it when nothing is mapped   (via jExperience)
4  jCustomer                                      rule stored, visible in Form mappings
```

Diff before POST, as the Forms bridge does, so a publication that changes nothing does not
churn the rule. The rule id is derived from the site key and the form's UUID, so renaming a
form never orphans a rule.

| Hop | Carries | Trust |
|---|---|---|
| Author → live | The field nodes with their mixin properties | Editor permissions, as any publication |
| Listener → jCustomer | Rule id, scope, one `setPropertyAction` per mapped field | Admin client owned by jExperience; the listener runs in a system session |

### Submitting: from the browser to the visitor's profile

```
1  Visitor's browser   ──►  Formidable            POST fid · lang · pid, header tracker on/off + URL, profile cookies
2  Formidable          ──►  Visitor's browser     validates, runs actions, answers 200 right away        (response)
3  Formidable          ──►  jCustomer             is this form referenced by a rule here? (cached 60 s)  (via jExperience)
4  jCustomer           ──►  Formidable            tracked conditions                                      (response)
5  Formidable          ──►  jCustomer             form event: all values, visitor's profile and session   (via jExperience)
6  jCustomer                                      stores the event, applies the mapping rule → profile updated
```

The visitor's response never depends on jCustomer. The header can only prevent the event;
every reason to send it is established by the server. The page reference is an identifier the
server resolves; the URL the visitor actually used travels alongside as analytics data, exactly
as jExperience's own page events carry it.

| Hop | Carries | Trust |
|---|---|---|
| Browser → Formidable, query | `fid` form UUID, `lang`, `pid` page UUID (written by the SSR into the action URL) | Validated as UUIDs; `pid` resolved in live and checked against the form's site |
| Browser → Formidable, headers | `X-Formidable-Tracking`: tracker on or off, plus the page URL and query string the visitor saw; existing logic-state, time-zone and captcha headers | Client-controlled; `off` or absent stops the event, `on` grants nothing by itself; the URL is analytics data, never an identity |
| Browser → Formidable, cookies | `wem-profile-id`, `wem-session-id`, first-party, set by the tracker | Read by the jExperience API; the profile id is the CDP's bearer token, see [Security](#security-and-trust-model) |
| Formidable → jCustomer, probe | The page as source, no event | Answer cached 60 s per page; jExperience adds the peer header and filters bots |
| Formidable → jCustomer, event | The `form` event bound to the visitor's profile and session | The server fixes event type, target, source and profile; only field values come from the client |

### Rendering: prefilling a field from the profile

```
1  Visitor's browser   ──►  Formidable            GET page (cookies)
2  Formidable                                     field with prefill on: rendered per request, not cached
3  Formidable          ──►  jCustomer             mapped profile properties (1 call per form, cached 60 s)   (via jExperience)
4  jCustomer           ──►  Formidable            values                                                    (response)
5  Formidable          ──►  Visitor's browser     page with prefilled fields                                 (response)
```

Only fragments of fields with prefill enabled leave the cache, and only for this request. One
jCustomer call serves every prefilled field of the form; repeated views by the same visitor hit
the per-profile cache.

> **To verify first, in a Cypress test.** The `expiration` request attribute must bypass the
> cache lookup as well as the store; the attribute must not leak to sibling fragments; and the
> render filter, at priority 10, must run its `prepare` before the aggregate cache filter's.
> `CaptchaRenderFilter` works this way today, which is the precedent.

---

## Data contracts

### Submission request

| Element | Value | Status | Server handling |
|---|---|---|---|
| `?fid` | Form node UUID | existing | Validated as UUID, resolved in live (pipeline steps 2 and 4, see [Form submission flow](form-submission-flow.md)) |
| `?lang` | Language tag of the rendered form | existing | Locale of the submission; also the language used to resolve the page |
| `?pid` | Main-resource page UUID, written by the SSR into the action URL | new | Validated as UUID; resolved in live; must belong to the form's site, else the form's own path is used as source |
| `X-Formidable-Tracking` | Base64 JSON, like the logic-state header: `tracker` is `on` when the tracker is present and activated in the page, `off` otherwise; `url` is `location.origin + location.pathname` as the visitor saw it, vanity URL included; `search` is `location.search`, campaign parameters included | new | `tracker` is one-way: `off` or absent means no event, `on` only lets the server-side gates decide. `url` and `search` are copied into the event source as `destinationURL` and `destinationSearch` after a same-origin and length check, with the `Referer` header as fallback; they never resolve anything. Malformed values read as `off` and no URL. Never fails the submission |
| Cookies | `wem-profile-id`, `wem-session-id`, fallback `context-profile-id` | existing (jExperience) | Read through `getProfileId` and `getWemSessionId`; never taken from a header or parameter |

The browser's test for `on` is the one the Forms bridge already performs, plus the activation
flag jExperience renders into `digitalData.wemInitConfig`: a consent manager that blocked the
script leaves `window.wem` undefined, a deactivated tree leaves the flag false. The exact flag
name in the tracker is confirmed at implementation time.

### The `form` event

```json
{
  "eventType": "form",
  "scope": "mysite",
  "source": {
    "itemType": "page", "itemId": "PAGE-UUID", "scope": "mysite",
    "properties": { "pageInfo": { "pageID": "PAGE-UUID", "pagePath": "/sites/mysite/home/contact", "language": "en",
                                  "destinationURL": "https://www.example.com/contact-us",
                                  "destinationSearch": "?utm_campaign=spring&utm_source=newsletter",
                                  "referringURL": "https://www.example.com/" } }
  },
  "target": { "itemType": "form", "itemId": "FORM-UUID", "scope": "mysite" },
  "flattenedProperties": {
    "fields": { "firstName": "Ada", "email": "ada@example.com", "topics": ["cdp", "forms"], "newsletter": "true" }
  }
}
```

- **Identifier.** `target.itemId` is the form's UUID, which is also the rendered `<form id>` and
  the `fid` parameter. Forms used a name derived from the node name; a UUID survives renames.
- **Values.** Every submitted value of every value-bearing field, as strings, multi-valued fields
  as arrays: text, textarea, email, number, range, color, date, datetime, hidden, radio, select,
  checkbox, and the extended consent, switch, rating and scale. Excluded: file fields, and fields
  marked sensitive once Formidable has that notion
  ([formidable#161](https://github.com/Jahia/formidable/issues/161), password first). Type
  conversion is the rule's job.
- **Source.** Two layers, the same two jExperience puts in every page event. Identity comes from
  the server: `pageID`, `pagePath` and `language` from the page resolved through `pid` and
  `lang`. The URL comes from the browser: `destinationURL` and `destinationSearch` are what the
  visitor actually saw, rewritten or vanity URL and campaign parameters included, so marketing
  can test pages by URL exactly as with page views. `referringURL` comes from the request.

### The mapping rule

```json
{
  "metadata": {
    "id": "formidable-form-mapping_mysite_FORM-UUID",
    "name": "Contact form", "description": "Formidable auto mapping",
    "scope": "mysite", "systemTags": ["formMappingRule"]
  },
  "priority": -1,
  "condition": { "type": "booleanCondition", "parameterValues": { "operator": "and", "subConditions": [
    { "type": "formEventCondition", "parameterValues": { "formId": "FORM-UUID" } },
    { "type": "booleanCondition", "parameterValues": { "operator": "or", "subConditions": [
      { "type": "sourceEventPropertyCondition", "parameterValues": { "scope": "mysite" } }
    ] } }
  ] } },
  "actions": [
    { "type": "setPropertyAction", "parameterValues": {
        "setPropertyName": "properties(firstName)", "setPropertyStrategy": "alwaysSet",
        "setPropertyValue": "eventProperty::flattenedProperties(fields)(firstName)" } },
    { "type": "setPropertyAction", "parameterValues": {
        "setPropertyName": "properties(interests)", "setPropertyStrategy": "setIfMissing",
        "setPropertyValueMultiple": "eventProperty::flattenedProperties(fields)(topics)" } }
  ]
}
```

- **Whole-site source condition** instead of one path per page hosting the form. A Formidable
  form is reusable content placed on several pages; the Forms bridge queried every display node
  to list paths, which is complexity without benefit here.
- **Value key by property type**, read from the property types endpoint: string →
  `setPropertyValue`, integer → `…Integer`, boolean → `…Boolean`, multivalued → `…Multiple`. The
  bridge never emitted the multivalued key; jExperience's own screen does. Date properties:
  confirm the Unomi 3 parameter before promising them.
- **Strategy per field**, `alwaysSet` by default or `setIfMissing`, the two values jExperience's
  screen offers.

### JCR definitions

```cnd
// formidable-engine — the marker: "this field can take part in a profile mapping"
[fmdbmix:profileMappableField] mixin

// formidable-elements and formidable-extended-inputs — every mappable field declares it as a supertype,
// exactly as it declares fmdbmix:element. Third-party field types opt in the same way.
[fmdb:inputText] > jnt:content, fmdbmix:element, fmdbmix:textField, fmdbmix:profileMappableField, ...
[fmdbext:switch]  > jnt:content, fmdbmix:element, fmdbmix:booleanField, fmdbmix:profileMappableField, ...

// formidable-jexperience-engine — one extension target, no list of field types, no dependency on the extended inputs
[fmdbmix:jExperienceProfileMapping] > fmdbmix:profileMappableField mixin
 extends = fmdbmix:profileMappableField
 itemtype = content
 - jExperienceProfileProperty (string, choicelist[formidableJExperienceProfileProperties]) indexed=no
 - jExperiencePrefillFromProfile (boolean) = false autocreated
 - jExperienceSetStrategy (string, choicelist) = 'alwaysSet' autocreated < 'alwaysSet', 'setIfMissing'
```

This follows the two-family rule of [CND module ownership](cnd-module-ownership.md): marker
mixins the back end reads to know what a type *is*, owned by the engine and claimed by field
types as supertypes; property mixins that add what the author configures, owned by the feature
module and attached through a marker, never through a list of concrete field types. The marker
plays for mapping the role `fmdbmix:formElement` plays for the field list.

A mixin *can* extend another mixin: the Content Editor resolves `extends` with
`type.isNodeType(target)`, which is true for every supertype of the field type, mixins included
(jcontent, `EditorFormServiceImpl.getExtendMixins`). The same method only offers an extension
when the module that declares it is installed on the site, so the "jExperience" section appears
exactly on the sites where the jExperience module of Formidable is deployed.

### Type compatibility in the dropdown

Which fields are mappable at all is decided by the marker mixin; which properties a mappable
field sees is decided from Formidable's value-kind mixins, so a third-party field that opts into
`fmdbmix:numberField` is treated like the built-in number field. The same table drives the value
key the rule builder picks, so the dropdown and the rule never disagree.

| Field kind (mixin) | Built-in fields | Profile property types offered | Cardinality |
|---|---|---|---|
| `textField` | text, textarea | `string` | single |
| `emailField` | email | `email`, `string` | single, or multivalued when the field is `multiple` |
| `numberField` | number, range; extended rating, scale | `integer`, `long`, `float`, `double` | single |
| `booleanField` | extended consent, switch | `boolean` | single |
| `dateField`, `datetimeLocalField` | date, datetime | `date` | single |
| `colorField` | color | `string` | single |
| `choiceField` | radio, select, checkbox | `string` (option values are strings) | single for radio and single select; multivalued for checkbox groups and multiple selects |
| no kind mixin | hidden | `string` | single |
| `fileField` | file | not mappable | — |

- **Cardinality must match.** A multivalued property is offered only to a multi-valued field and
  vice versa. Relaxing single field → multivalued property, which jCustomer accepts, is a
  possible later refinement.
- **Always removed**, whatever the type: properties flagged hidden, read-only or protected in
  their metadata, and those tagged `systemProfileProperties` or
  `hiddenFromFormMappingProperties`, the union of what the prototype, the Forms bridge and
  jExperience's own screen each exclude.
- **Where the stacks differ.** jExperience's Form mappings screen does not filter by type at all
  and lets jCustomer convert at rule time; the Forms bridge filters per input type with a table
  close to this one. Formidable follows the bridge's stance: a filtered dropdown is what makes
  the section usable for an author who does not know the profile schema.
- **To confirm on jCustomer 3**: the exact `valueTypeId` set exposed by the property types
  endpoint, and the `setPropertyAction` parameter for dates.

### The editor section: a Content Editor form override

An `extends` mixin shows up in the Content Editor as a switch-gated fieldset in the main
section. To give the mapping its own tab, the module ships a form override that declares a
**jExperience** section, exactly the way the engine declares the **Logic** section for
`fmdbmix:formLogicElement`. Java module, so the file lives under `META-INF`, not `settings`:

```
formidable-jexperience-engine/src/main/resources/META-INF/jahia-content-editor-forms/forms/fmdbmix_jExperienceProfileMapping.json
```

```json
{
  "nodeType": "fmdbmix:jExperienceProfileMapping",
  "priority": 2.0,
  "sections": [
    {
      "name": "jexperience",
      "labelKey": "fmdb.section.jexperience",
      "rank": 1.15,
      "fieldSets": [
        {
          "name": "fmdbmix:jExperienceProfileMapping",
          "rank": 0.0,
          "fields": [
            { "name": "jExperienceProfileProperty" },
            { "name": "jExperiencePrefillFromProfile" },
            { "name": "jExperienceSetStrategy" }
          ]
        }
      ]
    }
  ]
}
```

- **Rank.** Sections order by rank, and the ranks Formidable already uses are 1.10 for Logic and
  Responses, 1.20 Buttons, 1.30 Multi-step, 1.40 Style, 1.50 Validation messages. 1.15 gives
  jExperience a rank of its own and places it right after Logic on a field. Documenting the rank
  table and how to add a section is tracked in
  [formidable#311](https://github.com/Jahia/formidable/issues/311).
- **Labels.** `fmdb.section.jexperience=jExperience` plus the fieldset and property labels go in
  the module's own bundle, a Java bundle, so the French file escapes its accents as `\uXXXX`.
- **The switch stays.** The fieldset is left activatable, not `isAlwaysActivated`: switching it
  on is the author's "map this field to the visitor profile", and only fields carrying the mixin
  with a non-empty property take part in the rule and in prefill. Always-on would add the mixin
  to every mappable field saved on the site, which the
  [extension guide](../extension/how-to-extend-views-and-elements-from-third-party-module.md)
  accepts for a sample and flags as a deliberate choice for a product module.
- **No custom selector.** The standard choicelist selector renders the initializer's values, so
  no `fieldsets/` override is needed; the Logic section needs one only because its rules editor
  is a React selector of its own.

---

## Security and trust model

The principle is the one Formidable already applies to the logic-state header: the client
provides what only the browser knows, the server establishes everything it can, and no client
indication has power over validation, storage or another visitor's data.

### Gates the server establishes alone

| Gate | Source of truth | Applies to |
|---|---|---|
| jExperience installed on the site, configured, and jCustomer online | `getInstalledModules`, `isAvailable(siteKey)` | event, mapping, prefill |
| The form is referenced by a jCustomer rule with a form condition | Tracked conditions returned by jCustomer for the page source, cached 60 s | event |
| Tracking not deactivated on the page tree | jExperience's deactivation mixins on the resolved page's ancestors | event, prefill |
| Form identity, page identity, language | `fid` and `pid` validated as UUIDs and resolved in live, same site; `lang` | event source and target |
| Profile and session identity | Cookies, through the jExperience API | event, prefill |
| The submission was accepted | Pipeline steps 1 to 12 passed, actions succeeded | event |
| Bot filtering | jExperience's device-class check inside every context request | event, prefill |

### Threats

| Threat | What it takes | Effect | Control |
|---|---|---|---|
| Forge `X-Formidable-Tracking: on` while tracking is off in the browser | Any HTTP client | None by itself: the form must still be referenced in jCustomer, the site available, the tree not deactivated. Residual: a visitor who refused a consent manager can send their own submission, which they could already do against jCustomer's public collector | Server gates; profile consents checked server-side in a later phase |
| Forge `off`, or omit the header | Any HTTP client | No event. Scripts and bots leave no trace in jCustomer, as with Forms | By design |
| Flood jCustomer with events through the submit endpoint | A tracked form, and submissions that pass validation and captcha | Each event costs the attacker a full submission and a JCR write; jCustomer's public collector is the cheaper target | Existing pipeline limits and captcha; rate limiting stays a platform concern |
| **Poison another visitor's profile** | The victim's `wem-profile-id` cookie in the request | Cross-site submissions are rejected by the Origin and Referer check; without an XSS on the site a remote attacker has no such cookie. The profile id is a bearer token in the CDP model: anyone holding one can already push events through jCustomer's public routes. This design does not widen that exposure, it binds the event to the cookies of the request exactly as the tracker does | Same-origin check; never accept a profile id from a header or parameter |
| Poison one's own profile with crafted values | Submitting the form | Inherent to a form mapping, identical to Forms | Field constraints, property types on the jCustomer side |
| Garbage in indexed source fields | A forged page path or URL | Identity fields are safe: the browser sends an identifier, the server writes `pageID`, `pagePath` and `language`. The URL fields are browser-observed strings, exactly as in every jExperience page event today | `pid` resolution for identity; `destinationURL` and `destinationSearch` checked for same-origin and bounded in length, never used to resolve a node |
| Abuse of the trusted peer channel | Would need the server to forward client-chosen event fields | Server-side requests carry jExperience's peer header, which lets jCustomer accept restricted events | The server fixes event type, target, source and profile; only field values come from the client, under `flattenedProperties` |

Two rules for the [submission-flow trust model](form-submission-flow.md) and its Cypress
security specs: a missing or malformed tracking header or `pid` never fails the submission, and
it never makes the event leave.

---

## Performance

Server-side prefill has the same shape of cost as jExperience's own server-side
personalization, which also calls jCustomer during the render of a page holding SSR
experiences. The browser-computed context is not reused by the server in either case; only the
default browser mode avoids the call.

- **Per page view of a form with prefill**: K uncached field fragments, K being the prefilled
  fields, each a small React render plus a render-chain pass in the millisecond range; plus one
  jCustomer round trip per form, network-bound, which dominates.
- **Nothing is stored** for uncached fragments, so the cache does not grow. A per-user cache key
  would be the wrong lever for anonymous visitors.
- **The profile-cookie test is a guard, not an optimisation.** The tracker sets the cookie for
  every visitor after the first context call, guests included. It only spares the first page view
  and clients without JavaScript.
- **Real bounds.** Only forms with at least one prefill-enabled field pay anything. One call per
  form per request. A bounded in-memory cache of the requested properties per profile id, 30 to
  60 s, invalidated when that profile submits a mapped form. Short connect and read timeouts, an
  empty value on failure, never an error.
- **Submission side**: the tracked-conditions probe is cached per page for 60 s and the event is
  sent asynchronously; the visitor's response never waits.

**Plan B**, if the integration tests measure too much: render generic, cached fields with a
prefill marker and let a small client island ask the tracker for the properties. That is the
shape of the Forms solution, off the render path, with empty fields at first paint and a
JavaScript dependency.

---

## Decision log

| Date | Decision | Why |
|---|---|---|
| 2026-09-09 | Standard `form` event, sent server-side; the prototype's custom event and its six JSON schemas are dropped | Standard rules, goals and the Form mappings screen only work on the standard event |
| 2026-09-09 | The event carries all submitted values, minus files and sensitive fields | The event is the trace of the submission, as with Forms |
| 2026-09-09 | A submission is sent only when the form is referenced in jCustomer. The option "track every submission through a form checkbox, positively worded, unchecked by default" is recorded and not built | Keep the legacy behaviour; the default is a product decision to take with the PM |
| 2026-09-09 | Mapping and prefill configured per field: property dropdown, prefill toggle, strategy | Mirrors the per-field Forms node with its mapping-only variant |
| 2026-09-09 | A form-level mapping table is recorded as a later option | Needs a custom Content Editor selector; clearly more expensive than the per-field section |
| 2026-09-09 | The tracking header is one-way: it can only prevent the event | A forged "on" must gain nothing; pollution of jCustomer is a real cost |
| 2026-09-09 | Page reference travels as an identifier, `pid`; the language is already `lang`; the URL the visitor actually used, vanity URL and query string included, travels in the tracking header and lands in `destinationURL` and `destinationSearch` | Identity must be trusted, so the server resolves it; marketing tests pages by their real URL, which rewriting makes different from the JCR path, so the browser reports it, as jExperience's own page events do |
| 2026-09-09 | Prefill is server-side through a render filter and an OSGi service; the tracker-based island is plan B | Value at first paint, no JavaScript dependency; cost measured before committing |
| 2026-09-09 | New branch `feat/jexperience-integration` from main; the prototype's reusable pieces are ported onto it, and `save2jCustomer` is deleted once the port is done | Four months of drift, the action SPI changed, the event is redesigned |
| 2026-09-09 | A marker mixin in the engine, `fmdbmix:profileMappableField`, declared by every mappable field type; the jExperience mixin extends that single target instead of a list of field types | The engine-to-elements pattern already used for `fmdbmix:formElement`; removes the enumeration and the dependency on the extended inputs; verified against the Content Editor's `isNodeType` resolution of `extends` |

## Open questions

| Question | Owner | Status |
|---|---|---|
| Default tracking model: referenced-only as with Forms, or a form-level switch. With Forms the marketer turns tracking on from jExperience; with a switch the author decides in the form | PM | open |
| Honour profile consents server-side before sending the event and before prefilling | dev | phase 2 |
| Extended inputs (consent, switch, rating, scale): covered by declaring the marker mixin on them, no dependency needed; only their prefill rendering remains phase 2 | dev | settled |
| Prefill for choice fields, which have no default-value property today | dev | phase 2 |
| Date profile properties: which `setPropertyAction` parameter Unomi 3 expects | dev | verify |
| A local jCustomer for the dev loop and CI | dev | open |
| Upstream the generic profile-properties choicelist into jExperience, so the next module writes `choicelist[jExperienceProfileProperties='types=string,email;multiple=false']` instead of re-implementing the fetch and the filters | dev, jExperience team | proposal |

## Roadmap

| Phase | Deliverable | Proof |
|---|---|---|
| 1 | Module skeleton recalibrated on jExperience 4.2.1 with open OSGi ranges, added to the root pom. Marker mixin in the engine and on the field types, editor section, ported choicelist initializer with strategy. FR bundle with escaped accents, prototype's harness file dropped | JUnit on shape inference and filtering; module deploys next to jExperience |
| 2 | Mapping rule sync: rule builder, publication listener, diff, delete, resync on availability | Golden JSON test; Cypress: publish a mapped form, read the rule through the proxy |
| 3 | Observer SPI in the engine; `pid` routing param and tracking header in the elements module; submission listener with gates, tracked probe and `form` event | Pipeline unit tests; Cypress security specs for absent and malformed header and `pid`; direct submission with a profile cookie, then the profile read back |
| 4 | Prefill phase 1: service, render filter, text-like fields and hidden | Cypress: live page with a profile cookie shows the value; a second visitor does not; the page stays cached |
| 5 | Phase 2 items from the open questions as decided: choice fields, extended inputs, consents, hide or read-only after prefill | Per item |
| 6 | Changelog entry, submission-flow doc updated with the trust model, [formidable#153](https://github.com/Jahia/formidable/issues/153)'s jCustomer question answered | Review |

## Sources

- Jahia/jexperience, main: `ContextServerService`, `ContextServerServiceImpl`, `JExperienceInitializer`,
  `form-mapping.service.js`, `tests/cypress/utils/ruleHelpers.ts`, `live-mode/wem.js`,
  `ContextActivatorFilter`, `filters/ssr`, `wemContextServer.groovy`.
- Jahia/jexperience-forms-bridge, main: `JExperienceMapping.java`, `live-rules.drl`,
  `mfffPrefill.service.jsp`, `definitions.cnd`.
- Jahia/forms-core, main: `ffCallbackService.js`, `ffController.js`, `formDefinition.formView.jsp`,
  `FormSubmission.java`.
- Jahia/formidable: `api/FormAction.java`, `servlet/FormSubmissionPipeline.java`,
  `captcha/CaptchaRenderFilter.java`, `permissions/FormPublicationAclSyncListener.java`,
  `utils/optionsSource.server.ts`, `Form.client.tsx`; the `save2jCustomer` prototype branch and
  its `formidable-jexperience-engine` module.
- Jahia core 8.2: `AggregateCacheFilter`, the `expiration` request attribute and `j:expiration`.
- jcontent: `EditorFormServiceImpl.getExtendMixins`.
