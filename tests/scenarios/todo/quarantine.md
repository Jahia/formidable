# Quarantine — Scenarios with no spec

Acceptance scenarios for the quarantine of uploads that no spec covers yet.

Decision: [ADR 0002](../../../docs/adr/0002-a-switch-on-the-form-holds-its-uploads-until-release.md).
Tracking: [formidable#489](https://github.com/Jahia/formidable/issues/489).

Every other scenario, `Q01` to `Q26`, moved to
[`tests/scenarios/quarantine.md`](../quarantine.md) with the spec that covers
it. Two scenarios read parts of a product module. The plan of 2026-11-19
shipped none, so they were dropped.
[ADR 0003](../../../docs/adr/0003-jahia-ships-the-quarantine-hold-as-an-optional-module.md)
proposes that Jahia ships a product module, `formidable-quarantine`, which
later work builds. So both scenarios are open again. They wait here until a spec of that module covers them, and they
keep their identifiers.

## Q17 — Deleting results deletes the held submissions

Open: the "Delete all results" of the engine knows nothing of the quarantine
tree. The design of `formidable-quarantine` does not connect them yet.

## Q19 — What an analyst sees

Open: the design of `formidable-quarantine` shows a held submission to a
quarantine manager only
([Quarantine of the uploads](../../../docs/architecture/quarantine.md)).
No part of it shows a held submission to a results reader yet.
