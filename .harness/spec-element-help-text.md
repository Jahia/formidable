# Spec: contributor-authored rich text help on form fields

## Goal

Let contributors author, on each form field, a help message ("how to fill this
field…") displayed to the visitor below the field label, with basic formatting:
bold, italic, underline, lists, image, and links to pages/contents/images
(Jahia pickers).

- Branch: `feat/element-helpText` (from `main`) — PR: https://github.com/Jahia/formidable/pull/142
- Scope: the 10 visible field types — inputText, inputEmail, inputDate,
  inputDatetimeLocal, inputColor, inputFile, checkbox, radio, select, textarea.
  Excluded: inputHidden (invisible) and inputButton (not a fillable field).

## Decision history

1. **v1 (abandoned)**: `helpText (string) i18n` carried by the `fmdbmix:element`
   mixin, with a dedicated CE section "Help" (json-override
   `forms/fmdbmix_element.json`). Dropped after review: hduchesne prefers a
   property declared **per definition** (fine-grained control of which types
   offer it) and **rich text** content.
2. **v2 (implemented)**: property per definition, `richtext` type with a
   dedicated CKEditor config (restricted toolbar).
3. Toolbar iterations validated with hduchesne: started from the `light` preset
   minus undo/redo, heading, removeFormat (Tx), bookmark (flag), insertTable
   and indentation, plus `underline`; alignment was then removed as well.
4. No shipped CSS (keep it simple) — the block only carries the
   `fmdb-form-help` class for sites to style.
5. CE labels: EN "Input help" / FR "Aide à la saisie" (purpose-oriented wording
   in both languages, not artifact-naming).

## Implementation

### 1. CND — property per definition

First property of each of the 10 `definition.cnd`:

```cnd
 - helpText (string, richtext[ckeditor.toolbar='FormidableHelp',ckeditor.customConfig='$context/modules/formidable-engine/javascript/ckeditor/helpTextConfig.js']) i18n indexed=no
```

`fmdbmix:element` (settings/definitions.cnd) stays a property-less mixin.

### 2. CKEditor config — dual CKE4 / CKE5 support

**Key finding**: on instances without the `richtext-ckeditor5` module, Content
Editor renders richtext with **CKEditor 4**, where `ckeditor.customConfig` must
be a **JS config file URL** (`$context` supported — verified in
`content-editor/RichText.jsx:68-77`), not a config name. A CKE5 registry name
is silently ignored there → full default toolbar.

**a) CKEditor 4** —
`formidable-engine/src/main/resources/javascript/ckeditor/helpTextConfig.js`
defines a **named toolbar** (industrial-module pattern, also the Academy doc's
alternative to toolbarGroups/removeButtons — a whitelist beats maintaining a
~40-button removal list):

```js
CKEDITOR.editorConfig = function (config) {
    config.toolbar_FormidableHelp = [
        ['Bold', 'Italic', 'Underline'],
        ['Image'],
        ['Link', 'Unlink'],
        ['BulletedList', 'NumberedList']
    ];
};
```

The Jahia pickers stay wired by Content Editor itself
(`filebrowserLinkBrowseUrl` → `editoriallink` picker, image → image picker).

