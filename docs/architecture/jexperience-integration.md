# jExperience Integration

> **Status: design specification, not implemented yet.** First design dated 2026-09-09
> (server-side event and prefill), **revised 2026-09-10 after Romain's review: everything the
> visitor triggers runs in the browser, through jExperience's tracker**. The implementation lands
> on the `feat/jexperience-integration` branch and this document is updated as each phase ships.
> Targets: Formidable 0.5.x, jExperience 4.x (the OSGi ranges stay open to 3.4+), jCustomer 3.x.

## Overview

When jExperience is installed and configured on a site, a Formidable form gains three
capabilities without anyone touching jExperience by hand:

- **A submission event.** Each accepted submission of a *tracked* form reaches jCustomer as the
  standard `form` event, sent by jExperience's tracker from the visitor's browser, with the values
  the server accepted. It feeds analytics, goals and segments exactly as a Forms submission does.
- **A form mapping.** Fields the author maps to profile properties produce a standard jCustomer
  form-mapping rule, created and kept in sync at publication. Marketers see it in the jExperience
  **Form mappings** screen.
- **Prefill.** A mapped field is pre-filled from the visitor's profile in the browser, with the
  profile properties the tracker loads anyway when the page opens.

What the 2026-09-10 revision changed, in one sentence: **Formidable's server never talks to
jCustomer about a visitor**. It publishes the mapping rule, feeds the profile-property dropdown of
the editor, and tells the browser which values it accepted; the tracker does the rest. The first
design (server-sent event, server-side prefill through a render filter, a server-side probe of
jCustomer's rules) is recorded in the [decision log](#decision-log) with the reasons it was dropped.

Non-goals for this iteration, each recorded with its reason in the decision log:

- A custom event type of Formidable's own. Standard rules never fire on it.
- A server-side event or prefill. Replaced by the browser-side design.
- A server-side probe of jCustomer's rules to know whether a form is referenced.
- A form-level mapping table in the editor. Recorded as a later option.

---

## Three things, one event

The author's mental model has three independent intents. jExperience implements them as one
event with two consumers, and that coupling drives every decision below.

| | Lives in | What it is |
|---|---|---|
| **The `form` event** | jCustomer | The trace of one submission: `eventType form`, target `itemType form` with the form's jExperience identifier as `itemId`, the page as source, the accepted values under `flattenedProperties.fields`. Persisted in jCustomer's event store, hence the statistics and dashboards. |
| **The mapping rule** | jCustomer | A rule tagged `formMappingRule` whose condition is a `formEventCondition` on that identifier and whose actions copy event fields into profile properties. Nothing about it lives in JCR. It is a *consumer* of the event: no event, no profile update. |
| **Prefill** | Browser | Reading the profile properties back into the form. It needs a mapped property to know what to read, but a mapping does not imply prefill. |

**What "tracked" means.** A form is tracked, and its submissions sent, in two cases:

1. **The author mapped at least one field** to a profile property. Mapping is a request to
   collect: without the event the mapping rule never fires.
2. **A marketer referenced the form in jCustomer** — a goal, a segment on a past event, a rule
   with a `formEventCondition`. jCustomer tells the browser about those references in the
   `trackedConditions` of every context response, and the tracker exposes them as
   `wem.getFormNamesToWatch()`. Formidable reads that list; see
   [The send condition](#the-send-condition-and-the-consent-gates) for why it is kept.

A form that is neither mapped nor referenced sends nothing, prefills nothing and has no rule —
the behaviour Forms has today. The option of a form-level switch ("send every submission") is
recorded and not built.

---

## Architecture

The mechanism fits in one sentence: **the browser talks to jCustomer through jExperience's
tracker, which is already on every live page; Formidable's server only publishes the mapping rule,
feeds the editor's property dropdown, and tells the browser what it accepted.** Formidable never
holds jCustomer credentials and never sees the visitor's profile.

```
Visitor's browser                          Formidable (server)          jExperience module      jCustomer
                                                                        (server, Jahia)         (CDP)

page  ◄── HTML (cached) with the form, an inline digitalDataOverrides
          push and a JSON config block ─── formidable-elements + the jExperience render filter

wem.js ─── context request, requiredProfileProperties included ───────────────────────────────►
       ◄── profile properties, trackedConditions ──────────────────────────────────────────────

island prefills the mapped fields

submit ─── POST ───────────────────────────► pipeline: whitelist, validation, actions
       ◄── 200 + accepted fields ────────────

island ─── wem.collectEvent(form event, accepted fields) ─────────────────────────────────────►
                                                                                                rule → profile

author  ─── publish ───────────────────────► listener ─── mapping rule ─── via the module ───►
```

One new Java module, `formidable-jexperience-engine`, holds every jExperience-specific piece.
Two small, generic changes land in the existing modules: a **submission-response enrichment SPI**
in the engine, and a **DOM event after a successful submission** in the elements' form island.
Nothing in Formidable depends on jExperience; the new module depends on both.

| Piece | Module | Role |
|---|---|---|
| `fmdbmix:profileMappableField` | formidable-engine, CND | Marker mixin, no properties: "this field can take part in a profile mapping". Declared as a supertype by every mappable field type in the elements and extended-inputs modules, and by third-party fields that want the feature. |
| `fmdbmix:jExperienceProfileMapping` | jexperience-engine, CND | Property mixin that `extends` the marker, so it reaches every field claiming it without naming a field type or depending on the extended inputs: profile property (choicelist), prefill toggle, write strategy. Surfaced as a "jExperience" section in the field's editor form through a Content Editor form override. |
| `ProfilePropertiesChoiceListInitializer` | jexperience-engine | Lists profile properties compatible with the field's shape (`FieldShapes`, inferred from the value-kind mixins), filtered on flags and system tags (`ProfilePropertyFilter`), through the module's admin client. Property types change rarely: `ProfilePropertyCatalog` keeps them one minute per site (a property just created in jExperience shows at the next opening); past the minute a failed read reports the schema unavailable rather than serving a list that may no longer be true. An unreachable jCustomer, or a schema with no property of the field's kind (jCustomer ships no boolean property), gives one message entry with an empty value, pre-selected so the closed select reads it (jcontent's `defaultProperty`), never a blank or broken dropdown. The field's current mapping is always offered, first and flagged "kept", when the list does not carry it: the Content Editor resets a value missing from its constraints, and a save during an outage — or after a property left the schema — must not wipe a mapping the author never touched. A message without a select would need a selector of the module's own — a UI bundle, left for a later phase. Nothing reusable exists today in jExperience; the generic half is written so it can be lifted there later. |
| `FormIdentifierListener` | jexperience-engine | Default-workspace listener on `fmdb:form`: when a form is created, or first edited after the module arrived, adds `fmdbmix:jExperienceForm` and writes the read-only `jExperienceIdentifier` (`formidable-jxp-<uuid>`) the author copies into a goal. Climbs from the `j:translation_*` subnode an i18n edit fires on. |
| `MappingRuleSyncListener` | jexperience-engine | Live-workspace publication listener that upserts the form's mapping rule, and deletes it when nothing is mapped, when the form is unpublished and when it is removed. Same pattern as `FormPublicationAclSyncListener`. |
| `FormJExperienceRenderFilter` | jexperience-engine | Render filter on `fmdb:form` (same family as `CaptchaRenderFilter`). When the module is available on the site, it writes next to the form: the inline `digitalDataOverrides.push` of the mapped profile properties, a JSON config block (identifier, mappings), and the module's client script once per page. Its output carries no visitor data: the fragment stays cached. |
| `formidable-jxp.js` | jexperience-engine, static resource | The client half: at `wemLoaded`, prefills the mapped fields from `wem.getLoadedContext().profileProperties`; on the island's `formidable:submitted` event, decides with `shouldCollect()` and sends the `form` event through `wem.collectEvent`. |
| `SubmissionResponseEnricher` SPI | formidable-engine, `api` package | Called by the pipeline after all actions succeeded, with the form node, the site and the validated parameters; returns a JSON block to add to the 200. The jExperience module contributes `jexperience: {formId, fields}` — the accepted values, minus files and sensitive fields — when it is available on the site. Enrichers never fail the submission. |
| `formidable:submitted` | formidable-elements, `Form.client.tsx` | DOM `CustomEvent` (bubbling) dispatched after a 200, carrying the form's UUID and the parsed response. The elements module knows nothing of jExperience: it only says "this was accepted, here is what the server answered". |

---

## Data flows

Four moments, on the same lanes every time. A step marked *via jExperience* goes through the
module's admin client; a step marked *response* is an answer. Everything involving a visitor is
in the first lane or in jExperience's tracker.

### Editing: choosing a profile property for a field

```
1  Author's browser    ──►  Formidable            opens a field, section "jExperience"
2  Formidable          ──►  jExperience module    which profile properties fit this field?
3  jExperience module  ──►  jCustomer             GET /cxs/profiles/properties/targets/profiles   (cached)
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

### Rendering and prefill: the tracker loads what the form needs

```
1  Visitor's browser   ──►  Formidable            GET page
2  Formidable          ──►  Visitor's browser     cached HTML: the form; before it, the render filter's
                                                  digitalDataOverrides.push({wemInitConfig: {requiredProfileProperties}}),
                                                  the JSON config block and the client script          (response)
3  wem.js (end of page)                           starts, reads the overrides
4  wem.js              ──►  jCustomer             /cxs/context.json — the request it makes anyway, now asking the properties
5  jCustomer           ──►  wem.js                context: profile properties, trackedConditions      (response)
6  formidable-jxp.js                              at wemLoaded: fills the mapped fields flagged prefill
```

Nothing personal is in the HTML: the page and the form fragment stay cached for everyone, and
one context request — the tracker's own — serves every prefilled field. The `push` must sit in the
markup before the tracker starts, which is why the render filter emits it and not the island: an
island runs after hydration, the tracker starts at the end of the body. jExperience documents
this extension point through its own tests (`wem.digitalDataOverrides.cy.ts`).

> **To verify first.** Hydration of the form island must not undo a prefill that ran before it:
> the client script waits for both `wemLoaded` and the island's `formidable:ready` event before
> writing values. Also the exact shape of `profileProperties` for multi-valued properties.

### Submitting: through the pipeline, then to the profile

```
1  Visitor's browser   ──►  Formidable            POST fid · lang, as today
2  Formidable                                     pipeline steps 1-12: whitelist, validation, captcha, actions
3  Formidable          ──►  Visitor's browser     200 + jexperience: {formId, fields} — the accepted values, purged   (response)
4  Form island                                    dispatches formidable:submitted with the response
5  formidable-jxp.js                              shouldCollect(formId)? → wem.collectEvent(wem.buildFormEvent(formId) + fields)
6  wem.js              ──►  jCustomer             the form event, with the tracker's profile and session cookies
7  jCustomer                                      stores the event, applies the mapping rule → profile updated
```

The event carries only what the pipeline accepted: undeclared fields, rejected values, files and
sensitive fields never reach jCustomer. A rejected submission (400) sends nothing, since the
island only dispatches on a 200.

---

## The identifier

The form's identity in jCustomer is **`formidable-jxp-<form UUID>`**: `target.itemId` of the
event, `formId` of the mapping rule, and what a marketer types in a goal.

- **Stable.** The UUID survives renames and moves; Forms used a node name and lost its history on
  a rename.
- **Valid.** Unomi's item schema constrains `itemId` to `^(\w|[-_@\.]){0,60}$`: 51 characters,
  letters, digits and dashes. A colon would be rejected.
- **Never the DOM id.** The rendered `<form id>` is the bare UUID, and stays so. jExperience's
  tracker attaches its own `submit` listener to any `<form>` whose `name` or `id` equals the
  `formId` of a tracked condition, then sends the raw DOM fields *before* validation. With an
  identifier that no DOM attribute carries, the tracker never finds a form to watch, whatever a
  marketer creates — so the only event is the island's, sent after the 200 with the accepted
  values. (Forms opted out with a `data-form-id` attribute; the tracker honours it in its initial
  scan only, and jExperience's observer of late forms ignores it, so it is not relied upon.)
- **Self-describing.** The `jxp` infix tells, in the Form mappings screen and in a goal report,
  that this is the jExperience identity of a Formidable form, not a Jahia identifier.
- **Readable in dashboards.** The event's target carries `properties: {name, path}` — Unomi's item
  schema allows a free `properties` object on the target, no schema extension needed — so a Kibana
  dashboard keys on `target.itemId` and labels with `target.properties.name`; a renamed form keeps
  its series.
- **Shown to the author.** The jExperience section of the form displays the identifier, copyable,
  because jExperience's goal editor asks for it as free text and offers no discovery of forms.

---

## The send condition and the consent gates

One function of the client script, `shouldCollect(formId)`, holds every reason to send or not.
It is deliberately the single place where Formidable depends on the tracker's API, so that a
change on jExperience's side is a change of one function and its test.

```js
const shouldCollect = (formId, config) =>
  window.wem !== undefined                                    // tracker present: no consent manager blocked it
  && window.wemLoaded === true                                // context loaded, callbacks executed
  && window.digitalData?.wemInitConfig?.activateWem !== false // the visitor did not disable tracking in jExperience
  && !window.digitalData?.wemInitConfig?.disableTrackedConditionsListeners
  && (config.mappings.length > 0                              // the author mapped a field
      || wem.getFormNamesToWatch().includes(formId));         // a marketer referenced the form (goal, segment, rule)
```

Why each line:

- **`window.wem` and `wemLoaded`.** A consent manager that blocks the script leaves no tracker;
  a page whose context failed to load has no profile to bind the event to. Both are jExperience's
  own signals, read as is.
- **`activateWem`.** jExperience's per-visitor switch ("disable tracking", the
  `enableWemActionUrl` action): when a visitor turned tracking off, Formidable sends nothing and
  prefills nothing.
- **`disableTrackedConditionsListeners`.** An integrator's page-level choice that turns the
  tracker's automatic form, video and link tracking off. Formidable's collection is of that
  kind: it follows the flag, exactly as Forms does — the Forms bridge sends only when
  `getFormNamesToWatch()` names the form, and that list stays empty under the flag. Formidable
  never sets the flag itself: it is global to the page and would silence other modules.
- **Mapped field.** The author asked for collection; without the event the mapping never fires.
- **`getFormNamesToWatch()`.** The `formId`s of the `trackedConditions` jCustomer returned for the
  page: goals and segments are rules in Unomi, so this is the complete list of marketer-side
  references, filtered for the current page as Unomi does (a goal with a start page only tracks
  that page). **Kept on purpose** (HDU, 2026-09-10): without it, a goal created in jExperience on
  a form nobody mapped would never count, and the marketer has no way to know why — Forms behaves
  as the list says, and marketers expect the same. Romain would rather see `trackedConditions`
  go; if jExperience removes or reworks them, this line is what changes. Two facts to remember
  when it does: the list is filled inside the tracker's context callback, so it must be read at
  submission time and not at page load; and it is empty under `disableTrackedConditionsListeners`,
  which is the behaviour wanted anyway.

The same gates apply to prefill, minus the last two: a visitor who refused tracking is not
prefilled either.

---

## Data contracts

### The render filter's contributions

Emitted before the form markup by `FormJExperienceRenderFilter` when the jExperience module is
available on the site; cacheable, identical for every visitor.

```html
<script>
  window.digitalDataOverrides = window.digitalDataOverrides || [];
  window.digitalDataOverrides.push({wemInitConfig: {requiredProfileProperties: ["firstName", "email"]}});
</script>
<script type="application/json" data-formidable-jxp="FORM-UUID">
  {"formId": "formidable-jxp-FORM-UUID", "name": "Contact form", "path": "/sites/mysite/contents/contact",
   "mappings": [{"field": "firstName", "property": "firstName", "prefill": true},
                {"field": "email", "property": "email", "prefill": false}]}
</script>
<script src="/modules/formidable-jexperience-engine/javascript/formidable-jxp.js" defer></script>
```

The `requiredProfileProperties` push is emitted only when at least one mapping asks for prefill;
the config block is emitted for every form, so that a form referenced by a goal without any
mapping is sent too. The property names in the push are exactly the mapped profile properties,
no wildcard: the profile stays private to what the form needs.

### Submission request and response

| Element | Value | Status | Server handling |
|---|---|---|---|
| `?fid` | Form node UUID | existing | Validated as UUID, resolved in live (pipeline steps 2 and 4, see [Form submission flow](form-submission-flow.md)) |
| `?lang` | Language tag of the rendered form | existing | Locale of the submission |
| Cookies | `wem-profile-id`, `wem-session-id` | existing (jExperience) | **Not read by Formidable.** The tracker attaches them to the event it sends; Formidable never sees, stores or forwards a profile id |
| Response `jexperience` block | `{formId, fields}` on a 200 | new | Added by the `SubmissionResponseEnricher` of the jExperience module when it is available on the site: the validated parameters after the field whitelist, minus file fields and fields marked sensitive ([formidable#161](https://github.com/Jahia/formidable/issues/161)), as strings, multi-valued as arrays |

Nothing of the first design's `pid` parameter and `X-Formidable-Tracking` header remains: the
page identity comes from the tracker's own `buildSourcePage()`, in the browser that shows it.

### The `form` event

Built by `wem.buildFormEvent(formId)` — event type, scope, source page, profile and session come
from the tracker — then completed by the client script:

```json
{
  "eventType": "form",
  "scope": "mysite",
  "source": { "itemType": "page", "itemId": "PAGE-UUID", "scope": "mysite",
              "properties": { "pageInfo": { "pageID": "PAGE-UUID", "pagePath": "/sites/mysite/home/contact",
                                            "destinationURL": "https://www.example.com/contact-us", "referringURL": "https://www.example.com/" } } },
  "target": { "itemType": "form", "itemId": "formidable-jxp-FORM-UUID", "scope": "mysite",
              "properties": { "name": "Contact form", "path": "/sites/mysite/contents/contact" } },
  "flattenedProperties": {
    "fields": { "firstName": "Ada", "email": "ada@example.com", "topics": ["cdp", "forms"], "newsletter": "true" }
  }
}
```

- **Values** come from the `jexperience.fields` block of the 200 — never from the DOM. Every
  value-bearing field the pipeline accepted, as strings, multi-valued as arrays; files and
  sensitive fields excluded server-side. Type conversion is the rule's job.
- **Source** is the page as the tracker sees it: identity, real URL, referrer — the same source
  jExperience puts in every page view, so marketing can test by URL.
- **Target properties** are the readable label of the identifier; Unomi's `FormSource` schema
  refers to the base item schema, whose `properties` is a free object.

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
    { "type": "formEventCondition", "parameterValues": { "formId": "formidable-jxp-FORM-UUID" } },
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
- **Name** = the form's title, what the Form mappings screen shows next to the identifier.

### JCR definitions

```cnd
// formidable-engine — the marker: "this field can take part in a profile mapping"
[fmdbmix:profileMappableField] mixin

// formidable-elements and formidable-extended-inputs — every mappable field declares it as a supertype,
// exactly as it declares fmdbmix:element. Third-party field types opt in the same way.
[fmdb:inputText] > jnt:content, fmdbmix:element, fmdbmix:textField, fmdbmix:profileMappableField, ...
[fmdbext:switch]  > jnt:content, fmdbmix:element, fmdbmix:booleanField, fmdbmix:profileMappableField, ...

// formidable-jexperience-engine — one extension target, no list of field types, no dependency on the extended inputs.
// `extends` only, no supertype: carrying the mapping must not make a node "mappable" — the type declares that.
[fmdbmix:jExperienceProfileMapping] mixin
 extends = fmdbmix:profileMappableField
 itemtype = content
 - jExperienceProfileProperty (string, choicelist[formidableJExperienceProfileProperties]) indexed=no
 - jExperiencePrefillFromProfile (boolean) = false autocreated indexed=no
 - jExperienceSetStrategy (string, choicelist[resourceBundle]) = 'alwaysSet' autocreated indexed=no < 'alwaysSet', 'setIfMissing'

// formidable-jexperience-engine — the form's identity in jCustomer, shown read-only to the author
[fmdbmix:jExperienceForm] mixin
 extends = fmdb:form
 itemtype = content
 - jExperienceIdentifier (string) indexed=no
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
- **Seen on jCustomer 3.0.0** (2026-09-11, default schema): `valueTypeId` ∈ {`string` ×24, `integer` ×5,
  `date` ×4, `email` ×1}; 7 read-only properties, 5 multivalued; `systemProfileProperties` on 5 and
  `hiddenFromFormMappingProperties` on 7. Still to confirm: the `setPropertyAction` parameter for dates.

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
- **The form's identifier.** A read-only `formidable-jxp-<uuid>` on the form itself, since
  jExperience's goal editor asks for it as free text: `fmdbmix:jExperienceForm` extends `fmdb:form`
  with `jExperienceIdentifier`, stamped by `FormIdentifierListener` when the form is created or
  first edited after the module's arrival, shown in a **jExperience** section of the form
  (`isAlwaysActivated` fieldset, `readOnly` field — `forms/fmdbmix_jExperienceForm.json`). No UI
  bundle for one read-only value; the author selects and copies it. A form saved before the
  module was deployed receives it at its next save.

---

## Security and trust model

The principle is the one Formidable already applies to the logic-state header: the client
provides what only the browser knows, the server establishes everything it can, and no client
indication has power over validation, storage or another visitor's data. In the browser-side
design the server's part is smaller and sharper: it decides **what** may reach jCustomer (the
accepted, purged values) and the tracker decides **for whom** (its own cookies) and **whether**
(its consent signals).

