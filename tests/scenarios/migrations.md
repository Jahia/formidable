# Migrations suite

`tests/cypress/e2e/migrations/` holds the specs of the startup migrations, the ones that rewrite content saved by an
earlier version. Each spec stores content in its former shape, restarts the engine (or formidable-elements) so that
the migration runs, then asserts the rewritten content; the content migrations also assert both workspaces and that a
later publication still reaches the live page (`expectNoLiveOwnedProperty`). The specs leave in 0.6 together with the
migrations they cover — see `docs/administration/upgrade-notes.md`, "Startup migrations".

| Spec | Migration | Wave |
|---|---|---|
| `80-choice-options-migration.cy.ts` | `ChoiceOptionsContentMigration` | 0.4.0 |
| `81-date-bounds-migration.cy.ts` | `DateBoundsContentMigration` | 0.4.0 |
| `82-list-titles-migration.cy.ts` | `ListTitlesContentMigration` | 0.4.0 |
| `83-elements-site-reactivation.cy.ts` | `ElementsSiteReactivation` | 0.4.0 |
| `84-mixin-property-names-migration.cy.ts` | `MixinPropertyNamesMigration` | 0.5.0 |

New specs continue the 8x family (`85-`, then `810-` once the decade is full).