**b) CKEditor 5 (when the module is installed)** —
`formidable-engine/src/javascript/init.tsx`: the CKE5 component resolves the
same selector-option value as a **`ckeditor5-config` registry key** (raw-value
lookup, no `$context` replacement — verified in
`RichTextCKEditor5.jsx:70-137`). The engine therefore registers the equivalent
config **under the key equal to the URL**, at `jahiaApp-init:99.5` (after the
module's presets, to extend `light`):

```tsx
registry.add('ckeditor5-config',
    '$context/modules/formidable-engine/javascript/ckeditor/helpTextConfig.js', {
    ...registry.get('ckeditor5-config', 'light'),
    toolbar: {
        items: ['bold', 'italic', 'underline', '|', 'insertJahiaImage', 'link', '|', 'bulletedList', 'numberedList'],
        shouldNotGroupWhenFull: true
    },
    menuBar: {isVisible: false}
});
```

Verified in the `richtext-ckeditor5` sources: `builtinPlugins` is the union of
all registered plugin sets, so a custom config without `plugins` still gets
`JahiaLinkProvider` (internal page/content/file picker in the link balloon);
the toolbar alone decides what is exposed.

### 3. Content Editor labels — resource bundles

Per-type keys (×10, EN + FR), inserted after each type's `ui.tooltip`:

```properties
fmdb_inputText.helpText=Input help
fmdb_inputText.helpText.ui.tooltip=Guidance displayed below the field label to help users fill the field.<br/><b>Accessibility:</b> <i>Announced by screen readers via aria-describedby</i>.
```

(FR: "Aide à la saisie" + equivalent tooltip.) No "Formatting" line in the
tooltips — the toolbar already shows what is allowed.

### 4. Rendering — shared component + views

- `src/design/HelpText/HelpText.tsx`: renders the contributor HTML via
  `dangerouslySetInnerHTML` in a `<div id class="fmdb-form-help">` (same trust
  model as the `fmdb:richText` view). Exports `helpTextId(nodeId)` →
  `help-<uuid>`. Pure JSX with no server imports, so it is also used by the
  client checkbox island.
- 10 server views: `<HelpText id text>` between the `<label>` and the control,
  `aria-describedby={helpId}` on the control. Specifics:
  - Radio/Checkbox group: help after the `<legend>`, `aria-describedby` on the
    `<fieldset>` (recommended pattern, no per-input repetition); for Checkbox
    the fieldset is rendered by the `Checkbox.client.tsx` island
    (`helpText`/`helpId` props).
  - Radio/Checkbox standalone (1 choice): help after the choice label,
    describedby on the input.
  - File: the input lives in the `File.client.tsx` island → `describedBy` prop.
- Validation compatibility: `updateDescribedBy` (validationUtils.ts) merges
  tokens and only removes its own error id → the static help id survives the
  error/correction cycle.

### 5. URL placeholder resolution (island props)

**Bug found while testing** (pre-existing for the form intro): rich text values
serialized into island props escape the platform HTML-level URL rewriting
(href/src attribute traverser), so internal links/images kept their storage
placeholders (`/cms/{mode}/{lang}/…`, `/files/{workspace}/…`) in the props
JSON. Server-rendered helps were fine (rewritten by the platform).

Fix: `src/utils/richTextUtils.ts` → `resolveUrlPlaceholders(html, renderContext)`
applies the exact platform substitution
(`URLGenerator.getBasePlaceholders()→getBase()`, same for files) before
serialization. Applied to the Form island props (intro, submissionMessage,
errorMessage — the messages are client-side templates interpolated with
`${fieldName}`, so SSR-children was not an option for them) and to the checkbox
group island `helpText`. Trade-off: no vanity/SEO shortening on those values
(long-form `/cms/render/live/en/…` URLs, valid), and the util must follow if
Jahia ever adds new placeholder formats.

### 6. Cypress E2E coverage

- Test site created with `en,fr`; helpers: `visitLiveForm(livePath, lang)`,
  `visitPreviewForm(livePath, lang)` (render/default servlet, authenticated),
  `createPublishedLiveFormPage` options `pageProperties` + `publishLanguages`.
- Page objects: `getHelpText()` / `shouldHaveHelpText(text)` (visibility + text
  + aria-describedby wiring) / `shouldNotHaveHelpText()` on `FormElement`,
  `RadioGroup`, `CheckboxGroup`.
- `fields/210-help-text.cy.ts` (numbering: 2xx family of the fields folder,
  20-29 exhausted):
  1. rendering + a11y wiring for text/select + radio/checkbox group variants,
     absence of the block when the property is empty, HTML rendering
     (`<strong>` asserted);
  2. internal page link + image (cats.jpg uploaded and published) in the help
     of a **checkbox group** (island-props path) and of a **text input**
     (server-rendered path), asserted in live and preview, in EN and FR:
     resolved href/src (no `{mode}`/`{lang}`/`{workspace}`/`##`), target
     served (200, `retryOnStatusCodeFailure` absorbs publication latency),
     no placeholder leak in the raw page source (covers the props JSON);
  3. help + validation-error coexistence in `aria-describedby`.
- `fields/20-all-field-types.cy.ts`: multilingual form intro with resolved
  internal link and image, live and preview (the intro is a form-level
  feature, so it belongs to the form spec — distribution agreed with
  hduchesne). The site home gets a FR title and an explicit publication (its
  live URL is a link target).
- `tests/scenarios/fields.md` updated.

### 7. Out of scope / verified non-impacts

- **formidable-engine (server)**: no impact — helpText is not submitted, no
  server validation, no conditional-logic interaction.
- **Existing content**: property absent → nothing rendered.
- Help HTML is not re-sanitized at render time (parity with `fmdb:richText`);
  the contribution surface is bounded by the restricted CKEditor toolbar.

## Status

- Commits: `93d3033` (autocomplete labels fix), `652a906` (placeholder
  resolution fix), `425b3c8` (helpText feature) — PR #142.
- Verified: elements/engine/tests typecheck + lint + build; spec 210 green
  (3/3) against the local instance; spec 20 intro test green after the home
  publication fix (last multilingual+preview iteration validated by hduchesne's
  run).
