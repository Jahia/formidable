# formidable-test-module-quarantine

The stub checks of the tests of `formidable-quarantine`, and the endpoints the specs drive them with. `formidable-quarantine` holds the uploads of a form with the switch `fmdbmix:quarantine` on ([ADR 0003](../../docs/adr/0003-jahia-ships-the-quarantine-hold-as-an-optional-module.md), [the design](../../docs/architecture/quarantine.md)). This module is a client of its check API: it keeps no hold and writes no quarantine tree.

**Never deploy this module in production.** Its checks are stubs: they read their outcome from a marker in the uploaded file. They detect nothing.

## What moved to `formidable-quarantine`

- The hold, the quarantine tree and its types (`fmdbquar:quarantineRoot` … `fmdbquar:auditRecord` became `fmdbq:*`), the chain, the state machine, the run, the release, the operations, the audit and the retention, with their unit tests.
- The check API (`check` became `org.jahia.modules.formidable.quarantine.api`), and `QuarantineRunner`, which starts a run.
- The inspection, operations and retention endpoints, under the same paths.
- The configurations `org.jahia.modules.formidable.quarantine.run` and `.retention` (formerly `org.jahia.test.modules.formidable.quarantine.*`).
- The permission `formidableQuarantineManage` and the role `formidable-quarantine-manager` (formerly `fmdbquarManageQuarantine` and `fmdbquar-quarantine-manager`).

## What stays here

- The two stub checks, `fmdbquar:remoteStubCheck` and `fmdbquar:inProcessStubCheck`, as `Qualifier` services, and the control of their outage.
- "Run now" (`/modules/formidable-quarantine/run`), which starts a run through the `QuarantineRunner` service, and the control of the stubs (`/modules/formidable-quarantine/stub`).

## Which forms the hold owns

