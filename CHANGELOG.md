# formidable Changelog

## 0.4.0

### New Features

* Added a ready-made title to every new form action (#169)

* Added a New content button to every step and field group in the Page Builder, so an empty one can be filled (#230)

* Improved form authoring with colour-coded zones and icons telling steps, fields and contents apart (#230)

* Added a translatable title to the field and action lists of a form, shown in the Page Builder (#231)

* Added live input-mask guidance while typing in text fields (#166)

* Added contributor-authored rich text help under form field labels (#142)

* Improved the select option rows with clearer placeholders and hover hints (#168)

* Changed source-based option settings to be required: a choice field must name the source its options mode needs (#196)

  **Breaking change.** You are affected if you create or import forms programmatically with a source-based options mode: the field can no longer be saved without the settings that mode needs — the source for a declared source, the root category for category options, the root node and content type for content options. The list of manually typed options is NOT required. In the editor nothing changes beyond the standard required indicators; existing forms are not modified, but the next edit of an incomplete field asks for the missing values.

* Added choice-field options filled live from admin-declared sources or categories (#193)

* Changed choice-field option storage to one shared format; existing forms are converted automatically at startup (#193)

  **Breaking change.** You are affected if you built custom views, queries or integrations that read a choice field's option list directly from its stored properties: selects, radios and checkboxes previously each stored their options under their own property, and they now all share a single one. Regular forms need no action: the conversion runs once when the new version starts, in both edit and live, and published forms keep rendering without a republish.

  One case needs attention: a form export made with version 0.3.0 or earlier and imported into an instance already running this version shows empty option lists on its choice fields until the server restarts, which re-runs the conversion. Restart after such an import, or re-save the options in the editor.

* Improved the form preview in jContent: every step and conditional field is shown, instead of a frozen first step (#233)

* Added choice-field options filled live from the site contents under a picked root (#196)

  The submitted value is the content path relative to the picked root, and the list is capped by a configurable limit: above it the field reports an error instead of silently truncating the options. Note that publishing the form also publishes the picked root and the contents under it; mark drafts as work in progress to keep them out.

* Added support for array entries in datalayer visibility rules (for example dataLayer.0.event) (#193)

* Added browser variables (such as datalayer entries) as conditional logic sources (#172)

* Added date and datetime bounds that follow the submission day, e.g. no birth date in the future (#202)

  **Upgrade impact.** You are affected if your forms use minimum or maximum dates: each bound is now a choice between a fixed date and the current date, and existing fixed bounds are converted automatically when the new version starts, in both edit and live — published forms keep rendering without a republish. Follow the documented installation order (see the upgrade notes), as for every upgrade that changes content definitions.

  One case needs attention: a form export made with version 0.3.0 or earlier and imported into an instance already running this version keeps its date bounds enforced when the form is submitted, but the date pickers and the editor do not show them until the server restarts, which re-runs the conversion. Restart after such an import, or re-select the bounds in the editor.

* Added date bounds at an offset from the submission day, e.g. age limits or booking windows (#210)

* Added the German and Spanish translations of every form element and form action label (#251)

* Added optional rating, scale, switch and consent fields, logic-ready and validated server-side (#172)

* Added numeric and boolean logic operators, with rules that survive renames, copies and imports (#172)

* Changed the editing of multi-step forms: every step is shown at once while authoring, with no step navigation (#230)

* Added a number field with spinner, minimum/maximum/step constraints and range validation messages (#175)

* Added a slider field with range bounds, end labels and tick marks, counted as answered on interaction (#176)

* Hardened form submissions by rejecting values for fields proven hidden by their display conditions (#183)

* Added URL parameters and cookies as conditions for showing or hiding a form field (#182)

* Added a style hook on fields driven by conditional logic, so template sets can flag them, notably in edit mode (#227)

* Added text, textarea and email fields as conditional logic sources (filled, empty, equals, contains) (#174)

* Added conditional logic date criteria that compare against the submission day instead of a fixed date (#205)

* Changed the form rendering module's identity; upgrading from 0.3.0 or earlier requires a one-time reinstall (#187)

  **Breaking change.** You are affected if any version up to 0.3.0 of the form rendering module is installed: uploading the new version fails with "Module upload failed because another module formidable-elements exists."

  Earlier versions were packaged under a placeholder group id; the module is now published under the official Jahia group id, as required for distribution on the Jahia App Store. Jahia treats a module with the same name but a different group id as a different module and blocks the upload.

  To upgrade, in **Administration > Server > Modules and Extensions > Modules**:

  1. Upgrade formidable-engine to the new version **first**, **with "Validate module definitions" unticked**: the new version removes submission properties no version since 0.2.0 has written, so the validation rejects the upload as a major definition change. This is expected — values stored by a 0.1.x instance are left in place.
  2. Stop and uninstall the old formidable-elements. **Do not tick the option to delete the module content when uninstalling** — that choice erases every form and every submission stored in the repository. Left unticked, forms and submissions are fully preserved; forms simply stop rendering while the module is absent.
  3. Install the new formidable-elements, **with "Validate module definitions" unticked**: the new version reorganizes some field properties, so the validation rejects the upload as a major definition change. This is expected — the automatic content migrations take over for the existing content.
  4. **Re-enable formidable-elements on every site that uses it**: the uninstall removed it from the sites' enabled modules, and forms show a "Module error" box until it is enabled again. A server restart does not repair this; re-enabling the module does, immediately.
  5. Check that forms render again. Existing content is migrated automatically; the migrated fields may show as *modified* (pending publication) afterwards — nothing is actually pending, publishing them is optional and only clears the flag.

  This is a one-time procedure: later upgrades install in place as usual. The full walkthrough, with the exact messages to expect, is in [docs/upgrade-notes.md](https://github.com/Jahia/formidable/blob/main/docs/upgrade-notes.md).

* Added a maintenance message on forms that store submissions while the platform is in read-only mode (#198)

  The message is editable per form and per language in the Response Messages tab, pre-filled with a translated default.

* Added translated default success and error messages, prefilled on newly created forms (#198)

* Added a configurable empty option label on select fields, so they start empty instead of preselecting a value (#196)

  Single-choice selects only: the option is not rendered on multiple selects. Starting empty makes the required validation of the field effective in the browser.

### Bug Fixes

* Changed failed submissions to answer with the status the failing action reported instead of a generic one (#271)

* Hardened submissions: a form declaring no field no longer stores arbitrary posted values (#242)

* Improved memory use when sending large form attachments by email (#249)

* Fixed form submissions being rejected for logged-in visitors by the Jahia CSRF protection (#200)

  Affects platforms running jahia-csrf-guard 4.3 and later: authenticated visitors received an error on every form submission, while anonymous visitors were unaffected. Cross-site submissions remain rejected by the built-in origin check.

* Improved the upgrade: sites that lost the form rendering module get it re-enabled automatically (#274)

* Fixed the CAPTCHA checkbox sometimes not appearing when the provider script takes time to load (#191)

* Hardened the automatic conversion of choice options so one broken field no longer blocks the others (#208)

* Fixed conditions on an enclosing section being skipped when a field shares its section's name (#268)

* Fixed submissions failing with a server error when a field shares its name with the section holding it (#262)

* Fixed the contributor guide's outdated description of how form field types are structured (#209)

* Fixed the security guide overstating the protections applied to submissions from logged-in visitors (#269)

* Changed the custom CSS field of a form to advise styling from the site instead, and fixed two wrong hints (#217)

* Secured the results CSV export against spreadsheet formula injection from submitted values (#241)

* Fixed custom form styles being dropped when a rule used quotes, '>' or an attribute selector (#265)

* Improved diagnostics: a condition whose source field was deleted is now reported in the server logs (#274)

* Fixed the upgrade guide and developer documentation drifting from the released behavior (#270)

* Fixed a display glitch when a form intro or message contains rich text (#247)

* Removed the unused submission metadata definitions (IP address, user agent, username); old stored values are kept (#244)

* Fixed a newly created fieldset offering no way to add fields from the Page Builder (#236)

* Improved failed submissions: the form now stays on screen with its values so visitors can simply retry (#263)

* Fixed form elements leaving a stray identity value on each translation, which made content-integrity scans fail (#215)

* Improved the content list offered inside a form: fields are grouped together, apart from steps and blocks (#216)

* Fixed the Page Builder create buttons of a form, which showed technical names for field, content and step types (#227)

* Fixed removing a form from a page: deleting now removes the reference, and Go to source opens the form (#234)

* Fixed conditions and labels being altered on native fields that reuse the migration's value-realignment path (#279)

* Fixed submissions being rejected after a section was hidden while a field inside it had its condition met (#263)

* Fixed forms refusing to send while a required choice field was hidden by conditional logic (#275)

* Removed a misleading unused dependency declaration from the form modules' build files (#272)

* Fixed fields hidden by a visibility rule staying editable: rules no longer apply while a form is being edited (#218)

* Improved the conditional logic editor: aligned fields, searchable selectors, errors shown on the faulty rule (#193)

* Changed rule saving so a visibility rule whose target was never chosen is removed instead of hiding the field (#193)

  Rules whose reference is filled but invalid are kept and shown in error in the editor.

* Changed the maintenance message to replace the form: nothing can be retried while the platform is read-only (#268)

* Fixed choice option values diverging between languages: every language now shares the default language's set (#211)

  Options are authored in the site's default language; every other language receives that list and translates only the labels. The options list is no longer required, so visiting another language never blocks saving a form field.

* Fixed dates shown one day early in confirmation messages for visitors in timezones west of UTC (#265)

* Fixed numeric-looking answers such as postal codes being reformatted in confirmation messages (#265)

* Fixed a field upgraded from an earlier version ignoring every later publication on the live site (#282)

* Fixed an upgrade issue where option lists translated before the upgrade lost their labels in other languages (#261)

* Fixed choice options and date bounds of 0.3-era forms staying unmigrated after the upgrade (#240)

* Fixed multi-step forms briefly showing the wrong buttons before the page finished loading (#263)

* Fixed conditional logic ignoring all but the innermost condition when sections with conditions are nested (#262)

* Changed the Page Builder boxes: field groups share one colour, the field list keeps the default look (#235)

* Hardened submissions: uploads are only accepted on file fields, and only forms accept submissions (#243)

* Fixed a harmless error logged at startup during an upgrade before all form modules were updated (#261)

* Fixed slider values losing their localized formatting in confirmation messages (#268)

* Fixed the upgrade guide to cover the definition warning shown when updating from older versions (#259)

* Improved the automatic module re-enabling to run once per site, so deliberately disabling it afterwards sticks (#276)

* Fixed access to form results: granting or revoking the reader role now takes effect after publication (#185)

* Fixed two simultaneous first submissions splitting a form's results in two (#246)

* Improved the results screen and exports: submission values follow the order of the fields in the form (#224)

* Improved the reliability of the built-in results reader role titles (#248)

* Fixed conditions showing an empty value in languages whose migrated option lists still diverge (#277)

* Fixed conditions written before the upgrade never matching again once their option values were unified (#278)

* Fixed accented characters showing as garbled text in the sample options source labels (#211)

* Improved help texts and validation messages so they behave consistently across all field types (#178)

* Fixed snapshot builds presenting themselves as the final release, which blocked installing the actual release (#260)

* Improved the custom options-source guidance: a static localized list replaces the repository-reading example (#203)

* Fixed hiding the steps indicator also disabling step-by-step display and validation (#263)

* Fixed step titles disappearing everywhere when the steps navigation option is turned off (#268)

* Fixed translated option labels being lost when a migrated choice field was first saved after the upgrade (#278)

* Secured file type detection by upgrading an embedded third-party library flagged by vulnerability scanners (#260)

* Improved the upgrade guide with the recovery path for modules installed in the wrong order (#273)

## 0.3.0

### New Features

* Added automated integrity checks and regression tests to detect corrupted Formidable form content and submissions.

* Changed form validation to show inline error messages instead of native browser tooltips (#115).

### Bug Fixes

* Conditional logic source resolution around sourceNodeId (#124)

## 0.2.0

### Breaking Changes

* Implement weakref model for conditional logic field resolution (#73)

* Clarified form type ownership and updated server-side semantics for containers, CAPTCHA, and authentication. (#86)

* Conditional step visibility in multi-step forms (#62)

* Conditional required field verification based on conditional rule (#62)

### New Features

* Hardened form CAPTCHA handling by validating verification endpoints and moving CAPTCHA tokens from URL parameters to secure request headers.

* Changed the public Java package for custom form actions to `org.jahia.modules.formidable.engine.api`. If you have custom form action implementations that import from the previous package, update your imports to use the new package and recompile your module.

* Add results permissions and delete action (#76)

### Bug Fixes

* Fixed form submissions so authentication and CAPTCHA can't be bypassed during repository errors (#79)

* Updated documentation based on review feedback.

* Improved form submission error handling and documented operational limits.

* Removed User and IP Address columns from the Form Results submissions view.

* Hardened form submission against cross-origin requests by requiring same-origin Origin/Referer checks (#82)
  Fixed uploaded file MIME detection so declared filenames no longer influence allowed-type checks (#82)

* Removed `ipAddress`, `submitterUsername`, and `userAgent` from newly stored form submissions; existing submissions keep these legacy properties but new CSV and JSON exports no longer include them.
  Hardened forwarded form submissions by sanitizing multipart field names to prevent header injection.
  Changed form submissions to be rejected with `FMDB-012` when the configured action list cannot be read, and with `FMDB-500` when form field metadata cannot be collected, instead of silently continuing.

* Improved form submission validation and updated email/forward actions to use shared utilities (#87)

* Package.json dependencies and versions (#99)

* Added sample extension modules and made conditional-logic rendering consistent across form containers.

* Review 2.1245 (#84)

* Improved reliability of escaping, validation, and configuration handling by expanding Java unit tests. (#92)

* Tighten outbound action safety checks (#80)

## 0.1.0

### New Features

* Initial Formidable alpha foundation for Jahia 8.2+
* Form rendering based on Jahia JavaScript modules
* Editor extensions powered by Module Federation
* First custom selector UI: SelectOptions
* Additional editor-side support for conditional logic
* Server-side submission pipeline
* Built-in actions (Save to JCR, Send email notification, Send submitted content by email, Forward submission to an external endpoint)
* Built-in protections (CAPTCHA support, optional authenticated-user-only submission guard)
* First Form Results administration UI in jContent for stored submissions
