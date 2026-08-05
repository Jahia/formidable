# Resume point: extensible logic framework (topic 1)

## Snapshot 2026-08-05: #172 MERGED, Phase 2 shipped as PR #174

- **PR #172 merged 2026-08-05 08:29** (`7ccb1b1` on main).
- **Phase 2 "field is filled" implemented the same day** on branch `feat/logic-textfield`,
  **PR #174 OPEN**: `fmdbmix:textField` mixin + `text` valueKind, operators
  isNotEmpty/isEmpty/equals/contains in both evaluators, opt-in on
  fmdb:inputText/textarea/inputEmail. Scope decisions (HDU 2026-08-05): 3 free-text core inputs
  only (no inputHidden), full operator set, no source-dropdown grouping (spec §4 stays open,
  revisit on feedback).
- Semantics guard: equals/contains require a non-empty expected value — an empty text input
  submits "" server-side but exposes no value browser-side; isEmpty covers the empty case.
  Documented in the extension guide + README.
- Validation: 168 Java unit tests green (3 new), tsc/eslint clean, both modules deployed locally
  with forceUpdate + marker grep, Cypress logics 50-55 = 14/14 green (new spec 55; spec 50
  adapted — its fixture textarea 'notes' is now a legitimate source).
- Chachalog entry references #174. NOTE: the PR's first Changelog check ran before the chachalog
  commit was pushed and failed; the push re-triggers it — verify it flipped green.
- Next: #163 rebase help (romain-pm), follow-ups #170/#171/#173, then spec §3 provider-seam
  hardening (event-based jsVariable invalidation, urlParam/cookie providers).

---

Snapshot **2026-08-04 end of session**. Branch **`feat/logic-framework`**, **PR #172 OPEN**,
head `9f1248b`, working tree clean, all pushed.

## ⚡ NEXT SESSION (2026-08-05): HDU merges the PR, then the follow-up cascade

1. **Check the last CI run**: 30930410042 (commit `9f1248b`, a11y fallbacks + boolean validation)
   was IN PROGRESS at session end — every previous run of the day was green, this one only adds
   aria-labels + a validator relaxation (19/19 unit tests green, fields specs 11/11 green locally).
   `gh run view 30930410042 --repo Jahia/formidable --json conclusion`