This module depends on `formidable-quarantine`, so a site that enables this module has it. The ownership rule of the two holds is in [Ownership of a form](../../docs/architecture/quarantine.md#ownership-of-a-form).

## Reference of the moved hold

The sections below describe the hold as it moved, until the documentation of `formidable-quarantine` replaces them.

## The quarantine tree

The store writes the tree in live, with a system session:

```text
/sites/<site>/formidable-quarantine              fmdbq:quarantineRoot   ACL inheritance cut, no ACE
  └─ <formId>                                    fmdbq:formQuarantine
       └─ YYYY/MM/DD                             fmdbq:dateFolder (UTC date of intake)
            └─ held-<uuid>                       fmdbq:heldSubmission
                 - state, createdAt, changedAt, attempts, nextAttempt, completedActions
                 - formId, siteKey, locale, originLanguage, originReferer, originTimeZone
                 - valueFields, sensitiveFields   the field names, in the order of the submission
                 ├─ values                       fmdbq:values           the public values
                 ├─ sensitive                    fmdbq:sensitiveValues  the sensitive values
                 ├─ item0, item1, ...            fmdbq:item             one per upload
                 │    - data (binary, indexed=no), sha256, size, fileName, fieldName, mimeType
                 ├─ checks                       fmdbq:records          added by the run
                 │    ├─ item0                   fmdbq:itemOutcomes
                 │    │    └─ <check>            fmdbq:outcome          outcome, reason, retryable, at, ticket, attempts, sha256
                 │    └─ decision                fmdbq:decision         reason, itemName, checkName, sha256, failedAction, at
                 └─ audit                        fmdbq:records          added by the operations and the run
                      └─ record1, record2, ...   fmdbq:auditRecord      operation, userName, fromState, toState, at
```

- The store creates the root on first use and cuts its ACL inheritance. It uses a node that already exists at `/sites/<site>/formidable-quarantine` only when the node is a `fmdbq:quarantineRoot`, its `j:acl` has `j:inherit=false`, and its `j:acl` holds no access control entry. It uses a form folder or a date folder that already exists only when the folder is of the type of the store and has no `j:acl` of its own. Any other node refuses the hold: the message of the refusal names its path, the engine logs it, and the visitor gets FMDB-018. A node that someone else created there could give its readers the held values and bytes.
- `changedAt` records the last change of the state; the retention reads it.
- No node of the tree is a file. An item holds its bytes in its own binary property, never in `jcr:data` on a `jnt:resource`. No core listener, no `/files` servlet and no text extraction reads it.
- The SHA-256 of a file is computed once at intake, from the bytes read once from `SubmittedFile.data()`.
- The sensitive values live in the node `sensitive`, apart from the public values in the node `values`. No endpoint reads them, and no log line names them. `finish` writes the final state, `completed` or `rejected`, with its `changedAt`, and removes both nodes and the bytes of every item, in one save. A failure writes neither, so a final state never keeps its data: a release stays `releasing` and a rejection by the checks stays `checking`. The record stays: the state, the field names, and of each item its name, size and SHA-256.
- An outcome records the SHA-256 of the bytes it was given for, never a byte. The run reads an outcome only for the bytes of that SHA-256.
- `checks` and `audit` accept any `jnt:content`: the outcomes, the decision and the audit records (`fmdbq:auditRecord`) live there, and a new record type needs no change to the types above, so a lab takes it without a refused CND change.

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

`QuarantineRun` makes one pass over the due held submissions of a site, or of one form. The scheduled run of the module (`RunSchedule`) starts it every `periodSeconds` on the processing server; the test lab turns it off (`periodSeconds=0`), so a spec starts it through "run now". A pass holds the lock of its site in the JVM and the lock of the run of the site in the repository ([The lock of the run](../../docs/architecture/quarantine.md#the-lock-of-the-run)), and each state move is a compare-and-set: a submission that another run or an operation moved first is skipped, with the error `moved by another run or operation`. A submission is due in state `held`, or in state `checking` when it has no next attempt or its next attempt is reached.

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

A manager of the quarantine of a form holds the permission `formidableQuarantineManage` on the form. `formidable-quarantine` declares the permission in `formidable-quarantine/src/main/import/permissions.xml`, and creates the role `formidable-quarantine-manager`, which grants it, when it starts (`ManagerRoleInitializer`). The engine creates its role `fmdb-results-reader` the same way. An administrator grants the role to a user or a group on the form, then publishes the form. The operations check the permission on the live form, in a session of the user of the request, so the ACL of the form decides.

A server administrator (`root`, or a member of the server group `administrators`) is a manager of every form. No other role gives the permission: a results reader, an editor or a site administrator of the site is not a manager.

We chose the simplest model that a Jahia module ships and that a spec grants with `grantRoles`. A permission of the module, and not the role alone, lets the check use `hasPermission` on the form.

## The operations

An operation runs on a held submission in state `needsReview`, and a resume or a reject also on a release that stopped half way in `releasing`. It checks, in this order, that a held submission is at the path, that the user is a manager of its form, and that the state and the decision allow the operation. A refused operation changes nothing. An operation takes the lock of the site in the JVM, which a pass of the run holds for the whole pass: it waits 2 seconds at most, then answers 409, "a run is in progress". A release and a resume also take the lock of the run of the site in the repository, and a reject of a submission in `releasing` needs that no run holds it, so a submission in `releasing` is a release that stopped, not one under way, on any server. An operation on a submission that a run moved first answers 409 too.

| Operation | Allowed when | What it does |
|---|---|---|
| `retry` | `needsReview` | Sets the attempts to 0, removes every outcome that is not a `PASS` (with its count of failures), and moves to `checking`. The next run calls those checks again |
| `release` | `needsReview`, and the decision is neither `ACTION_FAILED` nor `FORM_GONE` | Moves to `releasing`, then runs the release: the SHA-256 is computed again, then the actions after release run. The state is `completed`, or `needsReview` again |
| `resume` | `needsReview`, and the decision is `ACTION_FAILED`; or `releasing` | The same, from the index `completedActions`: the actions that ran do not run again |
| `reject` | `needsReview` or `releasing` | Moves to `rejected`, and deletes the values, the sensitive values and the bytes, in one save. The outcomes and the decision stay. If the save fails, the state and the data stay, and the manager rejects again |

A release after a failed action is refused: a failed action takes a resume. The release starts at `completedActions` in any case, so this is a rule of the module, not of the release.

A submission whose form is gone (`FORM_GONE`) cannot be released: its actions after release cannot run. The permission of a manager is checked on the form, so only a server administrator manages such a submission, and a reject ends it. Until then it keeps its data, because the retention purges `completed` and `rejected` submissions only.

An operation that ran answers its result even when its audit record cannot be written: the failure is logged. A log line of a failed operation names the class of an unexpected failure, never its message, which can quote a held value.

## The audit

Each operation that ran writes one audit record under `audit`: the operation, the name of the user, the state before and after, and the time. The run also writes one when it releases, rejects or sends a submission to review, with the operation `run` and no user. An operation or a run whose release stops half way writes one too, with the state reached, `releasing`, so the audit names who started the release that a manager then resumes or rejects. A submission that keeps checking gets none, so a run that waits for a back-off adds nothing. No audit record holds a value, a file name or a byte. A failure to write the audit record of the run is logged, and the run goes on: the state already moved.

## The retention

The retention purges each `completed` or `rejected` held submission of a site whose last change (`changedAt`) is older than the limit of the site: the whole node, with its items, its values, the outcomes, the decision and the audit. It never purges a submission in `held`, `checking`, `releasing` or `needsReview`. The limit is `retentionDays` of the platform, or `retentionDays` of the site settings (`fmdbqmix:quarantineSite`) when it is lower. The scheduled purge of the module (`PurgeSchedule`) runs it every `periodSeconds` of the retention on the processing server, and a spec starts it through its endpoint.

The repository frees the bytes of a purged item only when the garbage collection of its datastore runs.

## The endpoints for the specs

Each endpoint answers server administrators alone, as the inspection does: any other caller gets 403, and nothing runs or changes. A `POST` carries `X-Requested-With`: without it, every `POST` below answers 403, and nothing runs or changes. The retention and the operations of `formidable-quarantine` also answer 415 to a `POST` whose `Content-Type` is not `application/json`, and nothing runs or changes. No answer holds a value or a byte, and an unexpected failure of a submission is answered by the name of its class alone.

| Endpoint | Answer |
|---|---|
| `POST /modules/formidable-quarantine/run?site=<siteKey>[&form=<formId>][&ignoreSchedule=true]` | One pass of the run over the due submissions of the site, or of the form. `ignoreSchedule=true` also takes a `checking` submission whose next attempt is ahead. `{"site", "form", "settings", "processed": [{"path", "form", "from", "state", "error"}]}`: the state of each submission after the run, and why the run of a submission failed, or `null`. 409 when another run holds the lock of the site: nothing ran |
| `GET /modules/formidable-quarantine/run` | `{"settings": {"allowInProcessFirst", "maxAttempts", "backoffInitialSeconds", "backoffCapSeconds"}, "schedule": {"periodSeconds"}}`: the settings the next run applies, and the period of the scheduled run as the scheduler of this server holds it, 0 when it is off |
| `GET /modules/formidable-quarantine/stub/calls[?check=<name>]` | `{"outage", "calls": [{"nodeType", "checkName", "sha256", "kind"}]}`: the calls of the stubs, oldest first, all or those of one check node; `kind` is `SUBMIT` or `POLL` |
| `POST /modules/formidable-quarantine/stub/calls/clear` | Forgets the calls |
| `POST /modules/formidable-quarantine/stub/outage/lift`, `.../outage/set` | Lifts or sets the outage of the stubs. It is on when the module starts |
| `POST /modules/formidable-quarantine/retention?site=<siteKey>[&olderThanDays=<n>]` | Purges the completed and rejected held submissions of the site older than the limit. `olderThanDays` replaces the limit of the site for this call. `{"site", "retentionDays", "limit", "purged": [<path>], "failed": [<path>]}`, where `limit` is `platform`, `site` or `call` |

The operations answer managers too, not server administrators alone:

| Endpoint | Answer |
|---|---|
| `POST /modules/formidable-quarantine/operations?path=<held>&operation=<retry\|release\|resume\|reject>` | `{"path", "operation", "from", "state"}`: the state before and after the operation. 400 for a path that does not have the shape of a held submission, or an unknown operation; 403 for a user who does not manage the form (and for a path that names nothing, but to a server administrator, who gets 404); 409 when the state or the decision does not allow the operation; 415 when the `Content-Type` is not `application/json` |

## Configuration

`org.jahia.modules.formidable.quarantine.run.cfg`, deployed once with the module, then kept as edited:

| Key | Default | Meaning |
|---|---|---|
| `allowInProcessFirst` | `false` | Lifts the refusal of a list where an in-process check reads a file before a remote check passed it |
| `backoffInitialSeconds` | `60` | The delay after the first attempt; it grows by `backoffFactor` at each attempt |
| `backoffFactor` | `2` | What the delay is multiplied by at each attempt; at least 1 |
| `backoffCapSeconds` | `3600` | The longest delay |
| `maxAttempts` | `10` | The count after which the submission goes to `needsReview`: the retryable failures of a check on a file until it answers a result, or the passes in a row that bring no new `PASS` |
| `periodSeconds` | `60` | The period of the scheduled run on the processing server; `0` turns it off. The test lab sets `0` |
| `lockLeaseSeconds` | `600` | How long the lock of the run of a site stays held after a server stopped without releasing it |

`org.jahia.modules.formidable.quarantine.retention.cfg`, deployed the same way:

| Key | Default | Meaning |
|---|---|---|
| `retentionDays` | `30` | The number of days a completed or rejected held submission is kept after its last change. A site lowers it with `retentionDays` of its settings. A negative value falls back to 30 |
| `periodSeconds` | `86400` | The period of the scheduled purge on the processing server; `0` turns it off |

A setting the back-off cannot use, such as a cap below the initial delay, falls back to the defaults with a warning. A spec that needs few attempts edits `maxAttempts` through the provisioning API (`editConfiguration`), then waits until `GET /modules/formidable-quarantine/run` answers the new value, and sets the default back when it ends. No request parameter changes a limit: the run reads its limits from the configuration only.

## Node types

The module declares its stub checks in its own namespace, `fmdbquar` (`http://www.jahia.org/jahia/fmdbquar/nt/1.0`). Each takes the marker of the engine, `fmdbmix:qualifier`, as a supertype. The "Checks" list accepts no other kind of node.

## Java packages

The base package is `org.jahia.test.modules.formidable.quarantine`. The module compiles against the exported `api` packages of the engine and of `formidable-quarantine` only.

| Package | Content |
|---|---|
| `stub` | The two stub checks and the control of their outage |
| `web` | "Run now" and the control of the stubs |

## Build and test

```
mvn test -pl formidable-quarantine,jahia-test-module/formidable-test-module-quarantine -am
```

The spec `tests/cypress/e2e/quarantine/97-quarantine-intake.cy.ts` covers the intake, `tests/cypress/e2e/quarantine/98-quarantine-checks.cy.ts` the checks and the release, and `tests/cypress/e2e/quarantine/99-quarantine-operations.cy.ts` the operations, the audit and the retention. `tests/scenarios/quarantine.md` states their scenarios.
