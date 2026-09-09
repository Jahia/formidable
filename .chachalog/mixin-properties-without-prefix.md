---
# Allowed version bumps: patch, minor, major
formidable: minor
---

Changed the twelve options-source and date-bounds properties to unprefixed names, migrated at startup (#312)

**Breaking change** for code that reads those properties by name. In 0.4.0 the options of a
choice field and the bound modes of a date or datetime field were stored under `fmdb:`-prefixed
names — `fmdb:options`, `fmdb:optionsMode`, `fmdb:optionsSourceKey`, `fmdb:optionsRootCategory`,
`fmdb:optionsRootNode`, `fmdb:optionsNodeType`, `fmdb:minBoundMode`, `fmdb:maxBoundMode`,
`fmdb:minRelativeAmount`, `fmdb:minRelativeUnit`, `fmdb:maxRelativeAmount`, `fmdb:maxRelativeUnit` —
the only prefixed properties of the model. They are now `options`, `optionsMode`, `minBoundMode`…
like every other property.

- **Who is affected**: a template set, a third-party module or an integration reading these
  properties in a view, a GraphQL query, a JCR-SQL2 condition, a Content Editor override or a
  label key (`fmdbmix_dateBounds.fmdb_minBoundMode` becomes `fmdbmix_dateBounds.minBoundMode`).
  Contributors and visitors see no difference: the editor, the rendered forms and the
  submissions are unchanged.
- **Existing content**: renamed automatically when the engine starts (both workspaces,
  translations included), nothing to do. An export taken from 0.4.0 still imports; restart the
  engine afterwards so the imported fields are renamed too.
- **How to check**: `jahia.log` shows `[MixinPropertyNamesMigration] Renamed the prefixed mixin
  properties of N field(s)`, and a field reads back under the new names in GraphQL.