### What the server establishes alone

| Gate | Source of truth | Applies to |
|---|---|---|
| jExperience installed on the site, configured, and jCustomer reachable | `getInstalledModules`, `isAvailable(siteKey)` | render filter output, response block, mapping rule |
| The values that may leave | Pipeline steps 1 to 12: field whitelist, validation, actions succeeded; files and sensitive fields removed | response block, hence the event |
| The mapping rule | Publication listener in a system session, admin client owned by jExperience | rule |

### What the browser establishes

| Gate | Source of truth | Applies to |
|---|---|---|
| Profile and session identity | The tracker's first-party cookies, attached by the tracker itself | event, prefill |
| Consent | No tracker (blocked script), `activateWem` off, context not loaded | event, prefill |
| The form is tracked | Mapped field (in the cached config) or `getFormNamesToWatch()` (jCustomer's rules) | event |
| Bot filtering | jExperience's user-agent check before it injects the tracker (`ContextActivatorFilter`): a bot gets no `wem.js`, hence no event and no prefill | event, prefill |

### Threats

| Threat | What it takes | Effect | Control |
|---|---|---|---|
| Send an event with values the pipeline rejected | Any HTTP client calling jCustomer's public collector | Possible today for anyone, with or without Formidable: the public collector accepts any `form` event bound to one's own profile. Formidable adds no surface: its script only ever sends the server's `fields` block | Out of Formidable's hands by design; property types on the jCustomer side |
| Forge a `jexperience.fields` block | Would require forging the server's 200 | None: the block is computed server-side from the validated parameters | Pipeline |
| Poison another visitor's profile through Formidable | The victim's profile cookie | No new path: Formidable never handles a profile id; the tracker binds the event to the cookies of the browser it runs in, as for every page view | Same as jExperience |
| Prefill leaking to another visitor | A shared cache serving one visitor's HTML to another | Impossible by construction: no profile value is ever in the HTML; prefill happens in the browser from the tracker's own context response | Design |
| Read the mapped profile properties of a visitor | Being that visitor's browser | The tracker only requests the properties named in the push (the mapped ones); a page without a mapped form requests nothing more than today | Named properties, never `*` |
| A double event (tracker + island) on a form referenced by a rule | Rule `formId` equal to the DOM `<form id>` | Prevented: the identifier is `formidable-jxp-<uuid>`, the DOM id the bare UUID; the tracker finds no form to watch | Identifier rule, asserted by a Cypress spec |

---

## Performance

- **Rendering**: the form fragment and the page stay in Jahia's cache as today; the filter adds
  two small script tags and a JSON block, identical for every visitor. No call to jCustomer from
  Jahia at render time.
- **Prefill**: zero extra request — the properties ride on the context request the tracker
  makes on every page anyway; only pages whose form has a prefill-enabled mapping name any
  property.
- **Submission**: the pipeline does one more thing, serialising the accepted fields into the 200
  (microseconds); the event is one request from the browser to jCustomer, after the visitor
  already has the answer.
- **Editing**: the property-types call is cached in the module, one minute per site — see below.

### The profile-property catalog: when jCustomer is called, and why there is a cache

**When the call happens.** The Content Editor builds the whole form definition of a node in one
request (`forms.editForm`, or `forms.createForm` for a field being created): every section, every
fieldset — the dynamic ones included, switched on or not — and every choicelist initializer is
evaluated then. So the `formidableJExperienceProfileProperties` initializer runs **each time an
author opens or creates a mappable field**, whether or not they ever unfold the jExperience section
or switch the mapping on. Nothing in the module can make it lazier: the editor only re-asks a
choicelist on its own (`forms.fieldConstraints`) when it depends on another field's value.

**What the call costs.** `GET /cxs/profiles/properties/targets/profiles` through jExperience's admin
client (HTTPS, authenticated): 10 to 17 ms and 19 KB against a jCustomer on the same machine
(2026-09-11, 36 property types); a whole `editForm` of a text field, initializer included, 90 to
130 ms. A jCustomer on another network — the usual production layout — adds its round trip and TLS,
typically 50 to 200 ms, on every field opening of every author.

**What the cache does.** `ProfilePropertyCatalog` is one OSGi service per Jahia node, so its memory
is **shared by every author of the instance**, keyed by site: at most one jCustomer call per site
per minute, however many people edit forms. A property created in jExperience therefore shows in
the dropdown at the first opening after the minute — the tooltip of the property field says so, in
the author's words. **One duration rules everything**: a list is served while it is under a minute
old; past that, the next opening reads jCustomer again, and a read that fails yields the
"jExperience is not connected" entry instead of a list that may no longer be true (the stale entry
is dropped, so a later success starts a fresh minute). What the minute buys is thus not CPU: it
keeps the remote round trip out of every field opening and keeps authors from being a source of
traffic on jCustomer's admin API. What it does not buy, by choice, is hiding an outage: an author
opening a field while jCustomer is down reads the message.

**What it does not do.** No invalidation from the UI: the list refreshes by expiry (or when the
module restarts). A shorter time to live would not change the worst case (one call per site per
period) and would only shorten the wait of the one author who just created a property; no cache
would give the exact state of jCustomer at each opening at the price above. Serving an expired
list through an outage was built first and removed: a bounded grace period needed a second
duration to explain, and a list that may be an hour old is worse than a message. Kept at one
minute, one rule (decisions of 2026-09-11).

---

## Decision log

| Date | Decision | Why |
|---|---|---|
| 2026-09-09 | Standard `form` event; the prototype's custom event and its six JSON schemas are dropped | Standard rules, goals and the Form mappings screen only work on the standard event |
| 2026-09-09 | The event carries all accepted values, minus files and sensitive fields | The event is the trace of the submission, as with Forms |
| 2026-09-09 | Mapping and prefill configured per field: property dropdown, prefill toggle, strategy | Mirrors the per-field Forms node with its mapping-only variant |
| 2026-09-09 | A form-level mapping table is recorded as a later option | Needs a custom Content Editor selector; clearly more expensive than the per-field section |
| 2026-09-09 | A marker mixin in the engine, `fmdbmix:profileMappableField`, declared by every mappable field type; the jExperience mixin extends that single target instead of a list of field types | The engine-to-elements pattern already used for `fmdbmix:formElement`; removes the enumeration and the dependency on the extended inputs; verified against the Content Editor's `isNodeType` resolution of `extends` |
| 2026-09-09 | New branch `feat/jexperience-integration` from main; the prototype's reusable pieces are ported onto it, and `save2jCustomer` is deleted once the port is done | Four months of drift, the action SPI changed, the event is redesigned |
| 2026-09-10 | **Client-side design** (Romain): prefill through `digitalDataOverrides.push` and the tracker's context, event sent by the browser through `wem.collectEvent`. The server-side event (observer SPI, `pid`, tracking header) and the server-side prefill (render filter making the fragment uncacheable, `ProfilePrefillService`) are dropped | Server-side prefill wrote personal data into HTML that any cache in front of Jahia could serve to another visitor, and cost one jCustomer call per page view on the render path; the server-side event duplicated what the tracker already does with the visitor's own cookies. `digitalDataOverrides` is jExperience's documented extension point (its own Cypress tests exercise `wemInitConfig.requiredProfileProperties`) |
| 2026-09-10 | **The event carries the server's accepted values**, returned in the 200 by a generic `SubmissionResponseEnricher` SPI, not the DOM fields | Sending from the browser must not mean bypassing the pipeline: undeclared fields, rejected values, files and sensitive fields never reach jCustomer; a 400 sends nothing |
| 2026-09-10 | **No server-side probe of jCustomer's rules** (the first design's "is this form referenced here?", cached 60 s) (Romain) | The browser already holds the answer in the context the tracker loaded; recomputing it server-side costs a jCustomer round trip per submission for the same information |
| 2026-09-10 | **Tracked = mapped OR referenced in jCustomer**, the latter read from `wem.getFormNamesToWatch()`, kept on purpose; a form-level switch stays recorded, not built (HDU) | A goal or segment created in jExperience on a form nobody mapped must count, as with Forms; without the list the marketer has no way to know why it does not. Romain would rather see `trackedConditions` go: the dependency is confined to `shouldCollect()`, one function to change when jExperience reworks them |
| 2026-09-10 | **`disableTrackedConditionsListeners` is honoured**, and never set by Formidable (HDU) | It is the integrator's page-level "no automatic form tracking", possibly the outcome of a refusal; Forms goes silent under it (its watch list is empty) and Formidable does the same. Setting it would silence every other module on the page |
| 2026-09-10 | **Identifier `formidable-jxp-<uuid>`**, never equal to the DOM `<form id>`; `target.properties.name`/`path` for readability; shown in the editor (HDU) | The tracker attaches its own raw-fields listener to any `<form>` whose `id`/`name` matches a tracked `formId`: a distinct identifier is what makes the island the only sender, even once a marketer creates a goal. Unomi's `itemId` pattern allows it (51 chars, `[\w@.-]`); goals are typed by hand in jExperience, so the author must be able to copy it; dashboards keyed on an opaque id need the name as a label |
| 2026-09-10 | `data-form-id` is not used as the opt-out | Forms' convention, honoured by the tracker's initial scan only and ignored by jExperience's observer of late forms |
| 2026-09-11 | The form identifier is a read-only property stamped by a listener, not a custom selector | One read-only string does not justify a Module Federation bundle in the module; the Content Editor renders a `readOnly` field of the mixin; forms created before the module get it at their next save |
| 2026-09-11 | `fmdbmix:jExperienceProfileMapping` extends the marker without inheriting from it (the first draft wrote `> fmdbmix:profileMappableField` too) | Mappability is what a field *type* declares; the mapping is what an author configures. With the supertype, any node the mixin lands on — by API or import, a file field included — would pass every `isNodeType(marker)` check and the marker would stop meaning anything. `extends` alone is how every property mixin of the repository attaches to its target, and the Content Editor resolves it with `isNodeType`, so nothing needs the inheritance |
| 2026-09-11 | Field shape from the value-kind mixins; `fmdb:checkbox` is the one type name read | No mixin tells the checkbox group (always a list) from a radio group; every other cardinality comes from the `multiple` property |
| 2026-09-11 | `choicelist[resourceBundle]` for the write strategy | Labels for `alwaysSet` / `setIfMissing` come from the module's bundle instead of raw values in the dropdown |
| 2026-09-11 | The profile-property catalog keeps its list one minute per site, shared by every author; an empty match and an unreachable jCustomer each yield one explanatory entry, pre-selected (HDU) | The editor evaluates the initializer at every opening of a mappable field, section unfolded or not; one minute keeps the remote round trip out of the editor at the cost of a one-minute delay after a property is created — said in the tooltip. Removing the cache was weighed and declined: same worst case. jCustomer ships no boolean property, so a blank dropdown had to explain itself |
| 2026-09-11 | The field's stored mapping is always in the dropdown, flagged "kept", when the list lacks it | jcontent's single select resets a value absent from its constraints: without this, opening a mapped field while jCustomer is down (or after the property left the schema) and saving anything would wipe the mapping silently. The author keeps it or picks another entry, knowingly |
| 2026-09-11 | One duration: an expired list is never served, a failed read past the minute gives the message (HDU) | The first design served the previous list through any outage; a bounded grace period would have needed a second duration to explain, and a list that may be an hour old misleads an author more than a message. What the author sees is under a minute old, or says why it is not |
| 2026-09-11 | Local stack: the test Jahia joins the jCustomer compose network with a fixed address, jExperience 4.2.1 is installed by jar upload | jCustomer trusts privileged calls by IP; the artifact is only on Nexus' internal group, so `installModule mvn:` is a silent no-op on the test container (kit: `~/Jahia/modules/Formidable/jexperience/README.md`) |

