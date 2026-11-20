# formidable-test-module-quarantine

A reference hold for the tests of the quarantine contract of `formidable-engine`. The engine offers the contract: the switch `fmdbmix:quarantine`, the lists "Checks" and "Actions after release", the extension point `SubmissionHold` and the service `DeferredActions` ([ADR 0002](../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md)). This module implements a whole quarantine on that contract, so that the Cypress and JUnit suites prove the behaviour end to end.

**Never deploy this module in production.** Its checks are stubs: they read their outcome from a marker in the uploaded file. They detect nothing.

## What is built

- The intake: the reference hold stores a submission in the quarantine tree and returns. No check runs on the request thread.
- The quarantine tree and its store, `QuarantineStore`, an OSGi service.
- The inspection of the tree, for the specs.
- The two stub checks, as `Qualifier` services, and the control of their outage, for the specs.
- The chain of the checks and the state machine, as pure classes.
- The run of the checks over the due held submissions, started by a spec through "run now".
- The release: the SHA-256 computed again, then the actions after release, with a checkpoint after each one.
- The operations of a manager (retry, release, resume, reject), checked against a permission of the module.
- The audit of each operation, and of each final decision of the run.
- The retention of the rejected held submissions.

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
                 - state, createdAt, changedAt, attempts, nextAttempt, completedActions
                 - formId, siteKey, locale, originLanguage, originReferer, originTimeZone
                 - valueFields, sensitiveFields   the field names, in the order of the submission
                 ├─ values                       fmdbquar:values           the public values
                 ├─ sensitive                    fmdbquar:sensitiveValues  the sensitive values
                 ├─ item0, item1, ...            fmdbquar:item             one per upload
                 │    - data (binary, indexed=no), sha256, size, fileName, fieldName, mimeType
                 ├─ checks                       fmdbquar:records          added by the run
                 │    ├─ item0                   fmdbquar:itemOutcomes
                 │    │    └─ <check>            fmdbquar:outcome          outcome, reason, retryable, at, ticket, attempts, sha256
                 │    └─ decision                fmdbquar:decision         reason, itemName, checkName, sha256, failedAction, at
                 └─ audit                        fmdbquar:records          added by the operations and the run
                      └─ record1, record2, ...   fmdbquar:auditRecord      operation, userName, fromState, toState, at
```

- The store creates the root on first use and cuts its ACL inheritance. It uses a node that already exists at `/sites/<site>/formidable-quarantine` only when the node is a `fmdbquar:quarantineRoot`, its `j:acl` has `j:inherit=false`, and its `j:acl` holds no access control entry. It uses a form folder or a date folder that already exists only when the folder is of the type of the store and has no `j:acl` of its own. Any other node refuses the hold: the message of the refusal names its path, the engine logs it, and the visitor gets FMDB-018. A node that someone else created there could give its readers the held values and bytes.
- `changedAt` records the last change of the state; the retention reads it.
- No node of the tree is a file. An item holds its bytes in its own binary property, never in `jcr:data` on a `jnt:resource`. No core listener, no `/files` servlet and no text extraction reads it.
- The SHA-256 of a file is computed once at intake, from the bytes read once from `SubmittedFile.data()`.
- The sensitive values live in the node `sensitive`, apart from the public values in the node `values`. No endpoint reads them, and no log line names them. `finish` writes the final state, `completed` or `rejected`, and removes both nodes and the bytes of every item, in one save. A failure writes neither, so a final state never keeps its data: a release stays `releasing` and a rejection by the checks stays `checking`. The record stays: the state, the field names, and of each item its name, size and SHA-256.
- An outcome records the SHA-256 of the bytes it was given for, never a byte. The run reads an outcome only for the bytes of that SHA-256.
- `checks` and `audit` accept any `jnt:content`: the outcomes, the decision and the audit records (`fmdbquar:auditRecord`) live there, and a new record type needs no change to the types above, so a lab takes it without a refused CND change.

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
    "changedAt": "2026-11-19T18:30:00Z",
    "attempts": 0,
    "nextAttempt": null,
    "completedActions": 0,
    "fields": ["fullName"],
    "sensitiveFields": [],
    "valuesPresent": true,
    "items": [{"name": "item0", "fieldName": "supportingDocument", "fileName": "claim-note.txt",
               "mimeType": "text/plain", "size": 29, "sha256": "<hex>", "binaryPresent": true}],
    "checks": [{"item": "item0", "check": "remoteCheck", "outcome": "PASS", "reason": "stub.marker",
                "retryable": false, "ticketPresent": false, "attempts": 0, "sha256": "<hex>",
                "at": "2026-11-19T18:31:00Z"}],
    "decision": null,
    "audit": [{"operation": "run", "user": null, "from": "checking", "to": "needsReview",
               "at": "2026-11-19T18:31:00Z"}]
  }]
}
```

