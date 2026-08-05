# Plan: formidable-extended-inputs module (PR #162) + password field roadmap

Topic 3 of the 2026-07-31 roadmap. Status: **PR #162 open, review pending; password design direction proposed below.**

Issues/PRs: [PR #162](https://github.com/Jahia/formidable/pull/162) (implements [#158](https://github.com/Jahia/formidable/issues/158)), [#161](https://github.com/Jahia/formidable/issues/161) (password + sensitive contract + register-user action), [#159](https://github.com/Jahia/formidable/issues/159) (phone, separate), [#160](https://github.com/Jahia/formidable/issues/160) (logic sources — see `.harness/spec-extensible-logic-framework.md`).

## 1. State of PR #162 (`feat/extended-inputs-module`, branch on origin, verified 2026-07-31)

New monorepo sibling `formidable-extended-inputs` (pom packaging `pom`, `jahia-depends: formidable-elements,formidable-engine`), 4 field types, all `> jnt:content, fmdbmix:element, fmdbmix:validationMessages`, pure SSR (native HTML + CSS, no islands):

| Type | Value | Notes |
|---|---|---|
| `fmdbext:rating` | number 1..max | icon set star/heart/thumb/number, maxValue, end labels |
| `fmdbext:scale` (+ `nps` view) | number | min/max/step, end labels; NPS = view forcing 0-10 |
| `fmdbext:switch` | boolean | `toggle` (unchecked submits nothing) vs `buttons` (explicit false) |
| `fmdbext:consent` | `true` | rich-text statement, weakref termsTarget (page/file, must be guest-readable) |

Doubles as real-world validation of `docs/how-to-extend-views-and-elements-from-third-party-module.md`. SonarQube green. No reviews yet.

### Gaps to close before/around merge (from PR body + bot comments)

1. **Chachalog entry missing** (bot flagged) — add `.chachalog/*.md` referencing **the PR number (#162)**, `formidable: minor` (repo convention: entries reference the PR, not the issue).
2. **Engine-side semantic mixins / server validation** — submitted values are treated as free text server-side (no range/allowlist validation). PR body says this is "the main thing to decide before merge". Note: the semantic-mixin family designed in `spec-extensible-logic-framework.md` (`fmdbmix:numberField`, `fmdbmix:booleanField`) is EXACTLY what these fields need — one design should serve both server validation (#158 follow-up) and logic sources (#160). Recommendation: don't block #162 on it, but align the mixin names now so the CND doesn't churn.
3. **Content-type icons are placeholders** (copies of generic icon) — need real icons before release.
4. **No Cypress specs** — add under `tests/cypress/e2e/fields/` (2xx family; next free number after existing specs — check current max, was 210). Author offered to add in-PR.
5. Locales: editor EN/FR/DE/ES, front EN/FR — consistent with repo practice.

## 2. Password field (issue #161) — design direction

### Should password be a "standard" field (formidable-elements) or extended?

HDU's instinct ("password is a standard HTML input, shouldn't it be core?") is half right — split the answer by layer:

- **The input itself** (`<input type="password">`, eye toggle, confirm companion, strength constraints) IS HTML5-native, which matches formidable-elements' charter ("lean and HTML5-native"). The eye toggle needs a few lines of JS (island or inline) — the only non-pure-SSR bit.
- **BUT the field is unusable without the engine-side sensitive contract** (#161 is explicit): the value must never be persisted (Save to JCR), never appear in results/exports, never be emailed, stripped from forwards (like CAPTCHA tokens today) — while staying available in-pipeline for consuming actions.
- **AND its only built-in consumer is the "Register user" action**, which doesn't exist yet.

**Recommendation**: keep #161's delivery order —
1. `fmdbmix:sensitiveField` contract in **formidable-engine** (reusable beyond password: codes, IDs);
2. `fmdb:password` field in **formidable-elements** (it is native HTML; shipping it in extended-inputs would make a core-ish capability depend on an optional add-on, and the engine contract is the real dependency, not the widget);
3. "Register user" action in the engine (or a dedicated module if user-management deps are heavy).

Counter-argument for extended-inputs placement: a password field without the register-user action invites misuse (contributors collecting passwords into emails/JCR). The sensitive contract eliminates that risk by construction — which is another reason the contract MUST land first, wherever the widget lives.

### Sensitive contract — implementation touchpoints (from engine exploration)

- Submission pipeline: `formidable-engine/.../servlet/FormSubmissionPipeline.java` — where CAPTCHA-token stripping already happens; the contract generalizes that mechanism to any `fmdbmix:sensitiveField`-typed source field.
- Actions declare "consumes sensitive fields" (opt-in flag in the action contract) — default actions (save-to-JCR, email, forward) never see the value.
- Results screens/exports: nothing to do if the value is never persisted.

### Register user action — needs its own spec

Scope from #161: field mapping (username/email/password), site vs global users, default groups, duplicate-username behavior, optional email verification, server-side rejection of weak configs. Suggest a dedicated `.harness/spec-register-user-action.md` when we get there.

## 3. Suggested execution order across the 3 topics

1. **Now**: review/merge PR #162 (add chachalog entry, decide mixin-name alignment, optionally Cypress specs in-PR; icons can be a fast follow-up before release).
2. File the missing issue for topic 2 (choicelist-populated options) — none exists yet.
3. Land the semantic-mixin family (serves #158-follow-up server validation AND #160 logic sources) — see `spec-extensible-logic-framework.md` phasing.
4. Then #161 in its stated order (contract → field → action).
