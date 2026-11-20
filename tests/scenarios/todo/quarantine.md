# Quarantine — Dropped scenarios

Acceptance scenarios for the quarantine of uploads that no spec will cover.

Decision: [ADR 0002](../../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

Every other scenario, `Q01` to `Q26`, moved to
[`tests/scenarios/quarantine.md`](../quarantine.md) with the spec that covers
it. The plan of 2026-11-19 ships no product quarantine module: a test module,
`formidable-test-module-quarantine`, implements the quarantine for the tests.
Two scenarios read parts of a product module that no longer exist, so they are
dropped. They keep their identifiers, so no later scenario takes them.

## Q17 — Deleting results deletes the held submissions

Dropped: the "Delete all results" of the engine knows nothing of the
quarantine tree of a test module, and no product module exists to connect them.

## Q19 — What an analyst sees

Dropped: the Results page does not change, and nothing shows a held submission
to an analyst.