`checks` holds the last outcome of each check for each item: `PASS`, `FAIL`, `INCONCLUSIVE`, `PENDING` or `FAILED`. It never answers the ticket of a pending outcome. `decision` says why the run stopped the submission, or is `null`: `{"reason", "item", "check", "sha256", "failedAction", "at"}`. `audit` holds the audit records, oldest first: see "The audit" below.

It answers server administrators alone: `root` and the members of the server group `administrators`. Any other caller, a site administrator included, gets 403. It never answers a value, a sensitive value or a byte.

## The checks

Each check node of the "Checks" list carries two settings:

- `onInconclusive`: what an `INCONCLUSIVE` answer means. `review` (the default) sends the submission to `needsReview`; `reject` rejects it.
- `onUnavailable`: what a failure of the service of the check means. `retry` (the default) tries again at a later run, until the attempts are exhausted, then sends the submission to `needsReview`; `reject` rejects it.

There is no "accept": the chain fails closed. The run matches each check node to the `Qualifier` service of its node type. A node that no service serves sends the submission to `needsReview`.

The two stubs apply to `text/plain` files alone, and answer the first marker line of the file: `VERDICT:PASS`, `VERDICT:FAIL`, `INCONCLUSIVE`, `PENDING:<n>` (pending for `n` polls, then `PASS`), or `DOWN` (a retryable failure while the outage is on, then `PASS`). A file with no marker is inconclusive. The remote stub (`fmdbquar:remoteStubCheck`) declares that a remote service reads the bytes. The in-process stub (`fmdbquar:inProcessStubCheck`) declares that it reads them inside Jahia: the chain refuses a list where it would read a file before a remote check passed it. The tickets of the pending answers live in memory, and a restart of the module loses them: the next poll then fails, and the submission goes to `needsReview`, or to `rejected` when the check sets `onUnavailable` to `reject`. A check that throws instead of answering counts as a retryable failure of its service.

## The run

`QuarantineRun` makes one pass over the due held submissions of a site, or of one form. It has no thread and no scheduler: a spec starts it through "run now". A submission is due in state `held`, or in state `checking` when it has no next attempt or its next attempt is reached.

For each due submission:

1. A `held` submission moves to `checking`.
2. A submission whose form is gone goes to `needsReview` with the decision `FORM_GONE`, and keeps its data: no check runs. Any other failure to read the checks of the form leaves the submission `checking`, and the run reports it.
3. The chain decides the calls to make. The run calls each check, and records each answer under `checks/<item>/<check>`, with the SHA-256 of intake. A result starts the failures of that check over; a retryable failure counts one more.
4. The chain decides again. A pass calls each check at most once for each file. When a pass brings no new `PASS` and no final decision, the submission stays `checking`: `attempts` counts one more, from 0 when the pass brought a new `PASS`, and `nextAttempt` follows the back-off.
5. A final decision moves the state, and `checks/decision` records why:
   - a rejection (a `FAIL`, or a setting `reject`) moves to `rejected`, and deletes the values, the sensitive values and the bytes in the same save. If the save fails, the submission stays `checking` with its data, and the next pass decides again;
   - a review (inconclusive, a file no check applies to, attempts exhausted, an in-process check first, a check that no service serves) moves to `needsReview`, and keeps them;
   - a release runs the release below.

Every move of the state goes through the state machine. Each submission is read and written through the system sessions of the stores. A submission that fails is reported in the answer of the run, and the pass goes on with the next one.

## The release

1. The release reads the bytes of every item again, and computes their SHA-256 again. A difference from the SHA-256 of intake, or an item with no bytes, refuses the release: the state moves to `needsReview`, the decision is `INTEGRITY_FAILED` and names the item, and a log line names the item. No action runs.
2. Otherwise the state moves to `releasing`, and the engine's `DeferredActions` runs the actions after release with the released submission, from the index `completedActions`. After each action, the release writes its index plus one to `completedActions`.
3. A failed action stops the run: the state moves to `needsReview`, `completedActions` holds the index to resume from, and the decision is `ACTION_FAILED` with that index. The values and the bytes stay for the resume.
4. Once every action ran, the state moves to `completed`, and the values, the sensitive values and the bytes are deleted, in one save. The record and the outcomes stay. If the save fails, the submission stays `releasing` with its data.