## Open questions

| Question | Owner | Status |
|---|---|---|
| Hydration vs prefill ordering: the client script waits for `wemLoaded` and `formidable:ready`; confirm React does not reset the values, and the shape of multi-valued `profileProperties` | dev | verify in phase 4 |
| Honour profile consents (`profile.consents`) before sending and before prefilling, on top of the tracker-level gates | dev | phase 2 |
| Extended inputs (consent, switch, rating, scale): covered by the marker mixin, no dependency; their prefill rendering | dev | phase 2 |
| Prefill for choice fields, which have no default-value property today | dev | phase 2 |
| Date profile properties: which `setPropertyAction` parameter Unomi 3 expects | dev | verify |
| A local jCustomer for the dev loop and CI (docker compose jexperience + jcustomer + elasticsearch) — the phases' proofs are Cypress against it | dev | **dev loop done** 2026-09-11 (jCustomer 3.0.0 + jExperience 4.2.1 next to the test Jahia, kit on the developer's machine); the CI compose profile is still to add |
| Upstream the generic profile-properties choicelist into jExperience | dev, jExperience team | proposal |
| If jExperience removes or reworks `trackedConditions`: replace the `getFormNamesToWatch()` line of `shouldCollect()` by whatever replaces it | dev | when it happens |

## Roadmap

