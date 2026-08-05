# Formidable — Presentation Plan (5 slides)

Target: Google Slides deck (English, 5 slides max), generated as PPTX then uploaded to Google Drive with automatic conversion to Google Slides.

Sources: `README.md`, `CONTEXT.md`, `docs/` (architecture docs), CND definitions, `formidable-elements/package.json`, `formidable-engine` sources.

---

## Slide 1 — Why Formidable?

- The successor to Jahia Forms (Form Factory) — full replacement, no bespoke form engine
- Built 100% on Jahia standards:
  - Forms, steps, fieldsets and fields are regular **JCR content items**
  - Rendering via **Jahia JavaScript Modules** (npm/Vite based)
  - Actions are standard **Jahia/OSGi actions**
- Requirements: Jahia 8.2.2+, JavaScript Modules 1.2.0+, jContent 3.6+

## Slide 2 — What you get out of the box

- **12 field types**: text, textarea, email, select, checkbox, radio, date, datetime, file, color, hidden, rich text
- **Structure**: multi-step forms, fieldsets, reusable forms (form reference)
- **Conditional logic**: show/hide fields based on other fields' values
- **4 built-in actions**: save to JCR, email notification, email content, forward submission — plus CAPTCHA (Turnstile, hCaptcha, reCAPTCHA)
- **Results dashboard** in jContent: browse submissions, CSV/JSON export, private-by-default permissions (`fmdb-results-reader` role)

## Slide 3 — Architecture

- Two modules:
  - `formidable-elements` — front end: React 19 + TypeScript + Vite, SSR + client hydration via Islands
  - `formidable-engine` — Java/OSGi action pipeline + jContent editor extension (Module Federation)
- Content model in CND (`fmdb:` types, `fmdbmix:` mixins) — everything is JCR
- Submission flow: client POST → `formidableSubmit` action → OSGi `FormAction` pipeline → results stored in JCR
- Simple diagram: Contributor (jContent) → JCR → SSR render → visitor submit → action pipeline

## Slide 4 — Extensibility

- **Front**: override any field/container view from a third-party module (`jahiaComponent`), or create brand-new field types (CND + React server view + optional client Island)
- **Back**: custom actions by implementing the `FormAction` OSGi service (CND + `@Component` Java class)
- **Semantic mixins** (`emailField`, `fileField`, `choiceField`…) so the engine reacts to field semantics, not concrete types
- **Theming**: per-form custom CSS, stable `fmdb-` class system

## Slide 5 — Status & takeaways

- Roadmap: Alpha targeted ~June 2026; known gaps (no Page Builder visual editing yet)
- Key message: **"Forms as first-class Jahia content"** — standard tooling end to end, nothing proprietary to learn
- Optional call to action: try it, extend it, give feedback

---

## Delivery notes

- The Google Drive connector cannot populate a Slides deck slide by slide, but it converts an uploaded PPTX into Google Slides automatically.
- Plan: generate the deck with python-pptx, upload to Drive with conversion enabled → editable Google Slides presentation.
- Note: Formidable's front end is **React** (SSR + Islands), not Vue.js — "front views" refers to view overrides.