`QuarantineRun.release()` gives the release to an operation that releases or resumes a submission by hand: the operation moves the state to `releasing` first, then calls it.

A release that stops half way, on a failure of the store, an error of an action or a stop of the JVM, leaves the submission in `releasing`. The run never takes it. A manager resumes it, from `completedActions`, so no action runs twice, or rejects it: the state machine lets resume and reject leave `releasing` as they leave `needsReview`.

## The permission of a manager

A manager of the quarantine of a form holds the permission `fmdbquarManageQuarantine` on the form. The module declares the permission in `src/main/import/permissions.xml`, and creates the role `fmdbquar-quarantine-manager`, which grants it, when it starts (`ManagerRoleInitializer`). The engine creates its role `fmdb-results-reader` the same way. An administrator grants the role to a user or a group on the form, then publishes the form. The operations check the permission on the live form, in a session of the user of the request, so the ACL of the form decides.

A server administrator (`root`, or a member of the server group `administrators`) is a manager of every form. No other role gives the permission: a results reader, an editor or a site administrator of the site is not a manager.

We chose the simplest model that a Jahia module ships and that a spec grants with `grantRoles`. A permission of the module, and not the role alone, lets the check use `hasPermission` on the form.

## The operations

An operation runs on a held submission in state `needsReview`, and a resume or a reject also on a release that stopped half way in `releasing`. It checks, in this order, that a held submission is at the path, that the user is a manager of its form, and that the state and the decision allow the operation. A refused operation changes nothing. An operation takes the lock of the run: it never runs while the run checks or releases submissions, so a submission in `releasing` is a release that stopped, not one under way.

| Operation | Allowed when | What it does |
|---|---|---|
| `retry` | `needsReview` | Sets the attempts to 0, removes every outcome that is not a `PASS` (with its count of failures), and moves to `checking`. The next run calls those checks again |
| `release` | `needsReview`, and the decision is not `ACTION_FAILED` | Moves to `releasing`, then runs the release: the SHA-256 is computed again, then the actions after release run. The state is `completed`, or `needsReview` again |
| `resume` | `needsReview`, and the decision is `ACTION_FAILED`; or `releasing` | The same, from the index `completedActions`: the actions that ran do not run again |
| `reject` | `needsReview` or `releasing` | Moves to `rejected`, and deletes the values, the sensitive values and the bytes. The outcomes and the decision stay |

A release after a failed action is refused: it would run the actions again from the first, and store or send the submission twice. The release starts at `completedActions` in any case, so this is a rule of the module, not of the release.

## The audit

Each operation that ran writes one audit record under `audit`: the operation, the name of the user, the state before and after, and the time. The run also writes one when it releases, rejects or sends a submission to review, with the operation `run` and no user. An operation or a run whose release stops half way writes one too, with the state reached, `releasing`, so the audit names who started the release that a manager then resumes or rejects. A submission that keeps checking gets none, so a run that waits for a back-off adds nothing. No audit record holds a value, a file name or a byte. A failure to write the audit record of the run is logged, and the run goes on: the state already moved.

## The retention

The retention purges each `rejected` held submission of a site whose last change (`changedAt`) is older than `retentionDays`: the whole node, with its items, its values, the outcomes, the decision and the audit. It keeps every other state. A `completed` submission holds no value and no byte any more, so the retention keeps its record. It has no scheduler: a spec starts it through its endpoint.

The repository frees the bytes of a purged item only when the garbage collection of its datastore runs.

## The endpoints for the specs

Each endpoint answers server administrators alone, as the inspection does: any other caller gets 403, and nothing runs or changes. A `POST` carries `X-Requested-With`: without it, every `POST` below answers 403, and nothing runs or changes. No answer holds a value or a byte, and an unexpected failure of a submission is answered by the name of its class alone.

