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
- 4 built-in form actions:
  - Save to JCR (persists submissions and uploaded files under `formidable-results/`)
  - Send email notification (with `${fieldName}` interpolation in subject and body)
  - Send email content (sends the full submission by email, with optional file attachments)
  - Forward to external endpoint (multipart/form-data POST to operator-configured targets)
- CAPTCHA support (tested with Google reCAPTCHA, Cloudflare Turnstile and hCaptcha)
- Require authenticated user (reject anonymous submissions server-side)
- Form results admin panel in jContent (only for forms using the Save to JCR action):
  - Browse saved submissions per form
  - Detail panel with metadata, field values, file cards with thumbnails and preview
  - Keyboard navigation (arrow keys)
  - Multi-format export (CSV, JSON) with date range filtering
- Custom CSS injection per form
- Extension points:
  - Create your own action (implement `FormAction` OSGi service)
  - Overwrite the view for a field type
  - Create a new field type (CND + server view + optional client Island)

### Packaging
- 2 modules:
  - **formidable-elements** — provides the fields, form structure and rendering views
  - **formidable-engine** — provides the action framework (Java/OSGi), CAPTCHA verification, editor extensions (custom selectors, form results panel)

### Current known limitations
- When selecting a field, users don't know what it will look like. This pain point will be addressed globally inside Jahia in 2026.
- Forms are currently built using jContent list or structured view. There is no support for Page Builder / visual building. This issue should be resolved over the summer.
