# Spec groundwork: extensible conditional-logic framework

Topic 1 of the 2026-07-31 roadmap. Status: **discovery done, target architecture sketched, decisions pending.**

Issues: [#160](https://github.com/Jahia/formidable/issues/160) (any field type as logic source, semantic-mixin direction) and [#125](https://github.com/Jahia/formidable/issues/125) (community ask: make `SupportedConditionalSourceType`, `SupportedSourceType`, `FORM_TREE_BY_PATH` and operators extensible).
Additional product ask (HDU, 2026-07-31): go beyond field sources — a **framework** where new rule kinds can be plugged, e.g. *"if property X in the HTML data layer contains value Y"* or *"if this input field is filled"*.

Companion notes: `.harness/current-conditional-logic-lifecycle.md` (rule lifecycle), `.harness/spec-conditional-logic-logicid-sourcefieldkey.md` (fieldKey/sourceFieldKey identity design — any new work must compose with it).

## 1. Current implementation map (verified 2026-07-31)

### What is ALREADY extensible (good news)

- **GraphQL discovery** — `FORM_TREE_BY_PATH` (`formidable-engine/src/javascript/ConditionalLogic/graphql/queries.ts:34`) filters on mixins (`fmdbmix:formElement`, `fmdbmix:formStep`), not concrete types. Contrary to issue #125's assumption, the query itself needs no change; the restriction is client-side.
- **Java source index** — `FormSourceFieldIndex.java:58-61` is mixin-based (`fmdbmix:formElement`/`formStep` before target).
- **Java evaluator** — `ConditionalLogicEvaluator.java:71-89` is operator-only and type-agnostic.
- **Target hiding** — `LogicAwareRender.tsx` + `hidden.logic.server.tsx` wrap ANY child of a `fmdbmix:formContainer`; third-party fields can already be logic *targets* for free.
- **Semantic mixins already exist** — `fmdbmix:choiceField`, `fmdbmix:dateField`, `fmdbmix:fileField`… (`formidable-engine/src/main/resources/META-INF/definitions.cnd:~40-48`), and `fmdb:select/radio/checkbox` already carry `fmdbmix:choiceField`, `fmdb:inputDate` carries `fmdbmix:dateField`.

### The 5 hardcoded couplings to break (all per-`sourceFieldType` switches)

1. Type allowlists (duplicated): `SUPPORTED_SOURCE_TYPES` — `formidable-engine/src/javascript/ConditionalLogic/ConditionalLogic.utils.ts:13-18`; `SUPPORTED_TYPES` — `formidable-elements/src/utils/conditionalLogic.ts:25-30` (unknown types silently dropped at :39); plus `parseRule` fallback to `'fmdb:select'` (`utils.ts:35-37`).
2. Choice-property name switch — `ConditionalLogic.utils.ts:201` (`options` for select, `choices` otherwise).
3. Operator mapping switch — `getOperatorsForSource`, `ConditionalLogic.utils.ts:60-78`; value-widget branching in `ConditionalLogicCmp.tsx:324,385,389`.
4. Runtime DOM value reader — `getSourceFieldState`, `formidable-elements/src/utils/conditionalLogic.ts:95-144`, probes fixed selectors (`select`, `input[type=radio|checkbox|date]`).
5. i18n help text listing eligible types — engine `locales/en.json` key `conditionalLogic.help`.

Operators today (10): `in, notIn, isChecked, isUnchecked, containsAny, containsAll, before, after, on, between` — evaluated in **two places that must stay in sync**: browser `evaluateRule` (`conditionalLogic.ts:151-185`) and Java (`ConditionalLogicEvaluator.java:71-89`).

## 2. Target architecture

### 2.1 Core concept: value kind, not node type

Replace per-type switches with a **`valueKind`** enum owned by the engine:

| valueKind | operators | value widget | example types |
|---|---|---|---|
| `choice-single` | in, notIn | choice dropdown | select(single), radio |
| `choice-multiple` | isChecked/isUnchecked (1 choice), containsAny/containsAll | choice dropdown multi | checkbox, select(multiple) |
| `number` | eq, neq, lt, lte, gt, gte, between (NEW) | number input(s) | fmdbext:rating, scale/NPS |
| `date` | before, after, on, between | date input(s) | inputDate |
| `boolean` | isTrue, isFalse (NEW) | none | fmdbext:switch, consent |
| `text` | isEmpty, isNotEmpty, equals, contains (NEW) | text input | inputText, textarea, email… ("field is filled" = isNotEmpty) |

### 2.2 Declaration: CND semantic mixins (per issue #160 direction)

A field type opts in as a logic source by carrying an engine-owned mixin. **Reuse the existing semantic mixins where they exist** and complete the family:

- `fmdbmix:choiceField` → choice-single/multiple (single vs multiple resolved from the `multiple` property / type)
- `fmdbmix:dateField` → date
- NEW `fmdbmix:numberField` → number
- NEW `fmdbmix:booleanField` → boolean
- NEW `fmdbmix:textField` → text (decide: apply to core text inputs? this is what enables "if input is filled")

Open question: mixin-per-kind (above, zero-config, CND-only opt-in — a third-party module needs NO JavaScript to become a source) vs a single `fmdbmix:logicSource` + `logicValueKind` property. **Recommendation: mixin-per-kind** — matches the CND ownership model, is declarative, and the editor/runtime can resolve the kind from `node.isNodeType(...)` / GraphQL `isNodeType`.

Choice-property discovery: instead of the hardcoded `options` vs `choices` switch, either (a) standardize the property name for new types, or (b) let the descriptor registry override it (default `choices`, select declares `options`). (b) is backward-safe.

### 2.3 Editor: descriptor registry with mixin-based defaults

- Engine ships a **logic-source descriptor registry** (via `@jahia/ui-extender` `registry.add('formidableLogicSource', key, descriptor)`, same pattern as `init.tsx` selector registration).
- Default descriptors are resolved from the semantic mixin (no registration needed for the common case).
- A descriptor may override: choice property name, operator subset, custom value widget.
- `FORM_TREE_BY_PATH` gains `isNodeType` checks for the semantic mixins (or fetches mixin list) + fetches the declared choice property values.
- `mapSourceField` keeps only document-order-preceding fields but accepts any node carrying a source mixin.
- Help text becomes generic ("fields placed before this one that support conditions").

### 2.4 Runtime: generic DOM reader with escape hatch

Replace `getSourceFieldState`'s fixed probes with a **generic named-control reader**: within the source wrapper (`data-fmdb-node-id`), collect all form controls sharing the field name and derive `{values[], checkedCount, kind}`. Native-input-based fields (all of PR #162's fields are pure native inputs) work with **zero client code**. Escape hatch for exotic widgets: a `data-fmdb-logic-value` attribute maintained by the widget, read preferentially.

