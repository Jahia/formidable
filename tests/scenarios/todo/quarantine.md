# Quarantine — Test scenarios

Acceptance scenarios for the quarantine of uploads: a form with the quarantine
switch on holds its files until every check of the form passed them, then runs
its actions after release.

Decision: [ADR 0002](../../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

No scenario of this document is automated yet. A scenario moves to
[`tests/scenarios/quarantine.md`](../quarantine.md) with the spec that covers
it, under `tests/cypress/e2e/quarantine/`. The scenarios of M1, those of the
intake (Q01, Q10, Q11, Q16), and those of the checks and the release (Q02, Q03,
Q04, Q09, Q12, Q13, Q21, Q22, Q23) moved there.

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

No scenario calls a real engine. The quarantine test module registers a stub
check (M3), which applies to text files alone. The stub reads its outcome from a
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
- A spec never waits for the scheduler. It starts a run through "run now" of
  the quarantine test module: `POST /modules/formidable-quarantine/run`.

## Control runs

A security scenario proves nothing if it also passes without the change. Each
scenario marked **control** runs once against a control build, and must fail
there by assertion, never by an error of its setup.

The control build keeps the API, the node types and the test modules of the
change, so the setup of the scenario still runs. It removes the one guard that
the scenario proves:

| Scenario | The control build |
|---|---|
| Q09, Q21, Q23 | moved to [`../quarantine.md`](../quarantine.md) with their specs |

---

## 1. The hold and the actions after release (M1)

The specs of M1 cover the scenarios Q06, Q07, Q08, Q14, Q15, Q25 and Q26. These
scenarios moved to [`../quarantine.md`](../quarantine.md).

---

## 2. Intake (M2)

The scenarios Q01, Q10, Q11 and Q16 moved to
[`../quarantine.md`](../quarantine.md), with the reference hold of the
quarantine test module.

---

## 3. Checks and release (M3)

The scenarios Q02, Q03, Q04, Q09, Q12, Q13, Q21, Q22 and Q23 moved to
[`../quarantine.md`](../quarantine.md), with the run and the release of the
quarantine test module. Q05 and Q24 wait for the operations of the module.

The form of this section has one stub check in "Checks", and Save to JCR in
"Actions after release", unless the scenario says otherwise.

### Q05 — Attempts exhausted, then a manual retry

**Steps:**
1. Submit the form with a `DOWN` file.
2. Run the job until the attempts are exhausted.
3. Lift the outage of the stub, retry the submission through the
   administration mutation, and run the job.

**Expected:**
- The state is `needsReview` after step 2
- The state is `checking` after the retry, then `completed`

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
| Q05 | an outage of a check loses no claim |
| Q17, Q18 | no rejected file is kept forever |
| Q19 | no active content in the analyst's browser |
| Q20 | a release is the act of a named manager |
| Q24 | a failure after release sends no mail twice and stores nothing twice |
