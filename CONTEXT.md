# Formidable – Full context for AI session restoration

## Monorepo structure

Yarn monorepo + Maven multi-module. Root: `/formidable-modules/`.

| Module | Role |
|---|---|
| `formidable-elements/` | Jahia front-end module – form rendering (React SSR + client hydration) |
| `formidable-engine/` | Jahia editor extension + Java/OSGi action pipeline |
| `jahia-test-module/` | Java/JSP helper module for Cypress tests |
| `tests/` | Cypress E2E suite (not a Maven module) |

Toolchain: Java 17 (Temurin), Node LTS, Yarn 4, Maven 3 (see `mise.toml`).

---

## formidable-elements – Front-end architecture

### Server / client convention

Every component lives in `formidable-elements/src/components/<ComponentName>/`.

- `[view].server.tsx` → rendered server-side by Jahia via `jahiaComponent()` from `@jahia/javascript-modules-library`
- `[Name].client.tsx` → interactive React component, hydrated via `<Island>`

The `<Island>` boundary is the only place where props cross from server to client. **Props must be serialisable** (no JCR node objects).

### Form flow (canonical example)

```
default.server.tsx        ← jahiaComponent() for fmdb:form, reads JCR props
  └─ <Island component={Form} props={...}>
        └─ Form.client.tsx  ← React, handles submission, multi-step, captcha
              └─ Captcha.client.tsx  ← captcha widget (Turnstile / hCaptcha / reCAPTCHA)
```

### Key files – Form

| File | Role |
|---|---|
| `src/components/Form/default.server.tsx` | Computes `submitActionUrl`, `captcha`, `stepLabels`; passes everything to the Island |
| `src/components/Form/Form.client.tsx` | `fetch` submission, multi-step, DOMPurify, CAPTCHA guard |
| `src/components/Form/Captcha.client.tsx` | Renders the captcha widget via the provider's native API |
| `src/components/Form/types.ts` | `FormServerProps`, `FormProps`, `CaptchaProvider` |
| `src/components/Form/definition.cnd` | Mixins `fmdbmix:responses`, `fmdbmix:buttons`, `fmdbmix:multiStep`, `fmdbmix:captcha`, `fmdbmix:actionPipeline`, and the `fmdb:form` type |

### Vite

- Build: `@jahia/vite-plugin`, input globs `**/*.server.tsx` + `**/*.client.tsx`
- Alias `~` → `formidable-elements/src` (in both `vite.config.mjs` and `tsconfig.json`)
- Output: `dist/`; deployed to Jahia in watch mode with `yarn dev`

### CSS

All class names use the `fmdb-` prefix (e.g. `fmdb-form`, `fmdb-form-group`, `fmdb-form-control`).
Structural classes = plain strings; scoped overrides = CSS Modules (`*.module.css`) imported as `classes`.
Both coexist on the same element:

```tsx
<div className={clsx("fmdb-form-group", classes.group)}>
```

**Never rename `fmdb-` classes**: Cypress tests target them directly.

### i18n

`react-i18next`, namespace `formidable-elements`, key prefix = node type name (e.g. `fmdb_form`, `fmdb_inputCheckbox`).
Translation files: `formidable-elements/settings/locales/en.json` and `fr.json`.
Server-side labels come from JCR properties (`jcr:title`).

---

## CND – Type declaration conventions

Namespaces:
- `fmdb:` – concrete types (`fmdb:form`, `fmdb:step`, `fmdb:inputText`, …)
- `fmdbmix:` – mixins (`fmdbmix:formElement`, `fmdbmix:formStep`, `fmdbmix:formAction`, …)

Shared global types: `formidable-elements/settings/definitions.cnd`
Component-specific types: `formidable-elements/src/components/<ComponentName>/definition.cnd`
Action engine types: `formidable-engine/src/main/resources/META-INF/definitions.cnd`

### Rules for a new field type

1. Declare `[fmdb:myField] > jnt:content, fmdbmix:element` in its `definition.cnd`
2. Create `default.server.tsx` with `jahiaComponent({ componentType: "view", nodeType: "fmdb:myField", name: "default" }, ...)`
3. HTML `name` = `currentNode.getName()`; HTML `id` = `input-${currentNode.getIdentifier()}`

### Mixin hierarchy (formidable-elements)

```
fmdbmix:formElement (> mix:title, orderable)
  └─ fmdbmix:element         ← adds + * (fmdb:elementValidation) for validation nodes
fmdbmix:formContent          ← non-field content embeddable in a form
fmdbmix:formStep             ← step marker
fmdbmix:component            ← makes a type visible/droppable in the editor
```

