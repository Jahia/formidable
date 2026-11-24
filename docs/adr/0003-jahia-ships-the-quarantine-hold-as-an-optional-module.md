---
status: proposed
date: 2026-11-24
decision-makers: the Formidable maintainers
---

# Jahia ships the quarantine hold as an optional module

## Context and Problem Statement

[ADR-0002](0002-a-switch-on-the-form-holds-its-uploads-until-release.md) leaves the hold to an extension module. The hold is the security-critical part of the quarantine, as the architecture of the submission flow states. From one customer to the next, only the scanner that qualifies a file changes: a detonation sandbox, an antivirus, an allow-list.

Who writes the hold, and what does a customer who brings a scanner write?

## Decision Drivers

* The storage, the release and the audit of a held submission are written once, because each rewrite is a source of security defects.
* A customer or a partner who brings a scanner writes the code of that scanner, and nothing else.
* A reviewer handles a held submission in the editing interface, under their own account, for one site.
* A platform with no quarantine carries no binary store, no scheduled run and no manager endpoint.
* A fix to the hold or to its manager interface ships without a release of the engine.
* Another hold, such as a broker outside Jahia, can still replace the hold of a platform.

## Considered Options

* Jahia ships the hold as an optional module of the Formidable repository, and a customer writes a check against a public, versioned API of that module
* Each customer or partner writes a hold module, because every rewrite repeats the security-critical part, and two holds on one platform must hand a form off to each other correctly
* Jahia ships the hold inside the engine, because every platform then loads the binary store, the scheduled run and the manager endpoint, and a fix to the hold waits for a release of the engine
* Jahia ships the hold in a repository of its own, because it then needs a build and a release of its own, while the Formidable repository already builds optional modules against the engine API

## Decision Outcome

Chosen option: "Jahia ships the hold as an optional module of the Formidable repository, and a customer writes a check against a public, versioned API of that module", because it writes the security-critical part once, keeps it off a platform with no quarantine, and leaves the engine contract of ADR-0002 unchanged. The hold service of the engine stays public, so another hold can still take the place of this one.

### Consequences

* Good, because a customer writes and maintains a check only, and support, training and the audit format cover one model.
* Good, because the hand-off between holds becomes an edge case of a platform with a second hold, not a rule that every module must get right.
* Good, because a platform with no quarantine installs nothing of it.
* Bad, because Jahia maintains the hold, its manager interface and its scheduled run as a product.
* Bad, because the check API follows semantic versioning, and an incompatible change to it breaks every check module.
* Bad, because an operator installs one more module, and a form with the switch on and no check sends every file to review.

### Confirmation

An end-to-end scenario installs the engine, the hold module and one check module on a site with no test module, and asserts that a held file is checked on schedule, then released or sent to review. The test module runs the whole quarantine suite as a client of the public check API, with no hold of its own. The build compares the exported check API with its last release, and fails on an incompatible change that keeps the same major version.
