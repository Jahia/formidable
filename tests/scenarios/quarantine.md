# Quarantine — Test scenarios

Regression scenarios for the quarantine of uploads: a form with the quarantine
switch on hands its files to no action on submission, and its actions after
release receive the released files.

Decision: [ADR 0002](../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

This document holds the scenarios that a spec covers. The scenarios of the
later milestones stay in [`todo/quarantine.md`](./todo/quarantine.md) until
their specs exist. A scenario keeps its identifier wherever it moves, so the
identifiers of this document are not in order.

Specs:

- `tests/cypress/e2e/quarantine/95-uploads-hold.cy.ts` covers Q07, Q08, Q14,
  Q15, Q25 and Q26.
- `tests/cypress/e2e/quarantine/96-uploads-hold-email.cy.ts` covers Q06, and
  the mail of Q14 and of Q15.

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
  the last forward carried.
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

## Coverage map

| Scenario | Proves |
|---|---|
| Q06, Q07, Q08, Q15 | no file leaves through an action of the request |
| Q14 | no regression on a form with the switch off |
| Q25 | a submission with no file waits for nothing |
| Q26 | no action that cannot run after release enters that list |