### Core form types

```cnd
[fmdb:form] > jnt:content, fmdbmix:component, jmix:visibleInContentTree, mix:title
  + * (fmdbmix:formElement)
  + * (fmdbmix:formContent)
  + * (fmdbmix:formStep)

[fmdb:step] > jnt:content, fmdbmix:formStep, mix:title
  orderable
  + * (fmdbmix:formElement)
  + * (fmdbmix:formContent)

[fmdb:captchaProvider] > jnt:content, fmdbmix:component, mix:title
  - siteKey (string) indexed=no
  - scriptUrl (string) = 'https://challenges.cloudflare.com/turnstile/v0/api.js' autocreated indexed=no
```

---

## Captcha – Full integration

### Front-end

- `fmdbmix:captcha` mixin on `fmdb:form` → `captchaConfig (weakreference) < fmdb:captchaProvider`
- `default.server.tsx` reads `siteKey` and `scriptUrl` from the `fmdb:captchaProvider` node
- The provider is derived from `scriptUrl` (domain-based heuristic)
- The captcha script is injected server-side via `<AddResources type="javascript" ... defer/>`
- Turnstile requires `render=explicit` in the script URL (added automatically by `ensureCaptchaExplicit`)
- `Captcha.client.tsx` renders the widget into a `<div ref>` and exposes `getToken()` / `reset()` via `useImperativeHandle`
- Each widget auto-injects its token into the DOM under its native field name — **`FormData` picks it up automatically, no manual injection needed**

### Native token field names

| Provider | Field name in POST |
|---|---|
| Cloudflare Turnstile | `cf-turnstile-response` |
| hCaptcha | `h-captcha-response` |
| Google reCAPTCHA v2 | `g-recaptcha-response` |

### Server-side

`CaptchaVerificationFormAction` reads the field matching the provider from the POST parameters and calls the provider's `siteverify` endpoint.

---

## formidable-engine – Action pipeline

### Overview

When the `fmdbmix:actionPipeline` mixin is applied to a `fmdb:form`, the contributor selects action nodes via the `actions (weakreference, contentpicker[fmdbmix:formAction]) multiple` property.

`default.server.tsx` detects `hasProperty('actions')` and computes:
```
submitActionUrl = /cms/render/live/{locale}{nodePath}.formidableSubmit.do
```
This URL is passed to the Island instead of `customTarget`.

`FormSubmitAction` (Jahia `Action`, OSGi):
1. Reads the `actions` weak-references from the form node
2. For each referenced node, finds the OSGi `FormAction` service whose `getNodeType()` matches the node's primary type
3. Calls `handler.execute(actionNode, req, renderContext, session, parameters)` in order
4. On `FormActionException`, returns the exception's HTTP status to the client

### Java class layout

```
org.jahia.modules.formidable.engine.actions
├── FormAction.java                      ← strategy interface (getNodeType + execute)
├── FormActionException.java             ← exception carrying an httpStatus
├── FormSubmitAction.java                ← Jahia Action OSGi, orchestrates the pipeline
├── CaptchaVerificationFormAction.java
└── SendEmailFormAction.java
```

### `FormAction` interface

```java
public interface FormAction {
    String getNodeType();    // e.g. "fmdb:captchaAction"
    void execute(
        JCRNodeWrapper actionNode,
        HttpServletRequest req,
        RenderContext renderContext,
        JCRSessionWrapper session,
        Map<String, List<String>> parameters
    ) throws FormActionException;
}
```

### `FormActionException`

```java
FormActionException.badRequest("message");    // HTTP 400
FormActionException.serverError("message");   // HTTP 500
```

### `FormSubmitAction`

- Jahia action name: `formidableSubmit` (`setName("formidableSubmit")`)
- `setRequireAuthenticatedUser(false)` + `setRequireValidSession(false)`
- Collects `FormAction` OSGi services via `@Reference(cardinality=MULTIPLE, policy=DYNAMIC)`
- Uses `CopyOnWriteArrayList` for thread-safety

### Built-in actions

#### `fmdb:captchaAction` → `CaptchaVerificationFormAction`

CND:
```cnd
[fmdb:captchaAction] > jnt:content, fmdbmix:formAction, mix:title
 - provider (string) = 'turnstile' autocreated indexed=no < 'turnstile', 'hcaptcha', 'recaptcha_v2'
 - secretKey (string) indexed=no
```

