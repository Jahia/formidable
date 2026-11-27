# Quarantine — Test scenarios

Regression scenarios for the quarantine of uploads: a form with the quarantine
switch on hands its files to no action on submission, and its actions after
release receive the released files.

Decision: [ADR 0002](../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

This document holds the scenarios that a spec covers. Two scenarios, Q17 and
Q19, wait for a spec of the module `formidable-quarantine`:
[`todo/quarantine.md`](./todo/quarantine.md) says why. A scenario keeps its
identifier wherever it moves, so the identifiers of this document are not in
order.

Specs:

- `tests/cypress/e2e/quarantine/91-quarantine-scheduled-run.cy.ts` covers Q49:
  the scheduled run.
- `tests/cypress/e2e/quarantine/92-quarantine-release-values.cy.ts` covers Q40,
  Q41, Q42, Q43 and Q44: the policy of a form, which releases the values alone
  on a fail, and its switch in the editor.
- `tests/cypress/e2e/quarantine/93-quarantine-allow-list.cy.ts` covers Q29, Q30
  and Q31: the allow-list check of the module.
- `tests/cypress/e2e/quarantine/94-quarantine-ownership.cy.ts` covers Q27 and
  Q28: which hold owns a form.
- `tests/cypress/e2e/quarantine/95-uploads-hold.cy.ts` covers Q07, Q08, Q14,
  Q15, Q25 and Q26.
- `tests/cypress/e2e/quarantine/96-uploads-hold-email.cy.ts` covers Q06, and
  the mail of Q14 and of Q15.
- `tests/cypress/e2e/quarantine/97-quarantine-intake.cy.ts` covers Q01, Q10,
  Q11 and Q16, with the module hold.
- `tests/cypress/e2e/quarantine/98-quarantine-checks.cy.ts` covers Q02, Q03,
  Q04, Q09, Q12, Q13, Q21, Q22 and Q23: the run of the checks and the release.
- `tests/cypress/e2e/quarantine/99-quarantine-operations.cy.ts` covers Q05,
  Q18, Q20 and Q24: the operations of a manager, the audit and the retention.

## Vocabulary

- **Switch**: the option "Hold uploads in quarantine" of a form. A form with
  the switch on has three lists.
- **Actions on submission**: the list `actions`. It runs in the request, and
  receives the values and no file.
- **Actions after release**: the list `deferredActions`. It receives the values
  and the released files.
- **Hold**: the service that receives a submission with its uploads. The
  samples module ships a sample hold for the tests, which releases the
  submission inside the request, before the response. The sample hold records
  the number of files of each call.
- **Observer action**: `fmdbsample:uploadObserverAction` of the samples
  module. It records the number of files of each call, with the name of its
  node and of its list. Its type is deferrable.
- **Forward sink**: a receiver of the samples module, which the spec declares
  as the forward target `samplesink`. It answers the names of the parts that
  the last forward carried. A server administrator makes it refuse each
  forward with 503 (`POST .../refuse`, with `X-Requested-With`), until
  `POST .../accept` or `POST .../reset`. Any caller may post the reset, as
  in a test module. A refused forward is not counted as received.
- **Quarantine module**: `formidable-quarantine`. It ships the module hold,
  the quarantine tree, the run of the checks, the operations of a manager and
  an inspection of the tree. The module also ships one check, the allow-list
  check `fmdbq:allowListCheck`.
- **Quarantine test module**: `formidable-test-module-quarantine`. It ships the
  stub checks, the stub control and "run now". It keeps no hold. It depends on
  the quarantine module, so a site that enables it has the quarantine module
  too. Never for production: its checks are stubs.
- **Check of the module**: a node in the list "Checks" (`qualifiers`) of a
  form. The node is a stub check, `fmdbquar:remoteStubCheck` or
  `fmdbquar:inProcessStubCheck`, or the allow-list check
  `fmdbq:allowListCheck`.
- **Module hold**: the hold of the quarantine module. It ranks 100. It owns
  every held form of a site that has the quarantine module, whatever the list
  "Checks" of the form holds. It stores the submissions of that form in the
  quarantine tree. It hands a form of any other site to the next hold below its
  ranking. When no hold ranks below it, it refuses the submission with FMDB-018.
- **Sample hold**: the hold of the samples module. It ranks 200. It claims
  only a form whose list "Checks" holds `fmdbsample:sampleCheck`, and hands
  every other form down to the module hold. The fixtures put a sample check in
  the list of a held form that names no list.
- **Quarantine tree**:
  `/sites/<site>/formidable-quarantine/<form>/YYYY/MM/DD/<held>`, in live. The
  root cuts its ACL inheritance and holds no access control entry. The store
  refuses any other node at the path of the root.
- **Held submission**: a node of the quarantine tree, with its state, the names
  of its fields, its values and, apart, its sensitive values.
- **Item**: one upload of a held submission. Its bytes are a binary property
  of the item, not indexed, never `jcr:data` on a `jnt:resource`. It records
  the SHA-256 of the bytes.
- **Inspection**: `/modules/formidable-quarantine/inspection?site=<site>&form=<formId>`.
  It answers server administrators alone, as JSON: the state of each held
  submission, the names of its fields, and of each item its file name, size,
  SHA-256 and whether its bytes are there. It never answers a value or a byte.
- **Stub check**: a check of the module. It applies to text files alone, and
  reads its answer from a marker line of the file: `VERDICT:PASS`,
  `VERDICT:FAIL`, `INCONCLUSIVE`, `PENDING:<n>` (pending for `n` polls, then
  `PASS`) or `DOWN` (a failure that can be retried while the outage is on, then
  `PASS`). A file with no marker is inconclusive. The remote stub declares that
  an external service reads the bytes; the in-process stub declares that it
  reads them inside Jahia.
- **Stub control**: `/modules/formidable-quarantine/stub`. It answers the calls
  that the stubs received, with the name of the check node, and lifts or sets
  the outage of the stubs. The outage is on when the module starts. Server
  administrators only.
- **Run now**: `POST /modules/formidable-quarantine/run?site=<site>&form=<formId>`.
  One pass of the checks over the due held submissions of the form. It answers
  the state of each submission after the run. `ignoreSchedule=true` also takes
  a submission whose next attempt is ahead, so a spec never waits for time.
  Server administrators only.
- **Outcome**: the last answer of one check for one item, recorded on the held
  submission with the SHA-256 it was given for: `PASS`, `FAIL`,
  `INCONCLUSIVE`, `PENDING` or `FAILED`.
- **Decision**: why the run stopped a held submission, such as `CHECK_FAILED`,
  `FILE_WITHHELD`, `IN_PROCESS_FIRST`, `INTEGRITY_FAILED` or `ACTION_FAILED`.
  The inspection answers the outcomes and the decision.
- **Manager**: a user that holds the role `formidable-quarantine-manager` on
  the form, published. The role grants the permission
  `formidableQuarantineManage` of the quarantine module. A server administrator is a manager of every
  form.
- **Operation**: `POST /modules/formidable-quarantine/operations?path=<held>&operation=<op>`,
  where the operation is `retry`, `release`, `resume` or `reject`. It runs on
  a held submission in state `needsReview`, for a manager. Any other user gets
  403, and a state that does not allow the operation gets 409. `resume` needs
  the decision `ACTION_FAILED`, and `release` any other decision. A release
  that stopped half way in `releasing` takes `resume` or `reject` only.
- **Audit record**: a record under the held submission: the operation, the name
  of the user, the state before and after, and the time. The run writes one,
  with no user, when it releases, rejects or sends to review. The inspection
  answers them.
- **Retention**: `POST /modules/formidable-quarantine/retention?site=<site>`.
  It purges each `completed` or `rejected` held submission whose last change is
  older than the limit: `retentionDays` (30 by default), or the lower
  `retentionDays` of the site settings, or `olderThanDays` for that call.
  Server administrators only. The module also runs it on its own schedule.
- **Mailbox**: Mailpit, the `smtp-server` service of `tests/docker-compose.yml`.
  The provisioning of `@jahia/cypress` points the mail service of Jahia at it.

## Common setup

1. Create a site with the Formidable modules and the samples module deployed.
2. Set `enableDevForwardTargets=true`: the forward sink is plain HTTP on the
   instance.
3. Create and publish a form with one text field and one file field.
4. Turn the switch of the form on, unless the scenario says otherwise.
5. Submit the form as a direct multipart POST, with a value and one text file
   unless the scenario says otherwise.

A spec reads the calls of the observer action and of the sample hold from the
samples module, at `/modules/formidable-samples/upload-observations`, and
resets them before each test.

The specs 97, 98 and 99 also enable the quarantine test module on
the site, and put a remote stub check in the list "Checks" of the form. They
read the quarantine tree as root, through the inspection, and through Groovy
with a system session.

## Control runs

A security scenario proves nothing if it also passes without the change. Each
scenario marked **control** runs once against a control build, and must fail
there by assertion, never by an error of its setup.

The control build keeps the API, the node types and the test modules of the
change, so the setup of the scenario still runs. It removes the one guard that
the scenario proves:

| Scenario | The control build |
|---|---|
| Q06, Q07, Q08 | hands the uploads to the actions on submission, whatever the switch says |
| Q15 | ignores `uploadsRequireQuarantine` |
| Q10 | leaves the ACL inheritance of the quarantine root in place: `formidable-quarantine` without the line `root.setAclInheritanceBreak(true);` of `JcrQuarantineStore.rootOf`, and with `root == null` as the condition of `JcrQuarantineStore.closed` |
| Q09 | releases without computing the hash again: `Release.release` with `firstChanged(held, submission)` replaced by `null` |
| Q21 | does not reject on a `FAIL`: `CheckChain.judgeResult` answers `null` for `FAIL` as for `PASS`. Q21 fails on the state after the run, `checking` and not `rejected` |
| Q23 | runs a list whose first check is in-process: `CheckChain.refuseInProcessFirst` returns `null` at once, with its first test changed to `if (allowInProcessFirst \|\| files != null)`. The setting `allowInProcessFirst=true` is not a control: spec 98 checks that it is `false` before each test |
| Q20 | runs an operation for any user: in `QuarantineOperations.applyLocked`, `!managers.mayManage(held.formId())` replaced by `false` |
| Q49 | schedules no run: `RunSchedule.configure` with its body replaced by `job.unschedule();`. Q49 fails on the bound of its wait, the submission still `held` |

---

## The hold and the actions after release

The scenarios of this section use the sample hold, which releases a submission
inside the request: when the response arrives, the actions after release have
run, and a spec reads their effects with no wait.

### Q06 — An e-mail action on submission sends no file — control

**Steps:**
1. Add an e-mail content action that attaches the files (`attachFiles` set,
   which is not its default) to "Actions on submission",
   and a second one, with the same setting and another recipient, to "Actions
   after release".
2. Submit the form with a file.

**Expected:**
- HTTP 200
- The mail of the action on submission carries the field values and no
  attachment
- The mail of the action after release carries the attachment

### Q07 — A forward action on submission sends no file — control

**Steps:**
1. Declare a forward target that points at the forward sink of the samples
   module, and add a forward action on that target to "Actions on
   submission".
2. Submit the form with a file.

**Expected:**
- HTTP 200
- The target receives the values and no file part

### Q08 — A third-party action on submission receives no file — control

**Steps:**
1. Add the observer action of the samples module to "Actions on submission".
2. Submit the form with a file.

**Expected:**
- The action reports zero files
- The sample hold reports one file

The spec also adds the observer action to "Actions after release", and reads
that this second call reports one file.

### Q14 — A form with the switch off is unchanged

**Steps:**
1. Use a form with the switch off, Save to JCR and an e-mail content action
   that attaches the files (`attachFiles` set).
2. Submit the form with a file.

**Expected:**
- The file is stored under the submission, as before
- The mail carries the attachment, as before
- The sample hold reports no call

### Q15 — The quarantine is mandatory, and the switch is off — control

`uploadsRequireQuarantine` is an operator setting of the engine, in
`org.jahia.modules.formidable.uploads.cfg`. It applies to every form of the
platform, and it is `false` by default: Q14 runs with the default.

**Steps:**
1. Set `uploadsRequireQuarantine=true`.
2. Use the form of Q14: the switch off, Save to JCR and an e-mail content
   action that attaches the files.
3. Submit the form with a file.

**Expected:**
- HTTP 200
- The submission is stored with its values and no file
- The mail carries no attachment

### Q25 — The switch is on, and the submission has no file

**Steps:**
1. Add Save to JCR and the observer action to "Actions after release".
2. Submit the form with a value and no file.

**Expected:**
- HTTP 200
- When the response arrives, the submission is stored and the observer action
  has reported its call
- The sample hold reports no call

### Q26 — A type that is not deferrable stays out of the actions after release

**Steps:**
1. Open the form in the editor, and list the types that "Actions after
   release" offers.
2. Create a redirect action under `deferredActions` through a GraphQL
   mutation.

**Expected:**
- The list of step 1 holds Save to JCR, the two e-mail actions and the forward
  action, and no redirect action
- The mutation of step 2 is refused, and the list holds no redirect action

For step 1, the spec asks the repository which node types the list accepts
under it, and does not open the editor. For step 2, the spec first creates a
Save to JCR action with the same mutation, so the refusal is the one of the
type.

---

## The intake of the module hold

The scenarios of this section use the module hold. The form has a remote stub check in "Checks", unless the scenario says
otherwise. No scenario of this section releases a submission: the next section
covers the checks and the release.

### Q01 — A submission with a file is held

**Steps:**
1. Add Save to JCR and the observer action to "Actions after release".
2. Submit the form with a file.

**Expected:**
- HTTP 200 to the visitor, with the body of any accepted submission
- One held submission, with state `held`, the field names and the field values
- One item, with the file name, the size and the SHA-256 of the upload, and its
  bytes
- The sample hold reports no call, and the observer action no call
- No submission is stored in the results of the form

The observer action is deferrable, as an e-mail action is. Its call count proves
that no action after release ran, with no mailbox.

The spec also submits a file to a form with the switch on and a sample check in
its list "Checks". The sample hold claims that form and reports one call with one
file, and the quarantine tree holds nothing for that form.

### Q10 — No reader role reads the quarantine tree — control

The quarantine root cuts the ACL inheritance and holds no access control entry,
so a role granted on the site, the site administrator included, gives nothing
on it. A system session, and a member of the server `administrators` group such
as `root`, are outside this scenario: the Jahia core grants them every
permission, whatever the ACL says.

The store creates the root and cuts its inheritance. It uses a node that
already exists at that path only when the node is a `fmdbq:quarantineRoot`
that cuts its inheritance and holds no access control entry. It uses a form
folder or a date folder that already exists only when the folder is of the type
of the store and has no ACL of its own. Any other node refuses the hold, and
the visitor gets FMDB-018. The unit tests of `JcrQuarantineStore` prove that
refusal; this scenario does not. A spec would need a second site, because the
site of the suite already holds the root, and the refusal reaches the visitor
through the same error path as any other failure of the store.

`docs/administration/results-permissions.md` says that site administrators read
the results of a form, whose entry cuts the inheritance too. The core, as read
in its access manager and not run, gives a site administrator no such bypass.
This scenario is the check of that case on a live Jahia: if the site
administrator reads the item, the quarantine root needs another guard.

**Steps:**
1. Create three users: a results reader (editor of the site, and
   `fmdb-results-reader` on the form, published), an editor of the site, and a
   site administrator.
2. Submit the form with a file.
3. As root, read the held submission and its item through GraphQL.
4. As each of the three users, read the quarantine root, the held submission,
   its values and its item through GraphQL; the item through `/files/live/...`;
   the held submission and its item through the JCR REST API; and the
   inspection.
5. Read the ACL of the quarantine root with a system session (Groovy).

**Expected:**
- Root reads the held submission and its item: the paths are real
- Each GraphQL read of the three users answers no node
- Each `/files` and JCR REST read answers 401, 403 or 404
- The inspection answers 403
- The root cuts its ACL inheritance and holds no `jnt:ace`

The JCR REST API answers 404 when its module is not installed: that read then
proves nothing.

**Control build:** `formidable-quarantine` with two changes in
`JcrQuarantineStore`: the line `root.setAclInheritanceBreak(true);` removed
from `rootOf`, and the condition of `closed` replaced by `root == null`, so
that the store reuses the root it created. Build it with `package`, deploy it
in place of the module, and run spec 97. The spec creates the site again, so
the quarantine root is created by the control build. Q10 must fail by
assertion on a read of the editor or of the site administrator.

The first change alone proves nothing. The store then refuses every hold after
the first one with FMDB-018, because the root inherits its access: Q10 fails
in its `before` hook with a 503, before any read.

### Q11 — The quarantine tree is inert

**Steps:**
1. On a form with a multiple file field, submit a PDF and an image through the
   form page.
2. Submit the same PDF through the form page of a form with the switch off and
   Save to JCR. Poll its submission until a node under it carries
   `j:extractedText` (Groovy): the extraction of the core has then run. A
   platform that extracts no text fails this step, and the scenario proved
   nothing there.
3. Read the held submission through the inspection, and the quarantine tree of
   the first form with a system session (Groovy).

**Expected:**
- One held submission with two items, one for each upload, and each holds its
  bytes
- No `jnt:file`, `jnt:resource`, `nt:file` or `nt:resource` node
- No `j:extractedText` property
- No thumbnail node and no image node

### Q16 — Read-only maintenance

A form answers `FMDB-014` in read-only maintenance when it is presumed to
write. A form with the switch on is presumed to write, whatever its actions:
the hold stores a submission with a file, and the gate runs before the body
says whether a file came.

**Steps:**
1. Make two forms. The first has Save to JCR in "Actions after release". The
   second has only the observer action there, which is read-only compatible.
2. Put the platform in read-only maintenance.
3. Submit each form with a file, then with no file.

**Expected:**
- HTTP 503 and `FMDB-014` each time
- No held submission, no item and no stored submission is written
- The sample hold reports no call, and the observer action no call

---

## The checks and the release

The scenarios of this section use the module hold, and start the run of the
checks through "run now", restricted to the form of the scenario. The form has
one remote stub check in "Checks", and Save to JCR in "Actions after release",
unless the scenario says otherwise. Each scenario submits as a guest, with a
direct multipart POST, and reads the result as root.

The plan of the test module drops four things that the first version of these
scenarios read: the Results page, the alarm e-mail, the alert in edit mode, and
the marker of a released file.

### Q02 — Every check passes, and the file is released

**Steps:**
1. Submit the form with a `VERDICT:PASS` file.
2. Read the SHA-256 that the item records, through the inspection.
3. Run the checks.

**Expected:**
- The state is `completed`, and one action after release ran
- The outcome `PASS` of the check is recorded for the SHA-256 of step 2
- The stored submission holds one file, and the SHA-256 of its bytes is the one
  of step 2 (Groovy)
- The item keeps its name, size and SHA-256, and holds no bytes; the held
  submission holds no values

### Q03 — A check fails, and the submission is rejected

**Steps:**
1. Add Save to JCR and the observer action to "Actions after release".
2. Submit the form with a `VERDICT:FAIL` file.
3. Run the checks.

**Expected:**
- The state is `rejected`, and the decision is `CHECK_FAILED`, for the check
- The outcome `FAIL` and its reason are recorded on the held submission
- No submission is stored in the results of the form, and the observer action
  reports no call
- The held submission holds no values, and the item no bytes

### Q04 — A check that is down keeps the submission

**Steps:**
1. Submit the form with a `DOWN` file. The outage is on.
2. Run the checks.
3. Run the checks on schedule, then once more with `ignoreSchedule`.

The settings allow more attempts than the runs of this scenario. Q05 covers the
exhaustion of the attempts.

**Expected:**
- The submission was accepted with HTTP 200
- After step 2, the state is `checking`, with one attempt, a next attempt, and
  the outcome `FAILED` (`stub.down`, retryable)
- The run on schedule processes nothing: the next attempt is ahead
- After the last run, the state is still `checking`, with two attempts; the
  values and the bytes are kept, and the stub logs two calls

### Q09 — The bytes changed after intake — control

**Steps:**
1. Add Save to JCR and the observer action to "Actions after release".
2. Submit the form with a `VERDICT:PASS` file.
3. Change the binary of the item (Groovy). The new bytes still carry
   `VERDICT:PASS`, so every check passes them.
4. Run the checks.

**Expected:**
- The state is `needsReview`, and the decision is `INTEGRITY_FAILED`, for the
  item
- The outcome of the check is `PASS`: the release alone refused the bytes
- No submission is stored in the results of the form, the observer action
  reports no call, and no action after release ran

The decision names the item, and a log line names it too. The audit record of
the run names no item.

**Control build:** see the table of the control runs.

### Q12 — The visitor learns nothing of the outcome

**Steps:**
1. Submit the form twice with a `VERDICT:PASS` file, before any run. The keys
   of the response body whose values differ between these two responses are
   the only keys that the comparison below ignores.
2. Submit the form with a `VERDICT:FAIL` file.
3. Run the checks, so that two submissions are completed and one rejected.
4. Submit the `VERDICT:PASS` file and the `VERDICT:FAIL` file again.
5. As a guest, request the form page, the quarantine paths of the held
   submissions and the results path of the form, then the inspection, "run
   now" and the stub control.

**Expected:**
- The five responses to a submission have the same status, the same keys and,
  but for the keys of step 1, the same values
- The file already rejected is accepted again, with HTTP 200
- No response to the guest carries a state or an outcome, and each item path
  answers 401, 403 or 404
- The inspection, "run now" and the stub control answer 403

### Q13 — A file that no check applies to

**Steps:**
1. Submit a PNG image through the form page: the stub applies to text files
   alone.
2. Run the checks.

**Expected:**
- The state is `needsReview`, and the decision is `FILE_WITHHELD`
- The stub logs no call, and no outcome is recorded
- No submission is stored in the results of the form, and the item keeps its
  bytes

### Q21 — Two checks, and the first fails — control

**Steps:**
1. Put two remote stub checks in "Checks".
2. Submit the form with a `VERDICT:FAIL` file.
3. Run the checks.

**Expected:**
- The state is `rejected`, and the decision names the first check
- The stub logs one call for the first check, and none for the second

### Q22 — Two checks, and both pass

**Steps:**
1. Put two remote stub checks in "Checks", and two observer actions in "Actions
   after release".
2. Submit the form with a `VERDICT:PASS` file.
3. Run the checks, then run them again with `ignoreSchedule`.

**Expected:**
- The first run releases the submission: the stub logs one call for each
  check, in the order of the list
- The second run processes nothing
- Each observer action reports one call, with one file, in the order of the
  list
- The state is `completed`, with two actions after release that ran

### Q23 — An in-process check placed first — control

**Steps:**
1. Put the in-process stub check first in "Checks", and the remote stub check
   second.
2. Submit the form with a `VERDICT:PASS` file, and run the checks.

**Expected:**
- The state is `needsReview`, and the decision is `IN_PROCESS_FIRST`, for the
  in-process check
- Neither stub logs a call

The plan drops the alert of the editor on the list of checks.

---

## The operations and the retention

The scenarios of this section use the module hold and "run now", as the
section before. An operation runs as root unless the scenario names a user. The
form has one remote stub check in "Checks", and Save to JCR in "Actions after
release", unless the scenario says otherwise.

### Q05 — Attempts exhausted, then a manager retries

**Steps:**
1. Set `maxAttempts=2` in the configuration of the run.
2. Submit the form with a `DOWN` file. The outage is on.
3. Run the checks, then run them again with `ignoreSchedule`.
4. Lift the outage, and retry the submission through the operation.
5. Run the checks.

**Expected:**
- After step 3, the state is `needsReview`, the decision is
  `ATTEMPTS_EXHAUSTED`, and the outcome `FAILED` counts two failures
- After step 4, the state is `checking`, with no attempt and no outcome
- After step 5, the state is `completed`, the outcome is `PASS`, and one action
  after release ran
- The audit holds the run to `needsReview`, the retry by root, and the run to
  `completed`

The spec sets `maxAttempts=10` back when it ends.

### Q18 — Retention

**Steps:**
1. Submit the form with a `VERDICT:FAIL` file, then with another
   `VERDICT:FAIL` file, then with a `VERDICT:PASS` file, then with another
   `VERDICT:PASS` file, and run the checks.
2. Move the last change of the first rejected submission and of the first
   completed one 40 days back (Groovy).
3. Run the retention, with the default limit of 30 days.

**Expected:**
- After step 1, two submissions are `rejected` and two are `completed`
- The retention answers the paths of the first rejected submission and of the
  first completed one
- Those held submissions are gone, with their outcomes, their decision and
  their audit
- The second rejected submission and the second completed one are kept

The retention never purges a submission in `held`, `checking`, `releasing` or
`needsReview`, however old (unit tests of `QuarantineRetention`).

### Q20 — Only a manager runs an operation, and each leaves an audit record — control

**Steps:**
1. Create four users: a results reader (editor of the site, and
   `fmdb-results-reader` on the form), an editor of the site, a site
   administrator of the site, and a manager (`formidable-quarantine-manager` on
   the form, and no role on the site).
   Publish the form.
2. Submit the form three times with an `INCONCLUSIVE` file, and run the
   checks: each submission is `needsReview`.
3. As the reader, then as the editor, then as the site administrator, run
   each operation on each submission, and run the retention.
4. As the manager, release the first submission, reject the second, and retry
   the third. Then release the first again, and run the retention.

**Expected:**
- Each request of step 3 answers 403, and no state, value or audit record
  changes
- After step 4, the first submission is `completed`, the second `rejected`,
  with no value and no byte, and the third `checking`
- The second release answers 409, and the retention 403
- The last audit record of each submission names the operation and the
  manager, and no audit record holds a value

The plan dropped the Results page: no scenario opens it.

**Control build:** see the table of the control runs. Q20 must fail by
assertion on the status of an operation of the reader.

### Q24 — An action after release fails, and a manager resumes

**Steps:**
1. Declare the forward sink as a forward target. Put Save to JCR, then a
   forward action on the sink, in "Actions after release".
2. Make the sink refuse.
3. Submit the form with a `VERDICT:PASS` file, and run the checks.
4. Make the sink accept again. Release the submission, then resume it, through
   the operations.

**Expected:**
- After step 3, the state is `needsReview`, the decision is `ACTION_FAILED`
  with the index 1, one action ran, the submission is stored once, and the
  sink received no forward
- In step 4, the release answers 409: a failed action takes a resume
- After step 4, the state is `completed`, two actions ran, the submission is
  still stored once, and the sink received one forward, with the file

The first version of this scenario made a mail server refuse a mail. A Cortex
lab has no mailbox, so the forward sink refuses instead.

---

## The owner of a form

The scenarios of this section create each form with an empty list "Checks",
so that the sample hold does not claim the form. The site of the spec starts
without the quarantine module and without the quarantine test module.

### Q27 — A site without the quarantine module refuses a held form

**Steps:**
1. Create and publish a form with the switch on, an empty list "Checks", and
   the observer action in "Actions after release".
2. Submit the form as a guest, with a file.

**Expected:**
- The submission answers 503 with the code `FMDB-018`.
- The inspection answers no held submission for the form.
- The sample hold and the observer action report no call.

### Q28 — A form with no check is held, then sent to review

**Steps:**
1. Enable `formidable-quarantine` on the site.
2. Create and publish a form with the switch on, an empty list "Checks", and
   the observer action in "Actions after release".
3. Submit the form as a guest, with a file.
4. Start "run now" on the form, with `ignoreSchedule=true`.

**Expected:**
- The submission succeeds, and the inspection answers one held submission in
  state `held`.
- After the run, the state is `needsReview`, the decision is `NO_CHECK`, and
  the values are still there.
- The sample hold and the observer action report no call.

## The scheduled run

Spec `91-quarantine-scheduled-run.cy.ts`. The test lab turns the scheduled run off
(`periodSeconds=0` in `tests/provisioning-manifest-*.yml`), so the other specs
move a held submission with "run now" alone.

### Q49 — The scheduled run releases a held submission — control

**Steps:**
1. Enable the quarantine test module on the site.
2. Set `periodSeconds=2` in the configuration of the run.
3. Create and publish a form with the switch on, the remote stub check in
   "Checks", and Save to JCR in "Actions after release".
4. Submit the form as a guest, with a `VERDICT:PASS` file.
5. Wait, for 60 seconds at most, without "run now".
6. Set `periodSeconds=0` again.

**Expected:**
- The state becomes `completed` within the wait, and one action after release ran
- The outcome `PASS` of the check is recorded, and the audit holds one record
  of the run, with no user

**Control:** `RunSchedule.configure` schedules nothing. Q49 fails on the bound
of the wait, the submission still `held`.

---

## The policy of a form

The scenarios of this section use the module hold and start the run through
"run now", as the section on the checks does. The form carries the mixin
`fmdbqmix:quarantinePolicy` with the scan status `documentScanStatus`, the
clean value `clean` and the failed value
`Uploaded Document failed our security scan`. It has no field of that name. Its
"Actions after release" list holds Save to JCR and the observer action. Each
submission also posts a forged value named `documentScanStatus`.

### Q40 — A fail releases the values alone

**Steps:**
1. Set `onFail` to `releaseValues`.
2. Submit the form with a `VERDICT:FAIL` file and the forged value `clean`.
3. Run the checks.

**Expected:**
- The held submission names no field `documentScanStatus`: the engine skipped
  the forged value
- The state is `completed`, and the decision stays `CHECK_FAILED`
- The item holds no bytes, and the held submission holds no values
- The stored submission holds the name and the failed scan status, and no file
- The observer action reports one call with no file

### Q41 — A pass gets the clean scan status

**Steps:**
1. Set `onFail` to `releaseValues`.
2. Submit the form with a `VERDICT:PASS` file and the forged failed value.
3. Run the checks.

**Expected:**
- The state is `completed`, with no decision
- The stored submission holds the file and the scan status `clean`
- The observer action reports one call with one file

### Q42 — The default policy rejects a fail

**Steps:**
1. Set `onFail` to `reject`.
2. Submit the form with a `VERDICT:FAIL` file.
3. Run the checks.

**Expected:**
- The state is `rejected`, the decision is `CHECK_FAILED`, and no action ran
- The item holds no bytes, and the held submission holds no values
- The observer action reports no call

### Q43 — A reject by a manager releases the values alone

**Steps:**
1. Set `onFail` to `releaseValues`.
2. Submit the form with an `INCONCLUSIVE` file, and run the checks: the
   submission goes to review.
3. Reject it, as root.

**Expected:**
- The reject answers `completed`, and the decision keeps `INCONCLUSIVE`, the
  reason of the review
- The audit holds the run to `needsReview`, then the reject by `root` to
  `completed`
- The stored submission holds the name and the failed scan status, and no file
- The observer action reports one call with no file

### Q44 — The editor offers the policy

**Steps:**
1. Create a form with the quarantine switch on and no policy, and a form with
   the switch on and the policy.
2. Read the edit form of each through `forms.editForm`, as root.

**Expected:**
- In the section of the quarantine switch, the fieldset
  `fmdbqmix:quarantinePolicy` "Quarantine policy" is a switch: off on the first
  form, on on the second
- The section starts with the fieldset of the form, then the quarantine switch,
  then the policy

---

## The allow-list check

The scenarios of this section use the module hold. Each scenario starts the
run of the checks through "run now", restricted to the form of the scenario. The only check
of the form is the allow-list check `fmdbq:allowListCheck`, and "Actions after
release" holds Save to JCR. Each scenario submits a text file `claim-note.txt`,
of the type `text/plain`, as a guest, and reads the result as root.

### Q29 — The allow-list allows the type of the file

**Steps:**
1. Set the lists of the check to the extension `txt` and the type `text/plain`.
2. Submit the form, and run the checks.

**Expected:**
- The state is `completed`, and one action after release ran
- The outcome of the check is `PASS`, with the reason `allowList.allowed`

### Q30 — The extension of the file is not on the list

**Steps:**
1. Set the lists of the check to the extension `pdf` and the type `text/plain`.
2. Submit the form, and run the checks.

**Expected:**
- The state is `rejected`, and the decision is `CHECK_FAILED`, for the check
- The outcome of the check is `FAIL`, with the reason
  `allowList.extensionRefused`
- No action after release ran, and the item holds no bytes

### Q31 — The type of the file is not on the list

**Steps:**
1. Set the lists of the check to the extension `txt` and the type
   `application/pdf`.
2. Submit the form, and run the checks.

**Expected:**
- The state is `rejected`, and the decision is `CHECK_FAILED`
- The outcome of the check is `FAIL`, with the reason
  `allowList.mimeTypeRefused`

---

## Coverage map

| Scenario | Proves |
|---|---|
| Q01 | the intake always succeeds, and nothing runs after it before a release |
| Q02 | the bytes released are the bytes checked |
| Q03 | a file that failed a check never reaches an action after release |
| Q04, Q05 | an outage of a check loses no claim |
| Q06, Q07, Q08, Q15 | no file leaves through an action of the request |
| Q09 | a file whose bytes changed after intake is never released |
| Q10 | no reader opens a file before its release |
| Q11 | no parser of the platform reads a held file |
| Q12 | the form is no test bench for a malware author |
| Q13 | a file that no check applied to is not released by default |
| Q14 | no regression on a form with the switch off |
| Q16 | read-only maintenance writes nothing |
| Q18 | no finished submission, rejected or completed, is kept forever |
| Q20 | a release is the act of a named manager |
| Q21 | a later check never reads a file that an earlier check failed |
| Q22 | the actions after release run once, in order |
| Q23 | no in-process check parses a file that no remote check passed |
| Q24 | a failure after release stores nothing twice and forwards nothing twice |
| Q25 | a submission with no file waits for nothing |
| Q26 | no action that cannot run after release enters that list |
| Q27 | a site without the quarantine module never stores a held submission |
| Q28 | a form with no check is held and goes to review, never released unchecked |
| Q29 | the allow-list check releases a file of an allowed type |
| Q30, Q31 | the allow-list check rejects a file unless both its extension and its type are allowed |
| Q40, Q43 | a file that failed a check, or that a manager rejected, never leaves with the values |
| Q40, Q41 | the visitor cannot set the scan status |
| Q42 | the default policy still rejects a fail |
| Q44 | an author can set the policy of a form |
| Q49 | a held submission is checked on schedule, with no request of a user |
