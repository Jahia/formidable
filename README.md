# formidable-modules

## What is Formidable?
Formidable is the new solution to manage Forms with Jahia. It will fully replace Jahia Forms. It is currently in development.

## Main architecture principles
Formidable is based on Jahia standard technologies:
- Forms, steps, fieldsets and fields are regular content items
- Rendering / views use JavaScript modules, built in React / TSX
- Actions are declared in Java / OSGi, as per any other action inside Jahia
- The editor extension (custom selectors, form results admin) uses Module Federation via `@jahia/vite-federation-plugin`

## Prerequisites
- Jahia 8.2.2+
- JavaScript modules 1.2.0+
- jContent 3.6+

## Documentation

- [CND module ownership](docs/cnd-module-ownership.md) - where JCR types and mixins belong, and how to choose between `formidable-elements` and `formidable-engine`
- [Form submission flow](docs/form-submission-flow.md) - request lifecycle, pipeline steps, and server-side safeguards
- [CAPTCHA server-side validation](docs/captcha-server-side-validation.md) - provider verification endpoints and token handling
- [How to create a form action](docs/how-to-create-form-action.md) - step-by-step guide for implementing a custom `FormAction` OSGi service
- [How to extend views and elements from a third-party module](docs/how-to-extend-views-and-elements-from-third-party-module.md) - rendering contract for external container views and custom form elements
- [Save to JCR](docs/save-to-jcr.md) - how submissions and uploaded files are stored in JCR
- [Results permissions](docs/results-permissions.md) - per-form access control for submission results (`fmdb-results-reader` role)
- [Export](docs/export.md) - multi-format export architecture (CSV, JSON) with date range filtering
- [Custom validation](docs/custom-validation.md) - inline validation messages replacing native browser tooltips, with per-field contributor overrides
- [Conditional logic field resolution](docs/conditional-logic-field-resolution.md) - weakref-based model for conditional logic dependencies
- [Error codes](docs/error-codes.md) - server-side error codes returned on form submission failure
- [Dependency decisions](docs/dependency-decisions.md) - rationale for embedded vs. provided dependencies in Java modules

## Alpha version

### Scope

Alpha version is targeted for June 2026.
It will include the ability to:

- Create forms, with multi-step support, fieldsets, and 12 field types:
  - Text input (with optional pattern, mask, datalist)
  - Textarea (with resize, spellcheck, autocomplete options)
  - Email (with pattern, datalist, multiple recipients)
  - Select (dropdown, with multiple selection and size options)
  - Checkbox (single or group)
  - Radio (single or group)
  - Date (with min/max/step)
  - Datetime-local (with min/max/step)
  - File upload (with MIME type filtering, multiple files, image/video/PDF support)
  - Color
  - Hidden
  - Rich text (embeddable HTML content block)
- Create form references (reuse an existing form via weak reference)
- Conditional logic (show/hide fields based on other field values, using weakref-based dependency resolution)
- 4 built-in form actions:
  - Save to JCR (persists submissions and uploaded files under `formidable-results/`)
  - Send email notification (with `${fieldName}` interpolation in subject and body)
  - Send email content (sends the full submission by email, with optional file attachments)
  - Forward to external endpoint (multipart/form-data POST to operator-configured targets)
- CAPTCHA support (tested with Google reCAPTCHA, Cloudflare Turnstile and hCaptcha)
- Require authenticated user (reject anonymous submissions server-side)
- Server-side submission validation (field constraints, allowed MIME types, choice value allowlists, cross-origin request checks)
- Form results admin panel in jContent (only for forms using the Save to JCR action):
  - Browse saved submissions per form
  - Detail panel with metadata, field values, file cards with thumbnails and preview
  - Keyboard navigation (arrow keys)
  - Multi-format export (CSV, JSON) with date range filtering
  - Per-form access control via `fmdb-results-reader` role (results are private by default)
  - Submission deletion (admin-only in v1)
- Custom CSS injection per form
- Extension points:
  - Create your own action (implement `FormAction` OSGi service via `org.jahia.modules.formidable.engine.api`)
  - Overwrite the view for a field type
  - Create a new field type (CND + server view + optional client Island)

### Packaging
- 2 modules:
  - **formidable-elements** — provides the fields, form structure and rendering views
  - **formidable-engine** — provides the action framework (Java/OSGi), CAPTCHA verification, editor extensions (custom selectors, form results panel)

### Current known limitations
- When selecting a field, users don't know what it will look like. This pain point will be addressed globally inside Jahia in 2026.
- Forms are currently built using jContent list or structured view. There is no support for Page Builder / visual building. This issue should be resolved over the summer.
