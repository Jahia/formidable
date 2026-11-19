# formidable-test-module-quarantine

A reference hold for the tests of the quarantine contract of `formidable-engine`. The engine offers the contract: the switch `fmdbmix:quarantine`, the lists "Checks" and "Actions after release", the extension point `SubmissionHold` and the service `DeferredActions` ([ADR 0002](../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md)). This module implements a whole quarantine on that contract, so that the Cypress and JUnit suites prove the behaviour end to end.

**Never deploy this module in production.** Its checks are stubs: they read their outcome from a marker in the uploaded file. They detect nothing.

## Which forms the module owns

The reference hold owns a form only when the "Checks" list of the form (`qualifiers`) holds at least one check type of this module. It hands any other form to the next registered `SubmissionHold`, such as the sample hold of `formidable-test-module-samples-java`. With no other hold, it refuses the submission.

## Node types

The module declares its types in its own namespace, `fmdbquar` (`http://www.jahia.org/jahia/fmdbquar/nt/1.0`).

| Type | Role |
|---|---|
| `fmdbquar:remoteStubCheck` | A stub of a check that asks a remote service |
| `fmdbquar:inProcessStubCheck` | A stub of a check that reads the file inside the JVM |

Each type takes the marker of the engine, `fmdbmix:qualifier`, as a supertype. The "Checks" list accepts no other kind of node.

## Java packages

The base package is `org.jahia.test.modules.formidable.quarantine`. The module compiles against the `api` package of the engine only.

| Package | Content |
|---|---|
| `hold`, `store` | The hold and the quarantine tree |
| `check`, `chain`, `state`, `stub` | The checks, their chain, the state machine and the stubs. The chain and the state machine import no JCR and no OSGi class |
| `run`, `ops`, `web` | The run over the due submissions, the operations and the endpoints |

## Build and test

```
mvn test -pl jahia-test-module/formidable-test-module-quarantine -am
```
