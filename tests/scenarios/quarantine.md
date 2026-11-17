# Quarantine — Test scenarios

Regression scenarios for the quarantine of uploads: a form whose action list
holds a quarantining action hands its uploads to that action alone.

Decision: [ADR 0002](../../docs/adr/0002-uploads-awaiting-qualification-reach-only-a-quarantining-action.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

This document holds the scenarios that a spec covers. The scenarios of the
later milestones stay in [`todo/quarantine.md`](./todo/quarantine.md) until
their specs exist. A scenario keeps its identifier wherever it moves.

## Vocabulary

- **Quarantining action**: a form action whose Java code declares that it
  quarantines uploads. The samples module ships one for the tests,
  `fmdbsample:uploadQuarantiningAction`. It is a test double, and it keeps no
  file.
- **Quarantined form**: a form whose `actions` list holds a quarantining action.
- **Observer**: `fmdbsample:uploadObserverAction` of the samples module, an
  action that does not opt in.
- **Forward sink**: a receiver of the samples module, which the spec declares
  as the forward target `samplesink`. It answers the names of the parts that
  the last forward carried.

## Common setup

1. Create a site with the Formidable modules and the samples module deployed.
2. Set `enableDevForwardTargets=true`: the forward sink is plain HTTP on the
   instance.
3. Create and publish a form with one text field and one file field.
4. Submit the form with a value and one text file, as a direct multipart POST.

## Control runs

A security scenario proves nothing if it also passes without the change. Each
scenario marked **control** runs once against a control build, and must fail
there by assertion, never by an error of its setup.

The control build keeps the API, the node types and the test modules of the
change. It removes the one guard that the scenario proves:

| Scenario | The control build |
|---|---|
| Q07, Q08 | hands the uploads to every action, whatever an action declares |
| Q15 | ignores `uploadsRequireQuarantine` |

## 1. The split of the uploads

Spec: `tests/cypress/e2e/quarantine/95-uploads-split.cy.ts`.

### Q07 — A forward action sends no file — control

**Steps:**
1. Use a form with Save to JCR, a forward action to the forward sink, the
   observer and the quarantining action, in that order.
2. Submit the form with a file.

**Expected:**
- HTTP 200
- The forward sink received the values and no file part
- The submission that Save to JCR stored holds the values and no file

### Q08 — An action that did not opt in receives no file — control

**Steps:**
1. Use the form of Q07.
2. Submit the form with a file.

**Expected:**
- The observer reports zero files
- The quarantining action reports one file

### Q14 — A form with no quarantining action is unchanged

**Steps:**
1. Use a form with Save to JCR, a forward action to the forward sink, the
   observer, and no quarantining action.
2. Submit the form with a file.

**Expected:**
- The file is stored under the submission, as before
- The forward sink received the file part, as before
- The observer reports one file

### Q15 — The quarantine is mandatory, and the form has none — control

`uploadsRequireQuarantine` is an operator setting of the engine, in
`org.jahia.modules.formidable.uploads.cfg`. It applies to every form of the
platform, and it is `false` by default: Q14 runs with the default.

**Steps:**
1. Set `uploadsRequireQuarantine=true`.
2. Use the form of Q14.
3. Submit the form with a file.

**Expected:**
- HTTP 200
- The submission is stored with its values and no file
- The forward sink received the values and no file part
- The observer reports zero files

## Coverage map

| Scenario | Spec | Unit test |
|---|---|---|
| Q07 | `quarantine/95-uploads-split.cy.ts` | `FormSubmissionPipelineUploadSplitTest` |
| Q08 | `quarantine/95-uploads-split.cy.ts` | `FormSubmissionPipelineUploadSplitTest` |
| Q14 | `quarantine/95-uploads-split.cy.ts` | `FormSubmissionPipelineUploadSplitTest` |
| Q15 | `quarantine/95-uploads-split.cy.ts` | `FormSubmissionPipelineUploadSplitTest` |

The e-mail scenario Q06, and the mail assertions of Q14 and Q15, stay in
`todo/quarantine.md`: the suite has no mail sink yet.
