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
- `tests/cypress/e2e/quarantine/97-quarantine-intake.cy.ts` covers Q01, Q10,
  Q11 and Q16, with the reference hold of the quarantine test module.

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
- **Quarantine test module**: `formidable-test-module-quarantine`. It ships the
  reference hold, the quarantine tree and an inspection of the tree. Never for
  production: its checks are stubs.
- **Check of the module**: a node of `fmdbquar:remoteStubCheck` or
  `fmdbquar:inProcessStubCheck` in the list "Checks" (`qualifiers`) of a form.
- **Reference hold**: the hold of the quarantine test module. It ranks above the
  sample hold. It owns a form whose list "Checks" holds a check of the module,
  and stores its submissions in the quarantine tree. It hands any other form to
  the sample hold.
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

The specs of the reference hold (97) also enable the quarantine test module on
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
| Q10 | leaves the ACL inheritance of the quarantine root in place: the quarantine test module without the line `root.setAclInheritanceBreak(true);` of `JcrQuarantineStore.rootOf` |

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

## The intake of the reference hold

The scenarios of this section use the reference hold of the quarantine test
module. The form has a remote stub check in "Checks", unless the scenario says
otherwise. No scenario of this section releases a submission: the checks and
the release come with the next part of the module.

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

The spec also submits a file to a form with the switch on and no check of the
module. The sample hold reports one call with one file, and the quarantine tree
holds nothing for that form.

### Q10 — No reader role reads the quarantine tree — control

The quarantine root cuts the ACL inheritance and holds no access control entry,
so a role granted on the site, the site administrator included, gives nothing
on it. A system session, and a member of the server `administrators` group such
as `root`, are outside this scenario: the Jahia core grants them every
permission, whatever the ACL says.

The store creates the root and cuts its inheritance. It uses a node that
already exists at that path only when the node is a `fmdbquar:quarantineRoot`
that cuts its inheritance and holds no access control entry. Any other node
refuses the hold, and the visitor gets FMDB-018. The unit tests of
`JcrQuarantineStore` prove that refusal; this scenario does not.

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

**Control build:** the quarantine test module with the line
`root.setAclInheritanceBreak(true);` removed from `JcrQuarantineStore.rootOf`.
Build it with `package`, deploy it in place of the module, and run spec 97. The
spec creates the site again, so the quarantine root is created by the control
build. Q10 must fail by assertion on a read of the editor or of the site
administrator. The control build creates a root that inherits its access, so
it refuses every later hold on that site with FMDB-018: the tests of spec 97
that submit after the first hold fail too, and prove nothing about Q10.

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

## Coverage map

| Scenario | Proves |
|---|---|
| Q01 | the intake always succeeds, and nothing runs after it before a release |
| Q06, Q07, Q08, Q15 | no file leaves through an action of the request |
| Q10 | no reader opens a file before its release |
| Q11 | no parser of the platform reads a held file |
| Q14 | no regression on a form with the switch off |
| Q16 | read-only maintenance writes nothing |
| Q25 | a submission with no file waits for nothing |
| Q26 | no action that cannot run after release enters that list |
