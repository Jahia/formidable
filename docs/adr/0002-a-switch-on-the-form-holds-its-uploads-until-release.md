---
status: proposed
date: 2026-11-18
decision-makers: the Formidable maintainers
---

# A switch on the form holds its uploads until release

## Context and Problem Statement

A claim form receives files that checks qualify, such as a detonation sandbox, before an analyst opens them. A verdict takes minutes, and the submission request answers in seconds. The submission pipeline hands every upload to every action of the form: the storage action writes it as a file that the results readers open at once, and an e-mail or a forward action sends it out of the platform. The Jahia core extracts the text of a file node as soon as the node is written, and its file servlet serves that node to whoever may read it.

Which part of Formidable decides who receives an upload before its verdict, and how do the actions that need the file receive it after?

## Decision Drivers

* No action sends an upload out of the platform before its verdict.
* No parser of the platform reads the bytes before the verdict.
* No action receives an upload before its verdict, whatever its code declares and whoever wrote it.
* The contributor keeps the storage, e-mail and forward actions of the form, and they carry the file once it is released.
* A submission never fails and never waits because a check is slow or down.
* A form with no quarantine keeps its behaviour.

## Considered Options

* A switch on the form: the engine withholds the uploads from the actions of the request, hands the submission to a hold service that an extension module provides, and runs a second list of actions of the form when that module releases the submission
* An action declares in Java that it quarantines uploads, and the pipeline hands the uploads to such actions alone, because the form is then quarantined by the code of one action, and every other action of the form never receives the released file
* One storage action that holds the uploads and stores the submission at release, because the e-mail and forward actions then never carry the file, and the action duplicates the storage of the engine
* A synchronous gate before the pipeline, because a sandbox verdict takes minutes, and a gate that fails closed refuses every claim while a check is down
* A state on the stored file, behind a restricted ACL, because the core parses a file node when it is written, and every other action of the form still receives the bytes

## Decision Outcome

Chosen option: "A switch on the form: the engine withholds the uploads from the actions of the request, hands the submission to a hold service that an extension module provides, and runs a second list of actions of the form when that module releases the submission", because it is the only option where no action receives an upload before its verdict, whatever its code, and where the actions that the contributor already configures carry the released file. The engine refuses a submission with an upload when the switch is on and no hold service is installed, and an operator setting withholds the uploads of every form whose switch is off. The code of a type, never content, declares that an action may run at release, and where a check reads the bytes.

### Consequences

* Good, because no action of the request receives an upload of a form with the switch on, with no change to its code.
* Good, because the storage, e-mail and forward actions run at release as they run in a request, so the engine keeps one storage path.
* Good, because the intake of a submission depends on no check.
* Good, because a check changes, inside Jahia or behind an external broker, without a change to the hold or to the release.
* Bad, because the hold service and the run of the actions at release enter the public API of the engine, and follow its semantic versioning.
* Bad, because an action that runs at release has no HTTP request: the engine gives it the values it captured at intake from a documented list, and an action that reads anything else fails.
* Bad, because the values of a held submission, the sensitive ones included, wait in the repository until the release or the rejection, then are deleted.
* Bad, because a submission with no upload runs its second list inside the request, so one list runs at two moments.
* Bad, because the core parses a released file for the first time at its release, and a clean verdict does not prove that the parser is safe from the file.

### Confirmation

The unit tests of the pipeline assert which actions receive the uploads, with the switch on and off, with and without a hold service, and under the operator setting; the tests of each core action run it without an HTTP request. The end-to-end scenarios assert that an e-mail and a forward action of the request carry no file, that the second list runs with the released files, and that no reader role reads the held bytes. Each security scenario runs once against a build that lacks the one guard it proves, and fails there by assertion.
