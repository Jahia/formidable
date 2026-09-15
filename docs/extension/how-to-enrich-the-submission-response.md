# How to enrich the submission response

For the developer of a Jahia module that needs the browser to learn something once a form was
accepted: an identifier the page's scripts wait for, values a tracker should send, a token. The
engine's submit servlet answers `{"success": true}` on an accepted submission; a
`SubmissionResponseEnricher` adds entries to that body.

## When it runs

After the pipeline accepted the submission and **every action succeeded**. A rejected submission
(`4xx`, `5xx`) is never enriched: its body carries `success: false` and the error code only. The
form island then dispatches a bubbling `formidable:submitted` DOM event on the `<form>` element,
with `{formId, response}` as detail — `response` being the parsed body, your entries included —
so a script that knows nothing of the island can act on your block.

## Step 1: Implement the service

```java
@Component(service = SubmissionResponseEnricher.class, immediate = true)
public class MyEnricher implements SubmissionResponseEnricher {

    @Override
    public Map<String, Object> enrich(AcceptedSubmission submission) {
        if (!appliesTo(submission.siteKey())) {
            return Map.of();                       // nothing to add: an empty map, never null
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("formId", submission.formNode().getIdentifier());
        block.put("fields", submission.parameters());
        return Map.of("mymodule", block);          // one top-level key, named after your module
    }
}
```

Register it as an OSGi service (the `@Component` above does); the servlet tracks every
`SubmissionResponseEnricher` dynamically, no configuration needed. Import
`org.jahia.modules.formidable.engine.api` with an open range in your bundle, as for a `FormAction`.

## Step 2: Understand the input you receive

`AcceptedSubmission` is a record:

| Accessor | Value |
|---|---|
| `formNode()` | The form, read in the **live** workspace in the submission's locale |
| `siteKey()` | The key of the form's site |
| `locale()` | The locale of the submission (the `lang` parameter of the request) |
| `parameters()` | The accepted values by field name — declared, validated, non-file fields, each value as the submitter sent it. A snapshot: changing it changes nothing |

Files never reach an enricher: they belong to the actions.

## Step 3: Respect the rules of the body

- **Keys are top-level.** Name yours after your module. `success`, `errorCode`, `actionsCompleted`
  and `actionsTotal` belong to the servlet: an enricher writing one of them is logged and that key
  ignored, the rest of its entries kept.
- **Values are plain Java.** `Map`, `Collection`, `String`, `Number`, `Boolean`, nested as needed;
  they are serialised as JSON. No JCR node, no exception, nothing that is not data.
- **Never fail the submission.** The actions ran; an exception thrown by an enricher is logged and
  its entries left out, the `200` stands. Keep the work light: it runs in the request, before the
  visitor sees the success message.
- **Nothing personal that the page could not already know.** The body goes to the browser that
  submitted the form, and only there; still, return what the page needs, not the whole
  submission, when a subset does.

## Related example in this repository

`formidable-jexperience-engine`'s `SubmissionEventEnricher` returns
`jexperience: {formId, fields}` — the form's UUID and the accepted values of its profile-mappable
fields — for the client script that sends the form event through jExperience's tracker; see
[jExperience integration](../architecture/jexperience-integration.md), "Submitting".
