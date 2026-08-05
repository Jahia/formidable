# Spec groundwork: populate choice fields (select / radio / checkbox) from a choicelist initializer

Topic 2 of the 2026-07-31 roadmap. Status: **discovery done, design options laid out, decision pending.**

Related: issue to be filed (none exists yet as of 2026-07-31).

## Goal

Let a contributor fill the option list of a choice field (`fmdb:select`, `fmdb:radio`, `fmdb:checkbox`, and any future `fmdbmix:choiceField` type) from a **Jahia choicelist initializer** instead of typing options manually. Requested UX ideas (from HDU):

- either a param added to the `SelectOptions` selector type that reads a choicelist to auto-populate on first load (when the list is empty), plus a **re-sync button**;
- or a dedicated "populate from" field;
- or a **conjunction of both**: a "source" chooser — *manual* → existing `SelectOptions` editor, *from initializer* → pick an initializer;
- use `jmix:dynamicFieldset` for the manual/initializer switch.

## Current state (verified in repo, 2026-07-31)

### Data model — options are a JSON multi-valued property, NOT child nodes

- `fmdb:select` → `options (string) i18n multiple indexed=no` — `formidable-elements/src/components/Select/definition.cnd:4`
- `fmdb:radio` → `choices (string) i18n multiple mandatory` — `formidable-elements/src/components/Input/Radio/definition.cnd`
- `fmdb:checkbox` → `choices (string) i18n multiple mandatory` — `formidable-elements/src/components/Input/Checkbox/definition.cnd`
- Each value is a JSON blob `{"value","label","selected"}`; parsed at render time by `parseChoices()` — `formidable-elements/src/utils/choiceUtils.ts:7`.
- Marker mixin `fmdbmix:choiceField` (empty, semantic) — `formidable-engine/src/main/resources/META-INF/definitions.cnd:41`. **This is the natural anchor for the feature** (applies to all three types at once + future ones).

### Editor — `SelectOptions` selector type

- Registered in `formidable-engine/src/javascript/init.tsx:14` (`registry.add('selectorType', 'SelectOptions', ...)`).
- Component `formidable-engine/src/javascript/SelectOptions/SelectOptionsCmp.tsx` edits ONE value of the multi-valued property (CE's generic multiple-wrapper handles add/remove/reorder). Recently polished by PR #168 (placeholders + hover titles).
- Bound via JSON overrides `formidable-elements/settings/jahia-content-editor-forms/fieldsets/fmdb_{select,radio,checkbox}.json` (e.g. `{"name": "options", "selectorType": "SelectOptions"}`). No `selectorOptions` params used anywhere yet.

### Existing plumbing usable for the feature

- The engine already ships two `ModuleChoiceListInitializer`s (server-side template):
  `FormidableForwardTargetsInitializer.java` and `FormidableMimeTypesInitializer.java` in
  `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/choicelist/` — `@Component(service = ModuleChoiceListInitializer.class)`, fixed KEY, wired via CND `choicelist[key]`.
- CE selector components already extract editor context (path, language, workspace) + `useApolloClient()` — pattern in `ConditionalLogic/ConditionalLogic.utils.ts:137-166`.
- The platform GraphQL schema exposes `GqlEditorFormField.selectorOptions` and `valueConstraints` (root `schema.graphql:1221-1240`) and `choicelistValue(s)` (`schema.graphql:2372-2383`) — **never called by this repo yet**; would be new code.
- `jmix:dynamicFieldset` is **absent** from the repo — using it would be a first. The CE GraphQL exposes `dynamic`/`activated` on fieldsets, so the platform mechanism exists.
- Design precedent: `ConditionalLogic/README.md` ("Why the source-field lookup is done in TSX") — the team deliberately does editor-layer discovery in React when behavior depends on the edited field.

## Design questions to settle

### Q1 — Where does the truth live at render time?

Two families:

**(A) Editor-side prefill (snapshot)** — the initializer is read in the Content Editor, options are **materialized into the existing `options`/`choices` property**. Render path unchanged.
- Pros: zero engine/render change; results/exports keep working; i18n per language stays possible; offline-safe (initializer can disappear, form still renders); re-sync is an explicit contributor action (matches the "re-sync button" idea).
- Cons: values can drift from the source until re-synced; per-language sync needed (property is i18n).

**(B) Runtime resolution (live)** — store `optionsSource` and resolve the initializer at SSR time in `default.server.tsx`.
- Pros: always fresh.
- Cons: engine must expose initializer evaluation to the JS render layer (no such bridge today); submitted-value validation & results display must resolve the same source; live/preview perf; a deleted initializer breaks the form silently.

**Recommendation: (A) snapshot + re-sync.** It matches the stated UX ("populate on first load when list is empty" + re-sync button), keeps the render/submission pipeline untouched, and is resilient. (B) can be a later opt-in.

### Q2 — UI shape

Recommended conjunction (as suggested):

- New property on `fmdbmix:choiceField` (engine-owned): `optionsSource (string, choicelist[formidableOptionsSources]) = 'manual'` — values: `manual` + one entry per allowed initializer.
- `jmix:dynamicFieldset`-style switch OR handled inside the selector: two sub-options:
  1. **CND `jmix:dynamicFieldset` route**: turn the source into a mixin-driven dynamic fieldset. Heavier CND surgery, first use in repo, and the manual options editor lives on the *type's* fieldset (not a mixin's), so the show/hide split is awkward.
  2. **Selector-level route (recommended)**: extend `SelectOptionsCmp` area — when `optionsSource != manual`, the options rows become read-only + a "Re-sync from source" button appears. Matches the ConditionalLogic precedent (logic lives in the React selector), avoids dynamic-fieldset complexity, keeps one coherent editing surface.