2. **Merge is BLOCKED on human review** (branch protection: REVIEW_REQUIRED, no reviewer
   requested yet, author can't self-approve). Suggested: request review from romain-pm
   (their PRs #156/#162 are absorbed here; #163 is theirs to rebase after merge).
   Optionally refresh the PR body first with the day's additions (see log below).
3. **Post-merge cascade**: rebase help for PR #163 (conflicts: FormFieldMetadataCollector,
   CI manifest), follow-ups #170 (shared UI package), #171 (inputNumber), #173 (inputRange),
   then Phase 2 "field is filled" (fmdbmix:textField, spec §4 open decision).

## Session log 2026-08-04 (all pushed, all CI-green except the last run pending)

- `35ac033` CI fix: extended-inputs tgz copied to target/ (root cause of ALL integration-test
  failures: build-artifacts only collects **/target/, the module was never installed in CI —
  "fmdbext: is not a registered namespace prefix"). Mechanism confirmed in jahia-modules-action
  sources; provisioning-manifest-build.yml must NOT list extended-inputs (would pull stale Nexus).
- `f9dc1b6` release alignment (sync-version.js + release-prepare profile; package.json → tabs).
  Remaining pom diff vs elements: jahia-module-signature (vestigial — update-signature only signs
  'bundle' packaging; confirm with team before release).
- `c9a1114` Copilot round 1 (6 comments, replies posted): strict number parsing browser-side,
  O(n·depth) topmostPaths (NB: Copilot's sort-and-skip suggestion is WRONG for JCR paths — '.'
  sorts before '/'), validationProps comment, 2 chachalog rewordings.
- `94cc387` Copilot round 2 (5 SUPPRESSED findings, all valid, PR comment 5179252649): boolean
  isTrue/isFalse treat explicit "false" as off BOTH sides (switch buttons submits value="false"!),
  jsVariable logicIds excluded from logicsSrc orphan cleanup (test added), rating hidden
  minValue=1, ScaleField honors maxValue=0, FieldKeyAssignmentListener batches per event batch.
- `ce31595`+`12ca032` resource bundles: 4 locales at 67-key parity, tooltips restructured on the
  elements pattern (Usage/Validation/Example/Note + "If empty, default value:"), consent.helpText
  key added (was missing even in EN).
- `cb7bcb4` scale self-explanatory (displayName "Scale - Net Promoter Score (0-10)", tooltip
  "clickable chips, not a slider") + **issue #173** created (native inputRange belongs in
  formidable-elements, sibling of #171).
- `0b39d5c`+`59e8d72` scale layout: auto-fit grid in a body wrapper — fluid chips
  (--fmdbext-chip-min-width floor), end labels aligned by construction, equal columns when
  wrapping. HDU-driven design after screenshots of desynced max-widths.
- `5784f72` rating: own-drawn SVG mask stencils (sources in Rating/icons/), thumb = OUTLINED hand
  + filled wrist (HDU taste, "semi-full ok"), number-chip hover fills whole chip, flex gap →
  in-item padding (gap crossing flashed the fill-up), transitions on background/border.
- `380b23e` consent v2: statement = plain (string,textarea) — renders in a <label>,
  phrasing-only content — helpText first in CND, terms link right after statement; spec 215 adapted.
- `9f1248b` Copilot round 3 (4 suppressed, 3 valid, PR comment 5182005022): aria-label fallbacks
  (switch both modes, consent), validateBoolean accepts case-insensitive true/false + "on"
  (was inconsistent with the evaluators' new truthy contract — third-party plain checkboxes
  would fail submission), contract spelled out in the extension guide.
- Fields specs 212-215 run TWICE locally: 11/11 green each time. Local Jahia has everything
  deployed (engine jar + both JS modules, forceUpdate).
- Global CLAUDE.md gained: chachalog style rules; docs/ Academy standards (Front Matter sync,
  link rules, style guide) — Formidable's docs/ are all internal (no Front Matter), fine as is.

Previous snapshot (2026-08-03) below.

---

Snapshot **2026-08-03 end of session**. Branch **`feat/logic-framework`** PUSHED (17 commits over
main incl. the two absorbed-branch merges), **PR #172 OPEN**
(https://github.com/Jahia/formidable/pull/172), working tree clean.
PRs #156 and #162 closed as superseded (credit comments to romain-pm); coordination comment
posted on #163. Chachalog: 3 entries referencing #172 — the WF/Changelog check passes.

## ✅ RESOLVED 2026-08-04: CI integration-tests root cause found & fixed

Root cause: the CI `build-artifacts` upload only collects files under `**/target/`.
`formidable-elements` (and both tsx test modules) expose their JS package via a
**maven-antrun copy** of `dist/package.tgz` → `target/<finalName>.tgz`; the
`formidable-extended-inputs` pom (inherited from PR #162) was missing that execution, so its
tgz never reached the integration-tests instance → module absent. Confirmed by the Cypress XML
reports: `javax.jcr.NamespaceException: fmdbext: is not a registered namespace prefix` on every
addNode (specs 212-215 AND logic spec 54 live test). Fix: commit `35ac033` adds the same antrun
execution; verified locally that `mvn -pl formidable-extended-inputs package` now produces
`target/formidable-extended-inputs-0.4.0-SNAPSHOT.tgz`. CI re-run: 30891278217.

Mechanism confirmed at source level (jahia-modules-action `integration-tests/src/artifacts/prepareBuildArtifacts.ts`):
it scans all `target/` folders for `*-SNAPSHOT.jar`/`*-SNAPSHOT.tgz` and copies them to `tests/artifacts/`,
which the @jahia/cypress test image installs ON TOP of the provisioning manifest. So
`provisioning-manifest-build.yml` must NOT list extended-inputs (it would install the stale
published #162 Nexus artifact instead of the PR build) — no manifest change needed.

Also 2026-08-04 (`f9dc1b6`): release alignment — extended-inputs pom now mirrors elements'
maven-release-plugin config + `release-prepare` profile (own `.m2/sync-version.js`, package.json
normalized to tabs) so the released tgz gets its package.json version synced. Remaining pom diff
vs elements: `jahia-module-signature` (elements has one, likely vestigial — the update-signature
action only signs `bundle` packaging and ignores `pom` modules; confirm with team before release).

## ~~⚠️ FIRST THING NEXT SESSION: PR #172 CI — integration-tests FAIL~~ (resolved above)

Run https://github.com/Jahia/formidable/actions/runs/30834366333 (job 91756921586).
**Every extended-inputs field spec fails in CI while green locally**: 212 (2/2), 213 (3/3),
214 (4/4), 215 presumably too (log truncated). Everything BEFORE them passes (actions/70,
fields/20/21/210/211…). All other checks green (build, sonar, static analysis, changelog).

Diagnosis so far:
- CI integration tests use **`provisioning-manifest-build.yml`** (NOT the snapshot manifest I
  extended) — see `.github/workflows/on-code-change.yml` → reusable-integration-tests@v2 with
  `provisioning_manifest: provisioning-manifest-build.yml`, `module_id: formidable`.
- The build manifest lists only content-integrity + jcontent; elements/engine reach the CI
  instance through the jahia-modules-action artifact mechanism (reactor build → installed by the
  reusable workflow). The question is whether that mechanism picked up the NEW reactor module
  `formidable-extended-inputs` (tgz). Two candidate failure modes: module ABSENT in CI (most
  likely — all its specs fail incl. basic rendering) or STALE July version from Nexus.
- Do NOT blindly add `js:mvn:...LATEST/tgz` to provisioning-manifest-build.yml: for a PR that
  would install the OLD published #162 artifact, not the PR build.

Investigation steps:
1. `mise exec -- gh run download 30834366333 --repo Jahia/formidable` (or view artifacts) →
   Cypress screenshots/reports: absent module shows GraphQL addNode failures for fmdbext types;
   stale module shows assertion diffs (e.g. 212 first-label '1' vs old reversed DOM '7').
2. Check how jahia-modules-action/build@v2 collects artifacts (does it include all reactor
   modules' dist/package.tgz?) and how reusable-integration-tests installs them; mirror whatever
   makes formidable-elements (also a tgz reactor module) work.
3. `FORMIDABLE_MODULE_IDS` (constants.ts) now enables `formidable-extended-inputs` on the test
   site — if the module is absent, `enableModule` itself may be the first failure point.

## Copilot review round 2 — 5 SUPPRESSED findings, all valid, fixed in `94cc387` (2026-08-04)

Copilot's re-review (09:37) said "no new comments" but hid 5 findings in a collapsed details
block — all real, all fixed (PR comment 5179252649 documents them):
1. isTrue/isFalse: switch buttons mode submits value="false" for an answered no, server treated
   any non-blank as true (browser disagreed). Both evaluators now: empty or "false" = off,
   other non-empty = on (isTruthy helper Java / aligned getBooleanState TS; doc contract updated).
2. sync(): jsVariable logicIds no longer added to activeLogicIds → leftover logicsSrc weakref
   from a former field rule is cleaned as orphan (test syncRemovesStaleWeakrefWhenRuleBecameJsVariable).
3. fmdbext:rating: hidden autocreated minValue=1 (same `hidden` mechanic as fieldKey) so
   FieldValidator rejects forged sub-range values.
4. ScaleField: configuredNumber() helper honors explicit maxValue=0 (fallback only when
   absent/non-numeric).
5. FieldKeyAssignmentListener: one system session + one save per event batch.
165 engine tests green, tsc/eslint clean. Note: suppressed comments have no reply thread —
handled via a summary PR comment instead.

## UI polish session 2026-08-04 afternoon (HDU visual review on local Jahia)

`cb7bcb4` scale self-explanatory (view displayName spells Net Promoter Score, tooltip says
"clickable chips, not a slider"; issue #173 = native range input belongs in ELEMENTS, like #171).
`0b39d5c` themable max-width (superseded by:) `59e8d72` scale = auto-fit grid in a body wrapper
(fluid chips, labels aligned by construction). `5784f72` rating: own-drawn SVG mask stencils
(sources in Rating/icons/; thumb = OUTLINED hand + filled wrist per HDU taste, star/heart stay
filled — "semi-full is fine" HDU), number-chip hover fills whole chip, flex gap → in-item padding
(gap crossing reset the :hover fill-up every frame = flash). `380b23e` consent: statement is
plain (string,textarea) — renders in a <label>, phrasing-only — helpText first in CND, terms
link right after statement; spec 215 adjusted. All deployed+validated visually by HDU locally.
Resource bundles: 4 locales at full 67-key parity, tooltips restructured on the elements pattern
(Usage/Validation/Example/Note sections) — commits `ce31595`+`12ca032`.

## Branch state

```
99e5f37 feat: extended-inputs polish from code review
deba84e fix: rating renders in natural DOM order so keyboard arrows follow the visuals
a955c40 fix: switch buttons mode no longer prechecks the off state
009aa80 feat: server-side validation for number and boolean field kinds
af61635 test: field-level Cypress coverage for the extended-inputs fields
690d3de feat: extended-inputs fields opt in as conditional-logic sources
92cad84 merge: absorb feat/extended-inputs-module (PR #162) into the logic framework branch
77f945e feat: mixin-driven logic sources with number and boolean value kinds
675fbda test: adapt conditional-logic page object to the leading source-type dropdown
d49fc90 fix: process copied subtrees from their root in FormDuplicationCleanupListener
e46fff7 feat: stable fieldKey identity for form elements and sourceFieldKey rule resolution
08c9576 refactor: centralize conditional-logic source knowledge and generalize datalayer rules to jsVariable
1f5a47a merge: absorb feat/datalayer-conditional-logic (PR #156) into the logic framework branch
9c087b8 (base: origin/main at branch time)
```

Verification status (all on local docker Jahia, 2026-08-03):
- `mvn -pl formidable-engine test`: **159 tests, 0 failures** (29 evaluator incl. number/boolean,
  6 duplication listener incl. topmostPaths).
- tsc + vite green on formidable-engine, formidable-elements, formidable-extended-inputs; eslint
  clean on all touched files (incl. the previously pre-existing ConditionalLogicField error, fixed).
- Cypress **logics suite 50-54: 11/11 green** (spec 54 = editor + LIVE runtime with numeric proof).
- Manual validation of fieldKey scenarios done (see "session log 2026-08-03" below).

## What each commit contains

1. **`1f5a47a`** merge of PR #156 (datalayer rules, romain-pm) — superseded by this branch.
2. **`08c9576`** Phase 0 refactor: `sourceDescriptors.ts` single table; generic named-control DOM
   reader; datalayer → **jsVariable** rename (`sourceType: 'jsVariable'`, config key `variable`).
3. **`e46fff7`** fieldKey/sourceFieldKey identity (spec `.harness/spec-conditional-logic-logicid-sourcefieldkey.md`):
   hidden `fieldKey` UUID on `fmdbmix:formLogicElement`, `FieldKeys` + `FieldKeyAssignmentListener`,
   key-first resolution + backfill in `FormLogicSourceResolver`/`FormLogicSyncService`,
   same-form-copy remap, editor key-first, docs.
4. **`d49fc90`** fix from manual validation: `FormDuplicationCleanupListener` now aggregates each
   JCR event batch to its **topmost added paths** — JCR delivers one NODE_ADDED per copied node
   (children first), and per-leaf processing regenerated colliding fieldKeys one at a time so the
   copied rules were never rewritten (kept pointing at the original source). 2 unit tests.
5. **`675fbda`** Cypress page object adapted to the leading source-type dropdown
   (Field value / Datalayer value): named indices + openSourceDropdown/openOperatorDropdown/
   selectSourceType helpers; spec 50 counts +1.
6. **`77f945e`** **Phase 1 core** (issues #160/#125):
   - CND: `fmdbmix:numberField`, `fmdbmix:booleanField` (engine definitions.cnd, next to choiceField/dateField).
   - Editor: eligibility is mixin-driven — `FORM_TREE_BY_PATH` fetches 4 `isNodeType` flags;
     `sourceDescriptors.ts` = KIND_DEFAULTS (choice/date/number/boolean) + TYPE_OVERRIDES only for
     `fmdb:select` (options property) and `fmdb:checkbox` (choice-count operators);
     `getSourceDescriptor(type, valueKind)`.
   - Operators (BOTH evaluators, browser `conditionalLogic.ts` + Java `ConditionalLogicEvaluator`):
     number `eq/neq/lt/lte/gt/gte/between`, boolean `isTrue/isFalse` (server mirrors lone-checkbox
     submit-when-on). Numeric ops fail safe on non-numeric input.
   - Stored rule gains **`valueKind`** (denormalized): disambiguates 'between' (numeric vs ISO-string);
     legacy rules without it keep date semantics. Java record `ConditionalLogicRule` has the field;
     `FormLogicJsonEntry` preserves unknown JSON keys so no sync change needed.
   - Runtime escape hatch **`data-fmdb-logic-value`** (scalar or JSON array) read INSTEAD of native
     controls; `getBooleanState` = single-checkable checked OR values[0]==='true'.
   - Editor UI: `ScalarValueFields` (date|number input, 1 or 2 for between), boolean = no widget;
     locale keys `valueFrom`/`valueTo` (renamed from dateFrom/dateTo), 8 new operator labels (en+fr),
     generic help text; number/boolean rows in extension guide
     (`docs/how-to-extend-views-and-elements-from-third-party-module.md` new section
     "Make your field a conditional-logic source") + ConditionalLogic/README.md updated.
7. **`92cad84`** merge of PR #162 branch (`feat/extended-inputs-module`, romain-pm, single commit
   a9592db on an older main; conflict-free). Decision HDU 2026-08-03: #162 was MERGEABLE, CI green
   except Changelog check, but REVIEW_REQUIRED with zero reviews since 2026-07-21 — and its fields
   are the Phase 1 pilot. Same absorb pattern as #156.
8. **`690d3de`** pilot on the real fields: `fmdbext:rating`/`fmdbext:scale` + `fmdbmix:numberField`,
   `fmdbext:switch`/`fmdbext:consent` + `fmdbmix:booleanField` (one CND line each, zero JS).
   Cypress **spec 54**: editor discovery via mixins, operator sets, number widget, stored valueKind,
   weakref; LIVE runtime test (scale chip 10 satisfies gt "9" → numeric comparison proven since
   lexicographically "10" < "9"; switch toggle shows/hides dependent field; anti-flake: wait for
   `data-fmdb-logic-hidden` attr = hydration done before interacting; scale/switch inputs are
   sr-only → `check({force: true})`). `formidable-extended-inputs` added to
   `tests/provisioning-manifest-snapshot.yml` + `FORMIDABLE_MODULE_IDS` (constants.ts).

## Session log 2026-08-03 (manual validation, step 1 of previous snapshot)

All fieldKey scenarios validated live: auto-assignment at creation; legacy-rule backfill
(sourceFieldKey written + logicsSrc weakref named after logicId); rename + homonym trap (key wins
over uuid+name, sync REPAIRS a mis-pointed sourceNodeId); same-form fieldset duplication (bug found
→ fixed `d49fc90` → revalidated); export/import round-trip (document view carries
fieldKey/logics/logicsSrc, re-import rebinds to the imported form's own fields); jsVariable rules
stored verbatim, no weakref, survive import.

Observation (open review point): sync does NOT refresh a stale `sourceFieldName` (display/legacy
fallback only) — surface in PR review.

## Known deviations / open review points (for the PR body)

- Dropdown transient internal value = JCR UUID; PERSISTED identity is sourceFieldKey as specced.
- CND can't autocreate a random UUID → listener-based fieldKey assignment.
- No JS test infra in repo → no JS-side operator conformance table (vitest = team decision).
  Cypress spec 54's live test partially covers the browser evaluator.
- Stale `sourceFieldName` not refreshed by sync (see above).
- `between` uses valueKind for numeric vs date; legacy rules (no valueKind) = date semantics.
- Unanswered buttons-mode switch counts as isFalse (values empty) — document.

## Follow-ups created 2026-08-03 (extended-inputs review discussion with HDU)

- **Issue #170** — UI contract duplicated between formidable-elements and formidable-extended-inputs
  (HelpText + validationProps local copies, drift already visible). Direction discussed: private
  workspace package consumed via `workspace:*`, same mechanic as luxe-jahia-demo's
  `packages/design-system` (verified: private, no build, bundled by each consumer's vite).
  Deliberately NOT done in the framework PR to keep review focused. Whatever the outcome, the
  `data-fmdb-msg-*` / `help-<nodeId>` contract must stay documented for true third parties.
- **Issue #171** — no native number input field in formidable-elements (`fmdb:inputNumber` gap);
  would naturally carry `fmdbmix:numberField` once added.
- **Cypress specs 212-215** (fields family, after 211): rating, scale (incl. nps view), switch
  (toggle + buttons modes + defaultState), consent (statement richtext, required default,
  termsTarget link) — the field-level coverage #162 shipped without. Fixture factories in
  `tests/cypress/support/fixtures/extendedInputs.ts`. COMMITTED `af61635`, 10/10 green.
  Gotcha found: the test site's home page exists in LIVE but is NOT actually published
  (`/home.html` → 404 in live) — a consent termsTarget pointing at it resolves in preview but
  the LIVE render silently drops the link; spec 215 publishes home first
  (`publishAndWaitJobEnding(SITE_HOME_PATH)`).

## Code review of extended-inputs (2026-08-03, HDU request) — 3 majors FIXED in branch

- `009aa80` server-side number/boolean validation in FieldValidator (mixin-driven, minValue/maxValue
  bounds, "true"/"false" only) — was #162's own "main thing to decide before merge".
- `a955c40` switch buttons mode: OFF never prechecked (stored defaultState=false used to pre-answer
  "No", defeating required + biasing results).
- `deba84e` rating in natural DOM order (keyboard arrows used to run visually backwards);
  fill-up now uses :has(~ :checked) preceding-sibling selectors.
- Verified: 163 Java tests, fields 212-215 + full validation suite 26/26 green after redeploy.
- Minors ALSO fixed (`99e5f37`, per HDU): switch label fallbacks via resource bundle (not
  hardcoded English), consent helpText property + aria-describedby, edit-mode warnings
  (.fmdbext-edit-warning, translated en/fr) for: unresolvable termsTarget, rating maxValue
  clamp, scale truncation and step-skipped maxValue. Field specs re-run 11/11 green.
- ONLY remaining item from the review: HelpText/validationProps duplication → issue #170
  (deliberate follow-up, workspace-package direction).
- `2a42528` distinct content-type icons for the 4 fields (were byte-identical copies of
  fmdbmix_component): hand-drawn SVGs in the elements icon style (16x16 PNG, #111827 stroke,
  Lucide-inspired metaphors — star/gauge/toggle/shield-check), SVG sources kept alongside.
  Note: `settings/content-types-icons/` is packaged and served as the module's `icons/` folder.

## PR #163 interaction (flagged 2026-08-03, memorized)

#163 (romain-pm) = server-side **JS** field validators for extended inputs, STACKED on the now-closed
#162 branch, blocked on an UNRELEASED js-modules SDK (Jahia/javascript-modules#686). Overlaps
#172's `009aa80` (generic mixin-driven Java validation) but is stricter per-type (rating
integer-ness, scale step, consent must-be-true) and pluggable from JS. Complementary layers, not
mutually exclusive. After #172 merges, #163 needs: retarget/rebase onto main, conflicts in
FormFieldMetadataCollector (#172 added number/boolean flags + minNumber/maxNumber) and the CI
provisioning manifest (both add extended-inputs), then team decision: keep both layers
(recommended — engine baseline with zero deps + precise JS validators) or replace the generic one.

## Next steps (in order)

1. **Open the framework PR** (`feat/logic-framework` → main):
   - Push the branch (it has NEVER been pushed).
   - PR title conventional-commit lowercase; body per the global template (Description + Checklist).
   - The PR **supersedes BOTH #156 and #162** — close each with a pointer + credit to romain-pm
     (their commits are in this branch's history via the two merge commits).
   - chachalog entry referencing the NEW PR number (bot pushes on the PR branch — see
     [[chachalog-convention]] memory). Note: #162's Changelog check failed for the same reason.
2. **Phase 2**: "field is filled" — `fmdbmix:textField` on core text inputs, isEmpty/isNotEmpty in
   both evaluators. Open decision #4 (spec §4): all core text inputs at once makes the source
   dropdown much longer — consider a "show more" grouping.
3. Later (spec §3): provider seam hardening (event-based invalidation for jsVariable watcher,
   urlParam/cookie providers).

## Local deploy gotchas (validated this session)

- Engine jar: `mvn -pl formidable-engine package -DskipTests` then
  `curl -u root:root1234 -F 'bundle=@formidable-engine/target/formidable-engine-0.4.0-SNAPSHOT.jar' -F 'start=true' http://localhost:8080/modules/api/bundles`.
- JS modules (elements, extended-inputs): **same-version redeploy keeps serving the OLD bundle**
  (stop/start doesn't help). Use provisioning with `forceUpdate:true` (command in global CLAUDE.md,
  Build & deploy section) and VERIFY with a marker grep on the served file.
- `formidable-elements/.env` has stale `JAHIA_USER=root:root` → always override with
  `JAHIA_USER="root:root1234"`.
- Never run manual GraphQL scenarios on `FormidableSite4Tests` while Cypress runs (useFormidableSite
  recreates the site); use the `luxe` site and clean up after.

## Key files map (for quick re-entry)

- Editor: `formidable-engine/src/javascript/ConditionalLogic/{sourceDescriptors.ts, ConditionalLogic.utils.ts, ConditionalLogicCmp.tsx, ConditionalLogic.types.ts, graphql/queries.ts, README.md}`
- Runtime: `formidable-elements/src/utils/conditionalLogic.ts`, `src/hooks/useMultiStep.ts`, `src/components/FormContainer/LogicAwareRender.tsx`
- Java: `formidable-engine/src/main/java/.../logic/{FieldKeys, FieldKeyAssignmentListener, FormLogicSourceResolver, FormLogicSyncService, FormLogicJsonEntry, FormSourceFieldIndex, FormDuplicationCleanupListener, ConditionalLogicRule, ConditionalLogicEvaluator}.java`
- CND: engine `META-INF/definitions.cnd` (mixins), `formidable-extended-inputs/src/components/Input/*/definition.cnd` (pilot opt-ins)
- Tests: `tests/cypress/e2e/logics/50-54*.cy.ts`, `tests/cypress/page-object/ConditionalLogicField.ts`, `tests/cypress/support/fixtures/logics.ts`, `tests/provisioning-manifest-snapshot.yml`
- Docs: `docs/how-to-extend-views-and-elements-from-third-party-module.md`, `docs/conditional-logic-field-resolution.md`
- Specs: `.harness/spec-extensible-logic-framework.md` (§3bis = decisions log, updated 2026-08-03), `.harness/spec-conditional-logic-logicid-sourcefieldkey.md`