| Phase | Deliverable | Proof |
|---|---|---|
| 1 | Module skeleton recalibrated on jExperience 4.2.1 with open OSGi ranges, added to the root pom. Marker mixin in the engine and on the field types, editor section with the copyable identifier, ported choicelist initializer with cache, strategy and failure handling. FR bundle with escaped accents, prototype's harness file dropped | **Shipped 2026-09-11.** 23 JUnit tests (shape inference, property filter, catalog cache and failure paths, initializer, identifier); deployed on the test instance next to jExperience 4.2.1: the section lists 18 properties on a text field, the identifier is stamped on creation and on the first edit |
| 2 | Mapping rule sync: rule builder on `formidable-jxp-<uuid>`, publication listener, diff, delete on unpublish/removal, resync on availability | Golden JSON test; Cypress: publish a mapped form, read the rule through the proxy |
| 3 | Engine `SubmissionResponseEnricher` SPI and the elements' `formidable:submitted` event; render filter (overrides push, config block, script); client script with `shouldCollect()` and the event | Pipeline unit tests; Cypress: submit with a profile cookie, the event and the profile read back through the proxy; the tracker attaches no listener to the form (the DOM id is not the identifier); a form referenced only by a goal sends; no send under `activateWem` off |
| 4 | Prefill: text-like fields and hidden, from the context's profile properties | Cypress: live page with a profile cookie shows the value after `wemLoaded`; a second visitor does not; the page stays cached (same HTML for both) |
| 5 | Phase 2 items from the open questions as decided: choice fields, extended inputs, consents | Per item |
| 6 | Changelog entry, submission-flow doc updated with the response block, [formidable#153](https://github.com/Jahia/formidable/issues/153)'s jCustomer question answered | Review |

## Sources

- Apache Unomi tracker 1.5.0 (`apache-unomi-tracker`, the tracker jExperience embeds):
  `startTracker(digitalDataOverrides)`, `_registerListenersForTrackedConditions`,
  `_formSubmitEventListener`, `buildFormEvent`, `getFormNamesToWatch`,
  `disableTrackedConditionsListeners`, `requiredProfileProperties`.
- Jahia/jexperience, main: `live-mode/wem.js` (loader, `_formFactorySubmitEventListener`,
  `_observeForms`, `wemLoaded`), `tests/cypress/e2e/live/wem.digitalDataOverrides.cy.ts` and its
  fixture, `ContextServerService`, `form-mapping.service.js`, `goals/goal-formGoal*`,
  `ContextActivatorFilter` (`isContextEnabled`: no tracker for a bot, `deviceClassIsUnauthorized`).
- Jahia/jexperience-forms-bridge, main: `JExperienceMapping.java`, `mfffPrefill.service.jsp`
  (prefill by a second context request), `definitions.cnd`.
- Jahia/forms-core, main: `formDefinition.formView.jsp` (`data-form-id`), `ffCallbackService.js`.
- Apache Unomi JSON schemas: `events/form/form.json`, `form.source.json`, `items/item.json`
  (`itemId` pattern, free `properties`), `form.flattenedProperties.fields.json`.
- Jahia/formidable: `servlet/FormSubmitServlet.java` (the 200 body), `servlet/FormSubmissionPipeline.java`,
  `captcha/CaptchaRenderFilter.java`, `permissions/FormPublicationAclSyncListener.java`,
  `Form.client.tsx`, `useFormSubmission.ts`; the `save2jCustomer` prototype branch.
- jcontent: `EditorFormServiceImpl.getExtendMixins`.
