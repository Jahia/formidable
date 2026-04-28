# AGENTS.md

## Project Overview

**Formidable** is a Jahia CMS form-builder delivered as a Yarn monorepo with three Maven sub-modules:

| Module | Role |
|---|---|
| `formidable-elements` | Front-end Jahia module – form rendering (server + client React) |
| `formidable-engine` | CMS editor extension – registers custom selector types via Module Federation |
| `jahia-test-module` | Java/JSP helper module for Cypress tests |
| `tests/` | Cypress E2E test suite (not a Maven module) |

## Architecture: Server / Client Split

Each component lives in its own folder under `formidable-elements/src/components/`. The naming convention determines how Vite bundles it:

- `[view].server.tsx` – rendered server-side by Jahia's JS modules runtime. Uses `jahiaComponent()` from `@jahia/javascript-modules-library`.
- `[Name].client.tsx` – interactive React component, hydrated in the browser via `<Island>`.

Example flow (`Form` component):

```
default.server.tsx          ← registers view for fmdb:form, reads JCR props
  └─ <Island component={Form} props={...}>
        └─ Form.client.tsx  ← client-side React, handles submission state
```

The `<Island>` boundary is the only place server → client props are serialised; keep props serialisable (no JCR node objects).

## CND Definitions

Every component folder contains a `definition.cnd` file declaring the JCR node type. The namespace prefixes are:
- `fmdb:` – concrete node types (e.g. `fmdb:form`, `fmdb:step`, `fmdb:inputText`)
- `fmdbmix:` – mixins (e.g. `fmdbmix:formElement`, `fmdbmix:formStep`)

A new field type must: (1) declare a node type extending relevant mixins in its `definition.cnd`, (2) provide a `default.server.tsx` view registered with `jahiaComponent()`, (3) use `currentNode.getName()` as the HTML `name` attribute and `input-${currentNode.getIdentifier()}` as the `id`.

Global shared type declarations live in `formidable-elements/settings/definitions.cnd`.

## CSS Conventions

All CSS class names use the `fmdb-` prefix (e.g. `fmdb-form`, `fmdb-form-group`, `fmdb-form-control`). Structural classes are plain strings; component-scoped overrides use CSS Modules (`*.module.css`) imported as `classes`. Both coexist on the same element:

```tsx
<div className={clsx("fmdb-form-group", classes.group)}>
```

Cypress tests target the `fmdb-` classes, so do not rename them.

## i18n

Client-side translations use `react-i18next` with namespace `formidable-elements` and key prefixes matching the node type (e.g. `fmdb_form`, `fmdb_inputCheckbox`). Translation files are in `formidable-elements/settings/locales/[lang].json`. Server-side labels come from JCR properties (e.g. `jcr:title`).

## Key Developer Commands

```bash
# Install all workspace dependencies (run from root)
yarn install

# formidable-elements – build once
cd formidable-elements && yarn build

# formidable-elements – watch mode (rebuild + redeploy on change)
cd formidable-elements && yarn dev

# Start local Jahia via Docker (from formidable-elements/)
docker compose up --wait

# Full Maven build (all modules, including JS build via exec-maven-plugin)
mvn clean install

# Run Cypress tests (Jahia must be running on localhost:8080)
cd tests && yarn e2e:ci        # headless
cd tests && yarn e2e:debug     # interactive

# Lint / format (from repo root, applies to the file's workspace)
yarn lint
yarn format
```

## Testing Patterns

Tests use a page-object model. Key files:
- `tests/cypress/page-object/Form.ts` and `Fieldset.ts` – top-level page objects
- `tests/cypress/page-object/elements/` – per-element wrappers
- `tests/cypress/support/fixtures/` – typed JCR node factories per element type

Each test creates JCR content via `addNode()`, navigates to a JContent preview iframe, and asserts against `fmdb-` CSS selectors. Disabled specs use the `.cy.ts.disabled` extension.

## formidable-engine

Registers editor UI extensions (e.g. `SelectOptions` custom selector) into the Jahia CMS editor via `@jahia/ui-extender`'s `registry`. Built with `@jahia/vite-federation-plugin` (Module Federation). Output goes to `src/main/resources/javascript/apps/`. The entry point is `src/javascript/init.ts`.

The module also contains the **server-side form action framework** (Java / OSGi):

### Form action pipeline

**Roles:**
- **Admin** – creates action nodes (e.g. `fmdb:captchaAction`, `fmdb:emailNotificationAction`) anywhere in the site content tree.
- **Contributor** – applies the `fmdbmix:actionPipeline` mixin to a `fmdb:form` node, then selects one or more action nodes via the `actions` weakreference-multiple property.

When the form has at least one referenced action (`hasProperty('actions')`), the server-side view computes a Jahia action URL:

```
/cms/render/live/{locale}{nodePath}.formidableSubmit.do
```

This URL is passed as `submitActionUrl` to the `Form` Island and used instead of `customTarget`.

The `FormSubmitAction` Java class resolves each weakreference, finds the matching `FormAction` OSGi service by primary node type, and executes them in order.

**Adding a new action type:**
1. Declare `[fmdb:myAction] > jnt:content, fmdbmix:formAction, mix:title` in `formidable-engine/src/main/resources/META-INF/definitions.cnd`.
2. Implement `FormAction` and annotate it `@Component(service = FormAction.class)`.
3. Return the node type string from `getNodeType()`.

**Built-in actions:**

| Node type | Java class | Description |
|---|---|---|
| `fmdb:captchaAction` | `CaptchaVerificationFormAction` | Verifies the token against Turnstile / hCaptcha / reCAPTCHA. Each widget auto-injects its native field (`cf-turnstile-response`, `h-captcha-response`, `g-recaptcha-response`) into the form DOM. |
| `fmdb:emailNotificationAction` | `SendEmailNotificationFormAction` | Sends an email via Jahia `MailService`; subject and body support `${fieldName}` interpolation |

Java sources live in `formidable-engine/src/main/java/org/jahia/modules/formidable/engine/actions/`.

## Toolchain Versions (see `mise.toml`)

- Java 17 (Temurin), Node LTS, Yarn 4, Maven 3
- Path alias `~` → `formidable-elements/src` (configured in both `vite.config.mjs` and `tsconfig.json`)
