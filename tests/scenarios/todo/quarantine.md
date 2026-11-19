# Quarantine — Test scenarios

Acceptance scenarios for the quarantine of uploads: a form with the quarantine
switch on holds its files until every check of the form passed them, then runs
its actions after release.

Decision: [ADR 0002](../../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

No scenario of this document is automated yet, except a part of Q16:
`tests/cypress/e2e/security/45-read-only-maintenance.cy.ts` covers its gate for a
submission with no file and for the form view. A scenario moves to
[`tests/scenarios/quarantine.md`](../quarantine.md) with the spec that covers
it, under `tests/cypress/e2e/quarantine/`. The scenarios of M1 moved there.

The scenarios of M1 are final. Those of a later milestone are the acceptance
list that its pull request starts from: that pull request completes their steps
against the design it delivers, and states each change it makes to them.

A scenario keeps its identifier, `Q01` to `Q26`, wherever it moves: a spec, a
pull request and the coverage map name it by that identifier. The sections
follow the milestones, so the identifiers are not in order.

## Milestones

Each milestone is one pull request, and delivers the specs of its scenarios.

| Milestone | Delivers | Scenarios |
|---|---|---|
| M1 | The engine: the switch, the two lists, the hold, the run of the actions after release, and a sample hold | Q06, Q07, Q08, Q14, Q15, Q25, Q26 |
| M2 | The quarantine module: the hold that stores a submission, and the intake | Q01, Q10, Q11, Q16 |
| M3 | The checks: the chain, the job, the release | Q02, Q03, Q04, Q05, Q09, Q12, Q13, Q21, Q22, Q23, Q24 |
| M4 | The Results page | Q19, Q20 |
| M5 | A check for a real sandbox | none: its client is tested against a recorded double, and once against the real service in a lab |
| M5b | A check of the validity of a PDF | none: unit tests |
| M6 | Retention and integrity | Q17, Q18 |

---

## Vocabulary

- **Switch**: the option "Hold uploads in quarantine" of a form. A form with
  the switch on has three lists.
- **Actions on submission**: the list `actions`. It runs in the request, and
  receives the values and no file.
- **Checks**: the list `qualifiers`. Each check receives one held file at a
  time, in the order of the list.
- **Actions after release**: the list `deferredActions`. It runs once every
  check passed, and receives the values and the released files.
- **Hold**: the service that receives a submission with its uploads. The
  samples module ships a sample hold for the tests (M1), which releases the
  submission inside the request, before the response. The quarantine module
  ships the hold that stores the submission (M2).
- **Held submission**: a submission in the quarantine tree, with its values.
- **Item**: one upload of a held submission, with its SHA-256.
- **State** of a held submission: `held`, `checking`, `releasing`,
  `completed`, `rejected` or `needsReview`.
- **Outcome** of a check on a file: `PASS`, `FAIL` or `INCONCLUSIVE`.
- **Withheld file**: a file that no check applied to. It is never released by
  default: its submission goes to `needsReview`.
- **Manager**: a user that holds `fmdb-quarantine-manager` on the form, and
  may retry, release, resume or reject a submission by hand. The role arrives
  with M4. Until M4, a spec acts through the administration mutations of M3,
  as a server administrator.

## Common setup

1. Create a site with the Formidable modules and the samples module deployed.
2. Create and publish a form with one text field and one file field.
3. Turn the switch of the form on, unless the scenario says otherwise.
4. Create a user that holds `fmdb-results-reader` on the form, an editor and a
   site administrator.

## The check double

No scenario calls a real engine. The samples module registers a stub check
(M3), which applies to text files alone. The stub reads its outcome from a
marker in the uploaded text file:

| Marker | The stub answers |
|---|---|
| `VERDICT:PASS` | `PASS` |
| `VERDICT:FAIL` | `FAIL` |
| `INCONCLUSIVE` | `INCONCLUSIVE` |
| `PENDING:<n>` | pending for `<n>` polls, then `PASS` |
| `DOWN` | a failure that can be retried, at every call, until a spec lifts the outage; then `PASS` |

A spec lifts the outage, and sets it again, through a request to the stub. The
bytes of an item never change for that: Q09 shows what a change of bytes does.
The stub logs each call it receives, with the name of its check node.

- The repository holds no malware and no EICAR file: the antivirus of a
  developer's machine deletes it. A scenario that needs the EICAR string builds
  it at run time.
- A spec never waits for the scheduler. It runs the job through the
  administration mutation "run now" (M3).

## Control runs

A security scenario proves nothing if it also passes without the change. Each
scenario marked **control** runs once against a control build, and must fail
there by assertion, never by an error of its setup.

The control build keeps the API, the node types and the test modules of the
change, so the setup of the scenario still runs. It removes the one guard that
the scenario proves:

| Scenario | The control build |
|---|---|
| Q10 | leaves the ACL inheritance of the quarantine root in place |
| Q09 | releases without computing the hash again |
| Q21 | runs every check, whatever the outcome of an earlier one |
| Q23 | runs a list whose first check is in-process |

---

## 1. The hold and the actions after release (M1)

The specs of M1 cover the scenarios Q06, Q07, Q08, Q14, Q15, Q25 and Q26. These
scenarios moved to [`../quarantine.md`](../quarantine.md).

---

## 2. Intake (M2)

### Q01 — A submission with a file is held

**Steps:**
1. Add Save to JCR and an e-mail content action to "Actions after release".
2. Submit the form with a file.

**Expected:**
- HTTP 200 to the visitor
- A held submission exists, with state `held` and the field values
- One item exists in the quarantine tree, with the SHA-256 of the upload
- No submission is stored in the results of the form, and no mail is sent

### Q10 — No reader role reads the quarantine tree — control

The quarantine root cuts the ACL inheritance and holds no access control entry,
so a role granted on the site, the site administrator included, gives nothing
on it. A system session, and a member of the server `administrators` group such
as `root`, are outside this scenario: the Jahia core grants them every
permission, whatever the ACL says.

`docs/administration/results-permissions.md` says that site administrators read
the results of a form, whose entry cuts the inheritance too. The core, as read
in its access manager and not run, gives a site administrator no such bypass.
This scenario is the check of that case on a live Jahia: if the site
administrator reads the item, the quarantine root needs another guard, and M2
does not ship without it.

**Steps:**
1. Submit the form with a file.
2. As the results reader, then the editor, then the site administrator, read the
   held submission and its item through GraphQL, through `/files/live/...` and
   through the JCR REST API.

**Expected:**
- Each read is refused or answers not found

### Q11 — The quarantine tree is inert

**Steps:**
1. Submit the form with a PDF and with an image.
2. Submit the same PDF to a form with the switch off and Save to JCR. Poll its
   file node until it carries `j:extractedText` (Groovy): the extraction of the
   core has then run. A platform that extracts no text fails this step, and the
   scenario says that it proved nothing there.
3. Read the quarantine tree with a system session (Groovy).

**Expected:**
- Two items exist, one for each upload, and each holds its binary
- No `jnt:resource` node
- No `j:extractedText` property
- No thumbnail node

### Q16 — Read-only maintenance

A form answers `FMDB-014` in read-only maintenance when it is presumed to
write. A form with the switch on is presumed to write, whatever its actions:
the hold stores a submission with a file, and the gate runs before the body
says whether a file came.

**Steps:**
1. Keep only actions that never write in both lists of the form.
2. Put the platform in read-only maintenance.
3. Submit the form with a file, then with no file.

**Expected:**
- `FMDB-014` each time
- No held submission, no item and no stored submission is written

---

## 3. Checks and release (M3)

The form of this section has one stub check in "Checks", and Save to JCR in
"Actions after release", unless the scenario says otherwise.

### Q02 — Every check passes, and the file is released

**Steps:**
1. Submit the form with a `VERDICT:PASS` file.
2. Read the SHA-256 that the item records (Groovy).
3. Run the job.

**Expected:**
- The file exists under `files/` of the stored submission, the SHA-256 of its
  bytes is the one that step 2 read, and the file carries the released marker
- The item is gone
- The state is `completed`

### Q03 — A check fails, and the submission is rejected

**Steps:**
1. Submit the form with a `VERDICT:FAIL` file.
2. Run the job.

**Expected:**
- The state is `rejected`
- No submission is stored in the results of the form, and no action after
  release ran
- The outcome and its reason are recorded on the held submission
- With M4: the outcome is on the Results page, and the alarm e-mail of the form
  is in Mailpit

### Q04 — A check that is down keeps the submission

**Steps:**
1. Submit the form with a `DOWN` file.
2. Run the job twice.
3. Age the held submission past the alarm delay (Groovy), and run the job.

The settings allow more attempts than the three runs of this scenario. Q05
covers the exhaustion of the attempts.

**Expected:**
- The submission was accepted with HTTP 200
- The state is `checking` after each run
- With M4: after step 3, the alarm e-mail of the form is in Mailpit

### Q05 — Attempts exhausted, then a manual retry

**Steps:**
1. Submit the form with a `DOWN` file.
2. Run the job until the attempts are exhausted.
3. Lift the outage of the stub, retry the submission through the
   administration mutation, and run the job.

**Expected:**
- The state is `needsReview` after step 2
- The state is `checking` after the retry, then `completed`

### Q09 — The bytes changed after intake — control

**Steps:**
1. Submit the form with a `VERDICT:PASS` file.
2. Change the binary of the item (Groovy).
3. Run the job.

**Expected:**
- No submission is stored in the results of the form
- The state is `needsReview`
- An audit record names the difference of hash

### Q12 — The visitor learns nothing of the outcome

**Steps:**
1. Submit the form twice with a `VERDICT:PASS` file, before any run of the
   job. The keys of the response body whose values differ between these two
   responses are the only keys that the comparison below ignores.
2. Submit the form with a `VERDICT:FAIL` file.
3. Run the job, so that two submissions are completed and one rejected.
4. Submit the `VERDICT:PASS` file and the `VERDICT:FAIL` file again.
5. As a guest, request the form page, and the quarantine and results paths of
   the submissions.

**Expected:**
- The five responses to a submission have the same status, the same keys and,
  but for the keys of step 1, the same values
- The file already rejected is accepted again, with HTTP 200
- No response to the guest carries a state or an outcome

### Q13 — A file that no check applies to

**Steps:**
1. Submit the form with an image: the stub check applies to text files alone.
2. Run the job.

**Expected:**
- The file is withheld: no submission is stored in the results of the form
- The state is `needsReview`
- The stub logs no call

### Q21 — Two checks, and the first fails — control

**Steps:**
1. Put two stub checks in "Checks".
2. Submit the form with a `VERDICT:FAIL` file.
3. Run the job.

**Expected:**
- The state is `rejected`
- The stub logs one call, for the first check, and none for the second

### Q22 — Two checks, and both pass

**Steps:**
1. Put two stub checks in "Checks", and two observer actions in "Actions after
   release".
2. Submit the form with a `VERDICT:PASS` file.
3. Run the job twice.

**Expected:**
- The stub logs one call for each check, in the order of the list
- Each observer action reports one call, with one file, in the order of the
  list
- The state is `completed`

### Q23 — An in-process check placed first — control

The samples module registers a second stub check, which declares that it reads
the bytes inside Jahia.

**Steps:**
1. Put the in-process stub check first in "Checks", and the stub check second.
2. Open the form in the editor.
3. Submit the form with a `VERDICT:PASS` file, and run the job.

**Expected:**
- The editor shows an alert on the list of checks
- Neither stub logs a call
- The state is `needsReview`

### Q24 — An action after release fails, and a manager resumes

**Steps:**
1. Put Save to JCR, then an e-mail content action, in "Actions after release".
2. Make the mail server refuse the mail.
3. Submit the form with a `VERDICT:PASS` file, and run the job.
4. Make the mail server accept the mail again, resume the submission through
   the administration mutation, and run the job.

**Expected:**
- After step 3, the submission is stored once, no mail is in Mailpit, and the
  state is `needsReview`
- After step 4, the submission is still stored once, one mail is in Mailpit,
  and the state is `completed`

---

## 4. Results page (M4)

### Q19 — What an analyst sees

**Steps:**
1. Submit the form three times: a `PENDING:3` file, a `VERDICT:FAIL` file, and
   a `VERDICT:PASS` file.
2. Run the job once: the first submission stays `checking`, the second is
   `rejected`, the third is `completed`.
3. On a form with the switch off and Save to JCR, submit a PDF.
4. Open the Results page of each form as the results reader.

**Expected:**
- Each submission shows its state, and the list filters on it
- The field values of the submission that is `checking` are visible
- Its file and the rejected file are listed by name, with no link
- Each file shows the outcome of each check
- The released file opens as a download, and never in an `<iframe>`
- The PDF of the form with the switch off keeps its preview

### Q20 — Manager actions

**Steps:**
1. Submit the form twice with `PENDING:9` files, and once with an
   `INCONCLUSIVE` file, then run the job once. The check of the form sends an
   inconclusive answer to review: the first two submissions are `checking`,
   and the third is `needsReview`.
2. As the results reader, try to release the first, to reject the second and
   to retry the third.
3. As a manager, release the first, reject the second and retry the third.
4. On a second form, bring a submission to `needsReview` as steps 1 to 3 of
   Q24 do, and make the mail server accept the mail again.
5. As the results reader, then as a manager, resume that submission, and run
   the job.

**Expected:**
- Each attempt of the reader is refused, and no state changes
- After step 3, the first submission is `completed`, the second `rejected` and
  the third `checking`
- After step 5, the submission of the second form is `completed`
- Each action of the manager leaves an audit record that names the user

---

## 5. Lifecycle (M6)

### Q17 — Deleting results deletes the held submissions

**Steps, by date range:**
1. Submit the form twice, with `PENDING:3` files, and date one of the two held
   submissions a month earlier (Groovy).
2. Delete the results of a range that holds the earlier submission alone.
3. Run the content-integrity scan.

**Expected:**
- The earlier held submission and its item are gone
- The other held submission and its item are still there
- The scan reports no orphan item

**Steps, "Delete all results":**
1. On another form, submit twice, with `PENDING:3` files.
2. Run "Delete all results" on that form.
3. Run the content-integrity scan.

**Expected:**
- No held submission and no item of that form is left
- The scan reports no orphan item

### Q18 — Retention

**Steps:**
1. Submit the form with a `VERDICT:FAIL` file and run the job, so that the
   submission is rejected. Age its record past the retention limit (Groovy).
2. Run the retention job.

**Expected:**
- The record of the rejected submission is purged, with its outcomes

---

## Coverage map

| Scenario | Proves |
|---|---|
| Q01 | the intake always succeeds, and nothing runs after it before a release |
| Q02 | the bytes released are the bytes checked |
| Q03 | a file that failed a check never reaches an analyst |
| Q04, Q05 | an outage of a check loses no claim |
| Q09 | a file whose bytes changed after intake is never released |
| Q10 | no reader opens a file before its release |
| Q11 | no parser of the platform reads a held file |
| Q12 | the form is no test bench for a malware author |
| Q13 | a file that no check applied to is not released by default |
| Q16 | read-only maintenance |
| Q17, Q18 | no rejected file is kept forever |
| Q19 | no active content in the analyst's browser |
| Q20 | a release is the act of a named manager |
| Q21 | a later check never reads a file that an earlier check failed |
| Q22 | the actions after release run once, in order |
| Q23 | no in-process check parses a file that no remote check passed |
| Q24 | a failure after release sends no mail twice and stores nothing twice |