| Endpoint | Answer |
|---|---|
| `POST /modules/formidable-quarantine/run?site=<siteKey>[&form=<formId>][&ignoreSchedule=true]` | One pass of the run over the due submissions of the site, or of the form. `ignoreSchedule=true` also takes a `checking` submission whose next attempt is ahead. `{"site", "form", "settings", "processed": [{"path", "form", "from", "state", "error"}]}`: the state of each submission after the run, and why the run of a submission failed, or `null` |
| `GET /modules/formidable-quarantine/run` | `{"settings": {"allowInProcessFirst", "maxAttempts", "backoffInitialSeconds", "backoffCapSeconds"}}`: the settings the next run applies |
| `GET /modules/formidable-quarantine/stub/calls[?check=<name>]` | `{"outage", "calls": [{"nodeType", "checkName", "sha256", "kind"}]}`: the calls of the stubs, oldest first, all or those of one check node; `kind` is `SUBMIT` or `POLL` |
| `POST /modules/formidable-quarantine/stub/calls/clear` | Forgets the calls |
| `POST /modules/formidable-quarantine/stub/outage/lift`, `.../outage/set` | Lifts or sets the outage of the stubs. It is on when the module starts |
| `POST /modules/formidable-quarantine/retention?site=<siteKey>[&olderThanDays=<n>]` | Purges the rejected held submissions of the site older than the limit. `olderThanDays` replaces `retentionDays` for this call. `{"site", "retentionDays", "purged": [<path>], "failed": [<path>]}` |

The operations answer managers too, not server administrators alone:

| Endpoint | Answer |
|---|---|
| `POST /modules/formidable-quarantine/operations?path=<held>&operation=<retry\|release\|resume\|reject>` | `{"path", "operation", "from", "state"}`: the state before and after the operation. 400 for a path that does not have the shape of a held submission, or an unknown operation; 403 for a user who does not manage the form (and for a path that names nothing, but to a server administrator, who gets 404); 409 when the state or the decision does not allow the operation |

## Configuration

`org.jahia.test.modules.formidable.quarantine.run.cfg`, deployed once with the module, then kept as edited:

| Key | Default | Meaning |
|---|---|---|
| `allowInProcessFirst` | `false` | Lifts the refusal of a list where an in-process check reads a file before a remote check passed it |
| `backoffInitialSeconds` | `60` | The delay after the first attempt; it doubles at each attempt |
| `backoffCapSeconds` | `3600` | The longest delay |
| `maxAttempts` | `10` | The count after which the submission goes to `needsReview`: the retryable failures of a check on a file until it answers a result, or the passes in a row that bring no new `PASS` |

`org.jahia.test.modules.formidable.quarantine.retention.cfg`, deployed the same way:

| Key | Default | Meaning |
|---|---|---|
| `retentionDays` | `30` | The number of days a rejected held submission is kept after its last change. A negative value falls back to 30 |

A setting the back-off cannot use, such as a cap below the initial delay, falls back to the defaults with a warning. A spec that needs few attempts edits `maxAttempts` through the provisioning API (`editConfiguration`), then waits until `GET /modules/formidable-quarantine/run` answers the new value, and sets the default back when it ends. No request parameter changes a limit: the run reads its limits from the configuration only.

## Node types

The module declares its types in its own namespace, `fmdbquar` (`http://www.jahia.org/jahia/fmdbquar/nt/1.0`).

Each check type takes the marker of the engine, `fmdbmix:qualifier`, as a supertype. The "Checks" list accepts no other kind of node.

## Java packages

The base package is `org.jahia.test.modules.formidable.quarantine`. The module compiles against the `api` package of the engine only.

| Package | Content |
|---|---|
| `hold` | The reference hold and its rule of ownership |
| `store` | The quarantine tree: `QuarantineStore` and its JCR implementation |
| `check` | The SPI of a check: `Qualifier`, its settings and its outcomes |
| `chain` | The chain of checks, a pure class |
| `state` | The state machine and the back-off, pure classes |
| `stub` | The two stub checks and the control of their outage |
| `run` | The run of the checks and the release |
| `ops` | The operations of a manager, the permission, the role, and the retention |
| `web` | The endpoints: the inspection, "run now", the control of the stubs, the operations and the retention |

## Build and test

```
mvn test -pl jahia-test-module/formidable-test-module-quarantine -am
```

The spec `tests/cypress/e2e/quarantine/97-quarantine-intake.cy.ts` covers the intake, `tests/cypress/e2e/quarantine/98-quarantine-checks.cy.ts` the checks and the release, and `tests/cypress/e2e/quarantine/99-quarantine-operations.cy.ts` the operations, the audit and the retention. `tests/scenarios/quarantine.md` states their scenarios.