- Caveat on the strict `jmix:dynamicFieldset` mechanism: it shows/hides a **mixin's fieldset** based on a checkbox-like activation, not a per-value switch of arbitrary sub-fields. For a value-driven switch (`manual` vs `initializer X`), CE's dynamic fieldsets are not a direct fit — verify against the installed Jahia before committing to it (cf. global lesson: don't trust docs, check the bundle).

### Q3 — Which initializers are offered?

- Do NOT offer the raw platform-wide initializer list (many are context-dependent and meaningless here).
- Curated registry: an engine-owned `formidableOptionsSources` initializer (Java, same pattern as `FormidableForwardTargetsInitializer`) that enumerates **allowed** sources; allowlist via OSGi config (`FormidableConfigService` exists) so projects can add their own (e.g. a country list, a category tree, a custom module's initializer).
- Each source resolves to `List<ChoiceListValue>` → mapped to `{value, label(displayName per language), selected:false}` JSON blobs.

### Q4 — How does the editor fetch the values? (needed for prefill + re-sync)

Options:
1. **GraphQL `choicelistValues` / forms API** (exists in schema, unused in repo) — check it can evaluate an arbitrary initializer by key with a context node; if yes, no server code needed beyond the registry initializer.
2. **Custom engine endpoint** (servlet or GraphQL extension — repo has servlets, no graphql-dxm-provider usage yet).

Verify option 1 against the installed 8.2.x first (docker cp + javap if needed); fall back to 2.

### Q5 — i18n

`options`/`choices` are i18n properties. A sync must write per-language values: resolve the initializer once per site language (initializers receive locale), write each language's JSON blobs. Re-sync button should offer "current language" vs "all languages" (or default to all).

## Proposed increment plan

1. **Issue** (problem-only, per repo convention): "choice fields: populate options from a choicelist initializer".
2. Engine: `formidableOptionsSources` registry initializer + allowlist config.
3. CND: add `optionsSource (string, choicelist[formidableOptionsSources]) = 'manual' autocreated` to `fmdbmix:choiceField` (engine CND — engine owns semantics, matches CND ownership model).
4. Editor: extend `SelectOptions` selector zone — source dropdown, auto-populate when list empty & source chosen, "Re-sync" button (with confirm, since it overwrites), read-only rows when source ≠ manual.
5. Migration: nothing needed (default `manual` = current behavior).
6. Cypress: spec in `tests/cypress/e2e/fields/` family (2xx numbering — next free after 210).

## Open points for HDU

- Confirm snapshot-vs-live (recommendation: snapshot).
- Confirm selector-level switch vs `jmix:dynamicFieldset` (recommendation: selector-level; dynamicFieldset fit is doubtful for value-driven switching).
- Decide the first shipped sources (countries? categories? languages?) to make the feature demoable.
