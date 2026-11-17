# Quarantine — Test scenarios

Acceptance scenarios for the quarantine of uploads: a form holds its files in a
quarantine tree of the JCR until third-party engines qualify them.

Decision: [ADR 0002](../../../docs/adr/0002-uploads-awaiting-qualification-reach-only-a-quarantining-action.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

Nothing here is automated yet. A scenario moves to
`tests/scenarios/quarantine.md` with the spec that covers it, under
`tests/cypress/e2e/quarantine/`.

A scenario keeps its identifier, `Q01` to `Q20`, wherever it moves: a spec, a
pull request and the coverage map name it by that identifier. The sections
follow the milestones, so the identifiers are not in order.

## Milestones

Each milestone is one pull request, and delivers the specs of its scenarios.

| Milestone | Delivers | Scenarios |
|---|---|---|
| M1 | The split of the uploads in the engine, and a sample quarantining action | Q06, Q07, Q08, Q14, Q15 |
| M2 | The quarantine module: the action "Save with quarantine" and the intake | Q01, Q10, Q11, Q12, Q16 |
| M3 | The qualifiers, the qualification job, the release policy | Q02, Q03, Q04, Q05, Q09, Q13 |
| M4 | The Results page | Q19, Q20 |
| M5 | A qualifier for a real sandbox | none: its client is tested against a recorded double, and once against the real service in a lab |
| M6 | Retention and integrity | Q17, Q18 |

---

## Vocabulary

- **Quarantining action**: a form action whose Java code declares that it
  quarantines uploads. The samples module ships one for the tests (M1), and the
  quarantine module ships "Save with quarantine" (M2).
- **Quarantined form**: a form whose `actions` list holds a quarantining action.
- **Item**: one upload held in the quarantine tree, with its SHA-256.
- **State** of a submission: `pending`, `released`, `rejected` or `error`.
- **Qualifier**: an engine that gives a verdict on an item.
- **Policy** of a form: the settings of its quarantining action (M3). They name
  the qualifiers that a release requires, what happens to a file of a type that
  a qualifier does not support (withheld by default), and the delay after which
  a pending submission is flagged to an administrator.

## Common setup

1. Create a site with the Formidable modules and the samples module deployed.
2. Create and publish a form with one text field and one file field.
3. Add the quarantining action to its `actions` list, unless the scenario says
   otherwise.
4. Create a user that holds `fmdb-results-reader` on the form, an editor and a
   site administrator.

## The qualifier double

No scenario calls a real engine. The samples module registers a stub qualifier
(M3). The stub reads its verdict from a marker in the uploaded text file:

| Marker | The stub answers |
|---|---|
| `VERDICT:CLEAN` | clean |
| `VERDICT:MALICIOUS` | malicious |
| `PENDING:<n>` | pending for `<n>` polls, then clean |
| `DOWN` | a failure that can be retried, at every call |
| `UNSUPPORTED` | unsupported type |

- The repository holds no malware and no EICAR file: the antivirus of a
  developer's machine deletes it. A scenario that needs the EICAR string builds
  it at run time.
- A spec never waits for the scheduler. It runs the qualification job through
  the administration mutation "run now" (M3).

## Control runs

A security scenario proves nothing if it also passes without the change. Each
scenario marked **control** runs once against a control build, and must fail
there by assertion, never by an error of its setup.

The control build keeps the API, the node types and the test modules of the
change, so the setup of the scenario still runs. It removes the one guard that
the scenario proves:

| Scenario | The control build |
|---|---|
| Q06, Q07, Q08 | hands the uploads to every action, whatever an action declares |
| Q15 | ignores `uploadsRequireQuarantine` |
| Q10 | leaves the ACL inheritance of the quarantine root in place |
| Q09 | releases without computing the hash again |

---

## 1. The split of the uploads (M1)

### Q06 — An e-mail action sends no file — control

**Steps:**
1. Add an e-mail content action with attachments after the quarantining action.
2. Submit the form with a file.

**Expected:**
- HTTP 200
- The mail in Mailpit carries the field values and no attachment

### Q07 — A forward action sends no file — control

**Steps:**
1. Add a forward action after the quarantining action.
2. Submit the form with a file.

**Expected:**
- HTTP 200
- The target receives the values and no file part

### Q08 — An action that did not opt in receives no file — control

**Steps:**
1. Add a third-party action of the samples module that does not opt in.
2. Submit the form with a file.

**Expected:**
- The action reports zero files
- The quarantining action reports one file

### Q14 — A form with no quarantining action is unchanged

**Steps:**
1. Use a form with Save to JCR, an e-mail content action and no quarantining
   action.
2. Submit the form with a file.

**Expected:**
- The file is stored under the submission, as before
- The mail carries the attachment, as before

### Q15 — The quarantine is mandatory, and the form has none — control

`uploadsRequireQuarantine` is an operator setting of the engine, in
`org.jahia.modules.formidable.uploads.cfg`. It applies to every form of the
platform, and it is `false` by default: Q14 runs with the default.

**Steps:**
1. Set `uploadsRequireQuarantine=true`.
2. Use the form of Q14: Save to JCR, an e-mail content action, and no
   quarantining action.
3. Submit the form with a file.

**Expected:**
- HTTP 200
- The submission is stored with its values and no file
- The mail carries no attachment

---

## 2. Intake (M2)

### Q01 — A submission with a file lands as pending

**Steps:**
1. Submit the form with a file.

**Expected:**
- HTTP 200 to the visitor
- The submission exists, with state `pending`
- One item exists in the quarantine tree, with the SHA-256 of the upload
- No node exists under `files/` of the submission

### Q10 — No reader role reads the quarantine tree — control

The quarantine root cuts the ACL inheritance and holds no access control entry,
so a role granted on the site, the site administrator included, gives nothing
on it. A system session, and a user that bypasses the ACLs such as `root`, are
outside this scenario.

**Steps:**
1. Submit the form with a file.
2. As the results reader, then the editor, then the site administrator, read the
   item through GraphQL, through `/files/live/...` and through the JCR REST API.

**Expected:**
- Each read is refused or answers not found

### Q11 — The quarantine tree is inert

**Steps:**
1. Submit the form with a PDF and with an image.
2. Read the quarantine tree with a system session (Groovy).

**Expected:**
- No `jnt:resource` node
- No `j:extractedText` property
- No thumbnail node

### Q12 — The visitor learns nothing of the verdict

The stub qualifier of M3 completes this scenario: steps 2 to 4 need a verdict.

**Steps:**
1. Submit the form with a `VERDICT:CLEAN` file, then with a `VERDICT:MALICIOUS`
   file.
2. Run the job, so that one file is released and the other rejected.
3. Submit the same two files again.
4. As a guest, request the form page, and the quarantine and results paths of
   both submissions.

**Expected:**
- The four response bodies of the submissions are the same, but for what
  differs between two submissions of one file
- The file already rejected is accepted again, with HTTP 200
- No response to the guest carries a state or a verdict

### Q16 — Read-only maintenance

**Steps:**
1. Put the platform in read-only maintenance.
2. Submit the form with a file.

**Expected:**
- `FMDB-014`
- No submission and no item is written

---

## 3. Qualification (M3)

### Q02 — A clean verdict releases the file

**Steps:**
1. Submit the form with a `VERDICT:CLEAN` file.
2. Run the job.

**Expected:**
- The file exists under `files/` of the submission, with the SHA-256 of the item
- The item is gone
- The state is `released`

### Q03 — A malicious verdict rejects the file

**Steps:**
1. Submit the form with a `VERDICT:MALICIOUS` file.
2. Run the job.

**Expected:**
- The state is `rejected`
- No node exists under `files/` of the submission
- The verdict and its detail are recorded on the submission

### Q04 — An engine that is down keeps the submission pending

**Steps:**
1. Submit the form with a `DOWN` file.
2. Run the job several times.
3. Age the item past the delay of the form's policy (Groovy), and run the job.

**Expected:**
- The submission was accepted with HTTP 200
- The state stays `pending` after each run
- After step 3, the submission is flagged to an administrator

### Q05 — Attempts exhausted, then a manual retry

**Steps:**
1. Submit the form with a `DOWN` file.
2. Run the job until the attempts are exhausted.
3. Make the stub answer clean, retry the submission as a manager, run the job.

**Expected:**
- The state is `error` after step 2
- The state is `pending` after the retry, then `released`

### Q09 — The bytes changed after intake — control

**Steps:**
1. Submit the form with a `VERDICT:CLEAN` file.
2. Change the binary of the item (Groovy).
3. Run the job.

**Expected:**
- No file exists under `files/` of the submission
- The state is `error`
- An audit record names the difference of hash

### Q13 — A type the engine does not support

**Steps:**
1. Submit the form with an `UNSUPPORTED` file, and run the job.
2. Set the policy of the form to release an unsupported file, submit again, and
   run the job.

**Expected:**
- The first file is withheld
- The second file is released

---

## 4. Results page (M4)

### Q19 — What an analyst sees

**Steps:**
1. Submit the form three times: a pending file, a rejected file, a released PDF.
2. Open the Results page as the results reader.

**Expected:**
- Each submission shows its state, and the list filters on it
- The field values of the pending submission are visible
- The pending file and the rejected file are listed by name, with no link
- The released PDF opens as a download, and never in an `<iframe>`
- A file of a form with no quarantine keeps its preview

### Q20 — Manager actions

**Steps:**
1. As the results reader, try to release, then to reject, a pending submission.
2. Do the same as a user that holds the manager role.

**Expected:**
- Each attempt of the reader is refused
- Each action of the manager succeeds, and leaves an audit record that names
  the user

---

## 5. Lifecycle (M6)

### Q17 — Deleting results deletes the items

**Steps:**
1. Submit the form several times, with pending files.
2. Delete the results of a date range, then "Delete all results".
3. Run the content-integrity scan.

**Expected:**
- The items of each deleted submission are gone
- The scan reports no orphan item

### Q18 — Retention

**Steps:**
1. Reject a file, and age its item past the retention limit (Groovy).
2. Run the retention job.

**Expected:**
- The binary of the item is purged
- The verdict stays on the submission

---

## Coverage map

| Scenario | Milestone | Proves |
|---|---|---|
| Q01 | M2 | the intake always succeeds |
| Q02 | M3 | the bytes released are the bytes qualified |
| Q03 | M3 | a malicious file never reaches an analyst |
| Q04, Q05 | M3 | an engine outage loses no claim |
| Q06, Q07, Q08, Q15 | M1 | no file leaves through another action |
| Q09 | M3 | a file whose bytes changed after intake is never released |
| Q10 | M2 | no reader opens a file before its verdict |
| Q11 | M2 | no parser of the platform reads a quarantined file |
| Q12 | M2 | the form is no test bench for a malware author |
| Q13 | M3 | an unsupported type is not released by default |
| Q14 | M1 | no regression on a form with no quarantine |
| Q16 | M2 | read-only maintenance |
| Q17, Q18 | M6 | no malicious file is kept forever |
| Q19 | M4 | no active content in the analyst's browser |
| Q20 | M4 | a release is the act of a named manager |
