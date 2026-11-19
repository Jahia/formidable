# formidable-test-module-quarantine

A reference hold for the tests of the quarantine contract of `formidable-engine`. The engine offers the contract: the switch `fmdbmix:quarantine`, the lists "Checks" and "Actions after release", the extension point `SubmissionHold` and the service `DeferredActions` ([ADR 0002](../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md)). This module implements a whole quarantine on that contract, so that the Cypress and JUnit suites prove the behaviour end to end.

**Never deploy this module in production.** Its checks are stubs: they read their outcome from a marker in the uploaded file. They detect nothing.

## What is built

- The intake: the reference hold stores a submission in the quarantine tree and returns. No check runs on the request thread.
- The quarantine tree and its store, `QuarantineStore`, an OSGi service.
- The inspection of the tree, for the specs.

The checks, their chain, the state machine, the run and the release come with the next parts of the module.

## Which forms the module owns

The reference hold ranks above the sample hold of `formidable-test-module-samples-java`, which declares no ranking. The engine binds the hold of the highest ranking, so it binds this one.

The reference hold owns a form only when the "Checks" list of the form (`qualifiers`) holds at least one node of `fmdbquar:remoteStubCheck` or `fmdbquar:inProcessStubCheck`. It hands any other form to the next registered `SubmissionHold` by ranking, such as the sample hold. So the specs of the sample hold stay green when both modules are deployed. The reference hold refuses the submission in two cases:

- no other hold is registered;
- the list of the form cannot be read. The next hold may release the files unchecked, so the reference hold does not hand the form on.

## The quarantine tree

The store writes the tree in live, with a system session:

```text
/sites/<site>/formidable-quarantine              fmdbquar:quarantineRoot   ACL inheritance cut, no ACE
  └─ <formId>                                    fmdbquar:formQuarantine
       └─ YYYY/MM/DD                             fmdbquar:dateFolder (UTC date of intake)
            └─ held-<uuid>                       fmdbquar:heldSubmission
                 - state, createdAt, attempts, nextAttempt, completedActions
                 - formId, siteKey, locale, originLanguage, originReferer, originTimeZone
                 - valueFields, sensitiveFields   the field names, in the order of the submission
                 ├─ values                       fmdbquar:values           the public values
                 ├─ sensitive                    fmdbquar:sensitiveValues  the sensitive values
                 ├─ item0, item1, ...            fmdbquar:item             one per upload
                 │    - data (binary, indexed=no), sha256, size, fileName, fieldName, mimeType
                 ├─ checks                       fmdbquar:records          added by the checks
                 └─ audit                        fmdbquar:records          added by the operations
```

- The state is one of `held`, `checking`, `releasing`, `completed`, `rejected` and `needsReview`.
- No node of the tree is a file. An item holds its bytes in its own binary property, never in `jcr:data` on a `jnt:resource`. No core listener, no `/files` servlet and no text extraction reads it.
- The SHA-256 of a file is computed once at intake, from the bytes read once from `SubmittedFile.data()`.
- The sensitive values live in the node `sensitive`, apart from the public values in the node `values`. No endpoint reads them, and no log line names them. `deleteValues` removes both nodes and the bytes of every item. The record stays: the state, the field names, and of each item its name, size and SHA-256.
- `checks` and `audit` accept any `jnt:content`. A later part of the module adds its own record types there with no change to the types above, so a lab that runs this version takes the next one without a refused CND change.

## The inspection

`GET /modules/formidable-quarantine/inspection?site=<siteKey>&form=<formId>` answers the held submissions of a form, oldest first:

```json
{
  "site": "formidableTestSite",
  "form": "<formId>",
  "submissions": [{
    "path": "/sites/.../held-<uuid>",
    "state": "held",
    "createdAt": "2026-11-19T18:30:00Z",
    "attempts": 0,
    "nextAttempt": null,
    "completedActions": 0,
    "fields": ["fullName"],
    "sensitiveFields": [],
    "valuesPresent": true,
    "items": [{"name": "item0", "fieldName": "supportingDocument", "fileName": "claim-note.txt",
               "mimeType": "text/plain", "size": 29, "sha256": "<hex>", "binaryPresent": true}]
  }]
}
```

It answers server administrators alone: `root` and the members of the server group `administrators`. Any other caller, a site administrator included, gets 403. It never answers a value, a sensitive value or a byte.

## Node types

The module declares its types in its own namespace, `fmdbquar` (`http://www.jahia.org/jahia/fmdbquar/nt/1.0`).

| Type | Role |
|---|---|
| `fmdbquar:remoteStubCheck` | A stub of a check that asks a remote service |
| `fmdbquar:inProcessStubCheck` | A stub of a check that reads the file inside the JVM |
| `fmdbquar:quarantineRoot` | The root of the tree of a site |
| `fmdbquar:formQuarantine` | The held submissions of one form |
| `fmdbquar:dateFolder` | A year, a month or a day |
| `fmdbquar:heldSubmission` | One held submission |
| `fmdbquar:values`, `fmdbquar:sensitiveValues` | The public values, and the sensitive values apart |
| `fmdbquar:item` | One upload, with its bytes and their SHA-256 |
| `fmdbquar:records` | The containers `checks` and `audit` |

Each check type takes the marker of the engine, `fmdbmix:qualifier`, as a supertype. The "Checks" list accepts no other kind of node.

## Java packages

The base package is `org.jahia.test.modules.formidable.quarantine`. The module compiles against the `api` package of the engine only.

| Package | Content |
|---|---|
| `hold` | The reference hold and its rule of ownership |
| `store` | The quarantine tree: `QuarantineStore` and its JCR implementation |
| `check`, `chain`, `state`, `stub` | The checks, their chain, the state machine and the stubs. The chain and the state machine import no JCR and no OSGi class |
| `run`, `ops`, `web` | The run over the due submissions, the operations and the endpoints, the inspection among them |

## Build and test

```
mvn test -pl jahia-test-module/formidable-test-module-quarantine -am
```

The spec `tests/cypress/e2e/quarantine/97-quarantine-intake.cy.ts` covers the intake, and `tests/scenarios/quarantine.md` states its scenarios.