New operators must be added to BOTH evaluators (browser + Java). Add a **shared conformance test table** (same fixtures run by vitest and by a Java unit test) so the two implementations can't drift.

### 2.5 Rule model evolution

Stored rule gains `valueKind` (denormalized for robustness) next to `sourceFieldType`. Composes with the planned `sourceFieldKey` (see companion spec): identity work and extensibility work touch the same JSON — **decide sequencing** (recommendation: land fieldKey/sourceFieldKey first or together; both change `normalizeStoredRule`).

### 2.6 Non-field sources (the "framework" part — dataLayer, etc.)

**Update 2026-07-31: [PR #156](https://github.com/Jahia/formidable/pull/156) (`feat/datalayer-conditional-logic`, open) already delivers datalayer rules — as a hardcoded second rule kind, not as a provider framework.** See section 2.7 for the review against this spec. The rule JSON discriminator shipped by #156 is **`sourceType`** (`'field'` default when absent | `'datalayer'`) — adopt that name, not `sourceKind`.

The framework generalization remains desirable for a THIRD kind (e.g. URL/query param, cookie/consent state, viewport):

- **Provider contract (editor)**: label, config UI, serialization into the rule.
- **Provider contract (runtime)**: `getState(ruleConfig): values[]` + an invalidation hook. #156 wires a 100 ms polling watcher (started only when the form has datalayer rules, snapshot-diff based) into `useMultiStep` — a pluggable trigger list would replace it.
- **Server-side**: #156 settled the caveat with option (a): `ConditionalLogicEvaluator.evaluateRule` returns `false` for datalayer rules → field counts as hidden → required-validation skipped (fail-safe, documented trade-off: required-ness of a datalayer-shown field is not enforceable server-side). Any future provider must adopt the same stance.

### 2.7 Review of PR #156 against this spec (2026-07-31)

**Aligned with the spec (good):**
- `sourceType` absent = `field` → full back-compat with stored rules, same design as §2.6.
- Server stance = exactly recommendation (a), implemented in `ConditionalLogicEvaluator` + unit test.
- `FormLogicSyncService`/`FormLogicJsonEntry` let datalayer rules pass through the JSON rewrite without weakref binding (they would previously have been dropped for lacking `sourceFieldName`) — a real bug preempted.
- Safe variable resolution: dotted identifier chains only, `^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*$`, no eval.
- Watcher lifecycle is clean (conditional start, snapshot compare, cleared on unmount).
- Operators `equals/notEquals/contains/exists/notExists` are name-compatible with the future `text` valueKind operator set (§2.1).

**Divergences / debts to track:**
1. **It adds a 6th hardcoded coupling instead of a provider seam**: `if (sourceType === 'datalayer')` branches in `ConditionalLogic.types.ts`, `ConditionalLogic.utils.ts`, `ConditionalLogicCmp.tsx`, `conditionalLogic.ts` (elements), `ConditionalLogicRule.java`, `FormLogicJsonEntry.java`, `FormLogicSyncService.java`. Defensible at 2 kinds (rule of three); Phase 0 of this spec must absorb these branches into the provider abstraction.
2. **Filler metadata in stored JSON**: `normalizeStoredDatalayerRule` (engine utils) serializes `sourceFieldType: 'fmdb:select'`, `sourceNodeId: ''`, `sourceFieldName: ''` on datalayer rules. Runtime ignores them (datalayer shape checked first) but the stored data is misleading — should omit these keys. Worth a review comment.
3. **Plain-object datalayers only** (answers Q5 implicitly): dotted paths fit `window.cxs.*` / `window.wem.*` (jExperience) and `window.digitalData`-style objects, NOT GTM's `window.dataLayer` event array (no flattening, no `.get()`). Fine as v1, but the editor help/docs should say so explicitly, and GTM support becomes a follow-up.
4. **Coercion limits**: values are compared via `String(raw)` — objects become `[object Object]`, arrays join with commas; only `exists/notExists` are meaningful on non-scalars. Acceptable, should be documented.
5. **Polling forever**: the 100 ms watcher never stops once started (even after the datalayer settles). Cheap (string snapshot), but an event-based invalidation hook (§2.6) is the better long-term shape.
6. **Field-source extensibility untouched**: #156 does NOT advance #160/#125 — the `SUPPORTED_TYPES` allowlists, operator switch, and DOM reader are unchanged (the type dropdown "Field value / Datalayer value" is however a natural mount point for future provider entries).
7. No Cypress coverage for the new rule kind (author flagged it); the sync/no-weakref path and the editor flow deserve specs.
8. `fieldKey`/`sourceFieldKey` interplay: orthogonal (datalayer rules reference no field), but both PRs rewrite `normalizeStoredRule`-adjacent code — merge-order the two chantiers consciously.

## 3. Suggested phasing

1. **Phase 0 — refactor, no behavior change**: introduce valueKind + descriptor resolution internally for the 4 built-in types; generic DOM reader; conformance test table; kill the 5 hardcoded couplings. Pure refactor, protected by existing Cypress logic specs.
2. **Phase 1 — third-party opt-in (closes #160/#125)**: new semantic mixins (`numberField`, `booleanField`, `textField`), mixin-driven discovery in editor + Java, new operators (number/boolean/text) in both evaluators, docs update (`docs/how-to-extend-views-and-elements-from-third-party-module.md` + `docs/conditional-logic-field-resolution.md`). **Pilot: make PR #162's rating/scale/switch sources** (issue #160's motivating examples) — also validates the contract end to end.
3. **Phase 2 — "field is filled"**: apply `fmdbmix:textField` to core text inputs; isEmpty/isNotEmpty.
4. **Phase 3 — non-field providers**: ~~needs its own issue~~ **partially delivered early by PR #156** (datalayer as a hardcoded 2nd kind, `sourceType` discriminator, server fail-safe). Remaining: absorb #156's branches into the provider registry (do it during Phase 0 if #156 merges first), event-based invalidation, GTM-array support if ever needed.

## 3bis. Sequencing decision (REVISED 2026-07-31, per HDU)

**Do NOT merge PR #156 into main. Build the framework on an integration branch that absorbs it, then supersede #156.**

Earlier reasoning ("merge first") conflated two things: the refactor's technical needs (satisfied by the #156 *branch* existing — code, tests, design decisions; a main-merge adds zero information) and product timing (shipping datalayer early — HDU accepts shipping it with the framework instead). With #156 never in prod, the persisted-JSON concern (filler `sourceFieldType`) dissolves entirely: the stored vocabulary gets defined once, correctly, no dual format, no migration.

**Generalization decided**: the rule kind is NOT datalayer-specific. Nothing in #156's runtime mechanics (dotted-path resolution against `window`, snapshot watcher, server fail-safe, no weakref binding) is datalayer-only. Persist **`sourceType: 'jsVariable'`** instead of `'datalayer'` (decided 2026-07-31 — a rule designates one dotted variable path, not a whole "context"; "external" was rejected as non-discriminating since every future non-field provider is external too). Rename the config key `datalayerVariable` → `variable` when porting. "Datalayer" becomes a mere UI label (possibly presets: Datalayer / jExperience / custom JS variable, all serializing the same kind). `jsVariable` is ONE provider in the seam — `field` | `jsVariable` | future providers (`urlParam`, `cookie`, … not expressible as dotted window paths, hence separate providers). Naming symmetry: each provider names the thing designated (`field`, `jsVariable`, `urlParam`), not its location or technology.

Plan:
1. ✅ DONE (2026-07-31) — branch `feat/logic-framework` created from main, `feat/datalayer-conditional-logic` merged in (`1f5a47a`).
2. ✅ DONE (2026-07-31, commit `08c9576`) — Phase 0: `sourceDescriptors.ts` (valueKind 'choice'|'date', operators, choiceProperty — single table, editor no longer branches on type names); generic named-control DOM reader in elements runtime (type allowlist removed); `sourceType` renamed to `jsVariable`, `datalayerVariable` → `variable`, filler keys dropped from stored jsVariable rules; Java vocabulary renamed (150 unit tests green; tsc/vite/eslint clean both packages). NOT included: JS-side conformance test table (repo has NO JS test infra at all — vitest introduction is a team/build decision, flagged as open point); editor help-text wording (still accurate today, reword in Phase 1 when eligibility broadens); `data-fmdb-logic-value` escape hatch (contract to design in Phase 1).
3. One focused PR superseding #156; close #156 with a pointer + credit. Keep the integration branch short-lived (main moves: #162, fieldKey).
4. ✅ DONE (2026-07-31, commit on `feat/logic-framework`) — fieldKey/sourceFieldKey chantier implemented per `.harness/spec-conditional-logic-logicid-sourcefieldkey.md`: `fieldKey` hidden UUID on `fmdbmix:formLogicElement` (assigned by new `FieldKeyAssignmentListener` at creation + on-the-fly by sync for legacy), `sourceFieldKey` in rule JSON with key-first resolution (uuid/weakref as tie-breakers when keys transiently collide), backfill on first sync, same-form-copy remap of colliding keys + rule rewrite, editor writes/resolves key-first, docs + 3 unit tests (153 green). **Deviations from the spec to surface in review**: (a) the dropdown's *transient* internal value stays the JCR UUID (fields may lack a key before their first save); the *persisted* identity is sourceFieldKey as specced; (b) CND can't autocreate a UUID default → assignment is listener-based, not `autocreated`. Next: Phase 1 (#160/#125, pilot on #162 fields) → Phase 2.

Accepted trade-offs: datalayer ships later; a bigger single review instead of two incremental ones.

### Update 2026-08-03 — Phase 1 implemented; PR #162 absorbed too (per HDU)

- **Phase 1 core landed** on `feat/logic-framework` (commit `77f945e`): CND mixins
  `fmdbmix:numberField`/`booleanField`, mixin-driven eligibility (isNodeType flags in
  FORM_TREE_BY_PATH, kind-default descriptors + per-type overrides for select/checkbox),
  number operators (eq/neq/lt/lte/gt/gte/between) and boolean operators (isTrue/isFalse)
  in BOTH evaluators, denormalized `valueKind` in stored rules (disambiguates numeric vs
  date 'between'), `data-fmdb-logic-value` escape hatch, generic help text, extension-guide
  section. `textField` stays Phase 2.
- **PR #162 absorbed** into the branch (merge commit, same pattern as #156) per HDU decision
  2026-08-03: #162 was MERGEABLE but unreviewed and its fields are the pilot the framework
  needs; waiting for its independent merge blocked the pilot. The framework PR therefore
  supersedes BOTH #156 and #162 (close both with pointer + credit to romain-pm).
- **Pilot applied to the real fields**: `fmdbext:rating`/`fmdbext:scale` carry
  `fmdbmix:numberField`, `fmdbext:switch`/`fmdbext:consent` carry `fmdbmix:booleanField`
  (one CND line each, zero JS — validating the contract). Cypress spec 54 covers editor
  discovery/operators/storage AND live runtime (scale chip 10 vs gt "9" proves numeric
  comparison; switch toggle shows/hides its dependent field). `formidable-extended-inputs`
  added to the CI provisioning manifest and the test-site module list.
- Discarded alternative: sample rating/switch fields in formidable-test-module-samples-tsx
  (implemented then dropped when HDU chose to absorb #162 — the real fields make better
  pilots and the sample fields duplicated them).

## 4. Open decisions for HDU

1. Mixin-per-kind vs generic mixin+property (reco: mixin-per-kind).
2. ~~Sequencing vs the fieldKey/sourceFieldKey identity spec~~ — settled, see 3bis.
3. Server-side stance for non-field rules (reco: client-authoritative visibility, documented).
4. Does `text` valueKind ship for ALL core text inputs at once (makes every form's dropdown much longer — maybe an editor-side "show more sources" grouping)?
5. dataLayer convention to support (GTM array vs plain object vs both).
