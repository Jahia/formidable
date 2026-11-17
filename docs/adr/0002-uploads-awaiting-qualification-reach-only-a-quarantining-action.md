---
status: proposed
date: 2026-11-17
decision-makers: the Formidable maintainers
---

# Uploads awaiting qualification reach only a quarantining action

## Context and Problem Statement

A claim form receives files that third-party engines qualify, such as a detonation sandbox, before an analyst opens them. A verdict takes minutes, and the submission request answers in seconds. The submission pipeline hands every upload to every action of the form: the storage action writes it as a file that the results readers open at once, and an e-mail or a forward action sends it out of the platform. The Jahia core extracts the text of a file node as soon as the node is written, and its file servlet serves that node to whoever may read it.

Which part of Formidable decides who receives an upload before its verdict, and where do the bytes wait?

## Decision Drivers

* No action sends an upload out of the platform before its verdict.
* No parser of the platform reads the bytes before the verdict.
* An action receives no upload of a quarantined form unless its code asks for it, whoever wrote the action.
* A submission never fails and never waits because an engine is slow or down.
* A form with no quarantine keeps its behaviour.

## Considered Options

* An action declares in Java that it quarantines uploads: the pipeline hands the uploads of the form to such actions alone, and the action holds the bytes in a tree of its own node type, outside the file model, until a verdict
* A synchronous gate before the pipeline, because a sandbox verdict takes minutes, and a gate that fails closed refuses every claim while an engine is down
* A state on the stored file, behind a restricted ACL, because the core parses a file node when it is written, and every other action of the form still receives the bytes
* A mixin on the form or on the action type marks the quarantine, because a mixin is content data that the definitions of any module can attach or leave out, while the capability belongs to the code that holds the bytes

## Decision Outcome

Chosen option: "An action declares in Java that it quarantines uploads: the pipeline hands the uploads of the form to such actions alone, and the action holds the bytes in a tree of its own node type, outside the file model, until a verdict", because it is the only option where the bytes neither leave the platform nor meet a parser before a verdict, and where a submission waits for no engine. An operator setting makes the quarantine mandatory: a form with no quarantining action then hands its uploads to no action.

### Consequences

* Good, because an action that does not opt in receives no upload of a quarantined form, with no change to its code.
* Good, because the intake of a submission depends on no engine.
* Good, because the component that qualifies a file, inside Jahia or behind an external broker, changes without a change to the split or to the tree.
* Bad, because the opt-in and the creation of a submission enter the public API of the engine, and follow its semantic versioning.
* Bad, because the quarantining action replaces the storage action on a form, and a form that holds both stores each submission twice.
* Bad, because the core parses a released file for the first time at its release, and a clean verdict does not prove that the parser is safe from the file.

### Confirmation

The unit tests of the pipeline assert which actions receive the uploads, with and without a quarantining action and under the operator setting. The end-to-end scenarios assert that an e-mail and a forward action of a quarantined form carry no file, and that no reader role reads the quarantine tree. Each of these scenarios runs once against a build that lacks the one guard it proves, and fails there by assertion.