Behaviour: reads the token from the provider's native field, calls `siteverify` via POST `application/x-www-form-urlencoded` with `secret`, `response`, `remoteip`. Checks `result.success`.

#### `fmdb:emailAction` → `SendEmailFormAction`

CND:
```cnd
[fmdb:emailAction] > jnt:content, fmdbmix:formAction, mix:title
 - to (string) indexed=no
 - from (string) indexed=no
 - subject (string) i18n indexed=no
 - templateMessage (string, textarea) i18n indexed=no
```

Behaviour:
- `${fieldName}` interpolation in `to`, `subject`, `templateMessage` from form parameters
- Requires Jahia `MailService` configured (SMTP in Jahia admin)
- Call: `mailService.sendMessage(from, to, null, null, subject, null, htmlBody)`
  → signature: `sendMessage(from, to, cc, bcc, subject, textBody, htmlBody)`
  → **Do NOT use** `new MailMessage()` + `mailService.sendMessage(message)` (incorrect API)
- Injected via `@Reference(cardinality=OPTIONAL, policy=DYNAMIC)`

### Adding a new action type

1. Add to `formidable-engine/src/main/resources/META-INF/definitions.cnd`:
   ```cnd
   [fmdb:myAction] > jnt:content, fmdbmix:formAction, mix:title
    - myProp (string) indexed=no
   ```
2. Create the Java class:
   ```java
   @Component(service = FormAction.class)
   public class MyFormAction implements FormAction {
       @Override public String getNodeType() { return "fmdb:myAction"; }
       @Override public void execute(...) throws FormActionException { ... }
   }
   ```
3. Read configuration properties from `actionNode` with `node.hasProperty(name) ? node.getProperty(name).getString() : null`

### `fmdbmix:formAction` mixin

```cnd
[fmdbmix:formAction] mixin
```
Pure marker — no properties. Enables the editor `contentpicker` to list all available action nodes across the site.

---

## formidable-engine – Editor extension (JS)

Entry point: `src/javascript/init.ts`
Registers extensions into the Jahia registry (`@jahia/ui-extender`):
- `SelectOptions`: custom selectorType for list-field options

Build: `@jahia/vite-federation-plugin` (Module Federation), output in `src/main/resources/javascript/apps/`.

---

## User roles

| Role | Action |
|---|---|
| **Admin** | Creates action nodes (`fmdb:captchaAction`, `fmdb:emailAction`, …) anywhere in the site content tree |
| **Contributor** | Creates a `fmdb:form`, applies `fmdbmix:actionPipeline`, selects actions via the `actions` property |

---

## Developer commands

```bash
# Install dependencies (from repo root)
yarn install

# Front-end build (once)
cd formidable-elements && yarn build

# Watch mode (rebuild + auto-redeploy)
cd formidable-elements && yarn dev

# Start local Jahia via Docker
cd formidable-elements && docker compose up --wait

# Full Maven build
mvn clean install

# Cypress tests (Jahia must be running on localhost:8080)
cd tests && yarn e2e:ci      # headless
cd tests && yarn e2e:debug   # interactive

# Lint / format
yarn lint
yarn format
```

---

## Cypress tests

Page-object pattern:
- `tests/cypress/page-object/Form.ts` and `Fieldset.ts` – top-level page objects
- `tests/cypress/page-object/elements/` – per-element wrappers
- `tests/cypress/support/fixtures/` – typed JCR node factories per element type

Each test creates JCR content via `addNode()`, navigates into the JContent preview iframe, and asserts against `fmdb-` CSS selectors.
Disabled specs use the `.cy.ts.disabled` extension.

---

## Known pitfalls / technical decisions

- **`MailMessage` + `setHtml` / `setHtmlBody`**: do not use. Correct API is `mailService.sendMessage(from, to, cc, bcc, subject, textBody, htmlBody)`.
- **Captcha token**: each widget auto-injects its hidden field into the DOM. `FormData` captures it automatically. On the Java side, read the provider's native field name (`cf-turnstile-response`, etc.) — never invent a field like `fmdb-captcha-token`.
- **`submitActionUrl` vs `customTarget`**: if `fmdbmix:actionPipeline` is active AND `actions` is non-empty, `submitActionUrl` takes priority in `Form.client.tsx`.
- **Turnstile `render=explicit`**: required, otherwise the widget renders automatically and ignores `containerRef`. Added by `ensureCaptchaExplicit` in `default.server.tsx`.
- **Island props must be serialisable**: never pass a `JCRNodeWrapper` to an Island. Extract scalars server-side with `getNodeProps()`.
