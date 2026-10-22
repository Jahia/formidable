# Mixin catalogue

Every mixin the Formidable modules declare, in one place: what each one says, who reads it, which
built-in types carry it, and how a module of your own takes part. The CND files stay the source of
truth — a row here summarises a declaration, it never replaces it — and
[CND module ownership](cnd-module-ownership.md) says *why* a mixin lives in the module it lives in.
This page says *what each one is for*.

Sixty-nine mixins, in eleven families: the form tree, the value kinds, the options of a choice field,
the bounds of a date field, the form-level features, the actions, the field settings, jExperience,
the import of Jahia Forms, the migration markers and the samples. A last table lists the platform
mixins Formidable relies on, and why.

## How to read the tables

**Use** says how a mixin reaches a node, because that decides what an author sees and what the
repository enforces:

| Use | What it means | Example |
|---|---|---|
| supertype | A type names it in its own declaration (`[fmdb:select] > …, fmdbmix:choiceField`). Nothing writes it into `jcr:mixinTypes`: the node has it from its type, with no switch in the editor. This is the form a third-party type follows for every marker and contract below. | `fmdbmix:formRoot` |
| switch | A mixin declared with `extends = <type>` and `itemtype`: the Content Editor shows it as a section with a toggle on every node of that type, and adds it to `jcr:mixinTypes` when the author turns it on. | `fmdbmix:captcha` |
| mode fieldset | A `jmix:dynamicFieldset` mixin with `extends = <base mixin>`: the editor activates it when a mode property of the base mixin takes the matching value (the `addMixin` wiring of the `fieldsets/` overrides) and removes it with the mode. | `fmdbmix:manualOptions` |
| written by code | Added by a service, a migration or an import, never by an author; its properties are hidden. | `fmdbmix:importedForm` |

**Read by** names the code that asks `isNodeType` for the mixin, and the editor override that lays
its properties out, so that a reader knows what changes when a type adopts it. A mixin nobody reads
at runtime is still a contract: the field-actions switch attaches to `fmdbmix:submittableField`
with `extends`, and the repository checks the children of a list against `fmdbmix:formElement`.

Every name the engine declares has a constant in `FmdbMixin`
(`org.jahia.modules.formidable.engine.api`), checked both ways by `scripts/check-nodetype-names.mjs`;
Java reads the constant, never the literal
([Naming these types from Java](cnd-module-ownership.md#naming-these-types-from-java)). The two
migration markers are the exception, held by `MigrationMarker` next to the migrations that write them.

## The form tree

Declared by `formidable-engine` unless said otherwise. These are the structural contracts: what is
a form, what carries logic, what contains, what submits.

| Mixin | Use | What it says | Read by | Carried by |
|---|---|---|---|---|
| `fmdbmix:formRoot` | supertype | The root of a form: the node the submission pipeline resolves, the ACL sync follows and the results reference. A supertype so that the JCR observation filters match — an event carries the primary type and Jackrabbit tests `isDerivedFrom`. A third-party form type declares it the same way, and never `addMixin`s it. | the submission pipeline, the Save to JCR action, the publication ACL sync, the logic sync and the duplication cleanup, the field-action endpoint, the jExperience render filter and mapping sync, the Results page's query of the forms still in edit | `fmdb:form` |
| `fmdbmix:formLogicElement` | supertype | A node that can carry conditional logic: `logics` (the rules, one JSON string each), the hidden `fieldKey` (the stable identity a rule points at, a UUID assigned at creation) and the hidden `logicsSrc` list (one `fmdb:logicSrc` per rule, whose weak reference is what the repository follows). [Conditional logic field resolution](conditional-logic-field-resolution.md). | `FieldKeyAssignmentListener` (assigns the `fieldKey`), `FormLogicSyncListener`, `FormLogicSyncService`, `FormLogicRuleValueRemap`, `FormDuplicationCleanupListener`, `TranslationFieldKeyCleanup`, `FormFieldMetadataCollector`; `LogicAwareRender` in the elements; the engine's editor override gives it the **Conditional logic** section | every element, container and step, through the three mixins below |
| `fmdbmix:formContainer` | supertype | A node that owns child form items and propagates a visibility context to them. Its children render through the `hidden.logic` view registered on this mixin ([Two rendering contracts](../extension/how-to-extend-views-and-elements-from-third-party-module.md#two-rendering-contracts)). | `FormFieldMetadataCollector` (the walk of the tree); the `hidden.logic` and `cm` views and the Page Builder box configuration in the elements | `fmdb:fieldList`, `fmdb:fieldset`, and `fmdb:step` through `formStep` |
| `fmdbmix:formStep` | supertype | A step of a multi-step form, a container too. | the rules editor's source query and `FormSourceFieldIndex` (with `formElement`: the nodes a rule can point at); `fmdb:fieldList` accepts it as a child | `fmdb:step` |
| `fmdbmix:formElement` | supertype | A form item with a title (`mix:title`) and logic: what the lists accept as a child, and what the pipeline submits unless it is `nonSubmittable`. | `FormFieldMetadataCollector` and `FieldActionCollector` (`formElement && !nonSubmittable` is a submitted field), `FormSourceFieldIndex` (the nodes a rule can point at), `FormidableOptionsSourceService` (skips them when listing content), the rules editor, the Results page and the import's attachment queries | `fmdb:fieldset`, and every field through `fmdbmix:element` |
| `fmdbmix:element` (elements) | supertype | The public contract of a leaf field: a `formElement` that `fmdb:fieldset` accepts as a child, shown under **Form field** in the content type selector. A third-party field type declares it, plus exactly one of the three markers below ([Case 3: add a new leaf form element](../extension/how-to-extend-views-and-elements-from-third-party-module.md#case-3-add-a-new-leaf-form-element)). | the child definition of `fmdb:fieldset`; `scripts/check-field-markers.mjs` | every built-in and extended input |
| `fmdbmix:formContent` (elements) | supertype | Content embeddable in a form that is not a field: no value, no logic, no title. Shown under **Form content** in the selector. | the child definitions of `fmdb:fieldList`, `fmdb:fieldset` and `fmdb:step` | `fmdb:richText` |
| `fmdbmix:component` (elements) | supertype | The authoring category **Formidable - Form Elements** of the content type selector, with the platform's droppable, editorial and access-controllable marks. | `FormidableOptionsSourceService` (skips them when listing content) | `fmdb:form`, `fmdb:formReference` |
| `fmdbmix:submittableField` | supertype | The positive marker of a field that submits a value: text, choice, number, date, colour, boolean, hidden. A mixin meant for "every field with a value" attaches here with `extends`, as the field-actions switch does — `extends` names one supertype and cannot say "`formElement` minus `nonSubmittable`". Nothing reads it at runtime: a field type without it is still submitted, it only lacks what attaches here. | `scripts/check-field-markers.mjs` | every input but the file input and the button |
| `fmdbmix:nonSubmittable` | supertype | A form element that submits nothing: the button, the fieldset. | `FormFieldMetadataCollector`, `FieldActionCollector`; the Results page leaves it out of the columns | `fmdb:inputButton`, `fmdb:fieldset` |
| `fmdbmix:fileField` | supertype | A field that submits a file, read from its multipart part — not a value a check judges. The third of the three markers, and a value kind. | `FormFieldMetadataCollector` (the accepted types, the multipart reading); `FieldShapes` (never mappable) | `fmdb:inputFile` |
| `fmdbmix:cardinalityFromChoices` | supertype | A choice field whose cardinality is its number of choices, not a `multiple` property: the view draws one input per choice, so one choice submits one value and several submit a list. | `FieldShapes` (the cardinality the jExperience mapping offers) | `fmdb:checkbox` |

A field type declares exactly one of `submittableField`, `nonSubmittable` and `fileField`;
`scripts/check-field-markers.mjs`, run by CI, refuses a CND that declares none or several.

## Value kinds

Declared by `formidable-engine`. A field type carries the kind of value it submits; the kind drives
three things: what the server checks on the submitted value, whether the field is offered as a
source of conditional logic and with which operators, and the shape of profile property the
jExperience mapping offers. A third-party field type opts in from its own CND
([Make your field a conditional-logic source](../extension/how-to-extend-views-and-elements-from-third-party-module.md#make-your-field-a-conditional-logic-source)).

| Mixin | What the server checks | Logic source | Carried by |
|---|---|---|---|
| `fmdbmix:textField` | nothing of its own: the length bounds and the pattern a field declares are checked whatever its kind | yes: is filled, is empty, equals, contains | `fmdb:inputText`, `fmdb:textarea`, `fmdb:inputEmail` |
| `fmdbmix:emailField` | the address format | no — an email input is also a `textField`, which makes it one | `fmdb:inputEmail` |
| `fmdbmix:numberField` | a finite number within `minValue`/`maxValue` — those two names, read by the server, not `min`/`max` | yes: equals, differs, lower, greater, between, numerically | `fmdb:inputNumber`, `fmdb:inputRange`, `fmdbext:rating`, `fmdbext:scale` |
| `fmdbmix:booleanField` | the state, `true`/`false` (`on` accepted) | yes: is true, is false | `fmdbext:switch`, `fmdbext:consent` |
| `fmdbmix:dateField` | an ISO date within the bounds of `dateBounds` | yes: before, after, on, between, the submission day allowed as a bound | `fmdb:inputDate` |
| `fmdbmix:datetimeLocalField` | an ISO date-time within the bounds of `datetimeBounds` | no | `fmdb:inputDatetimeLocal` |
| `fmdbmix:colorField` | the `#rrggbb` format | no | `fmdb:inputColor` |
| `fmdbmix:choiceField` | the value is one of the field's options, re-resolved from the source when they come from one; a source that fails rejects every non-empty value | yes: is one of, is not one of; the checkbox: checked, unchecked, contains any, contains all | `fmdb:select`, `fmdb:radio`, `fmdb:checkbox`, `fmdbext:card` |
| `fmdbmix:fileField` | the type against `accept` | no | `fmdb:inputFile` |

The checks are `FieldValidator`'s, on the metadata `FormFieldMetadataCollector` reads from these
mixins; the rules editor's source query and `FieldShapes` (jExperience) read the same set.
`fmdbmix:choiceField` takes `fmdbmix:optionsSource` as a supertype: a choice field gets the
options contract of the next family with its kind.

## Where a choice field takes its options

Declared by `formidable-engine`, edited through the elements' `fieldsets/` overrides, resolved
server-side ([Choice field options sources](choice-field-options-sources.md)). The base mixin
carries the mode switch; one mode fieldset per mode carries that mode's properties, activated by
the editor with the mode and removed with it, so a field never holds the settings of two modes.

| Mixin | Use | What it says | Read by |
|---|---|---|---|
| `fmdbmix:optionsSource` | supertype (of `choiceField`) | `optionsMode`: `manual` (default, so typing options by hand stays the out-of-the-box experience), `source`, `category` or `content`. | `ChoiceOptionsResolverImpl` (the mode, when the options are resolved), `MixinPropertyNamesMigration`; the `forms/` and `fieldsets/` overrides of the elements place the mode at the top of the Content section and wire the fieldsets below |
| `fmdbmix:manualOptions` | mode fieldset | `options`, the i18n list typed by hand. Not mandatory on purpose: a mandatory i18n property is validated in every language the author merely visits; `ManualOptionsLanguageSync` keeps the languages coherent instead. | `FormFieldMetadataCollector` (the allowlist), `ManualOptionsLanguageSyncListener`, the import's `FormsChoices`, `ChoiceOptionsContentMigration` |
| `fmdbmix:sourcedOptions` | mode fieldset | `optionsSourceKey`, a source declared in the configuration (an initializer of the platform or of a module). | `optionsSource.server.ts` (resolution at render), `FormFieldMetadataCollector` through `SourcedOptionsResolver`, `FormidableOptionsSourceService` and `ProfilePropertiesChoiceListInitializer` (the sources offered), the import's `ImportWriter` (a country source for a Forms country list) |
| `fmdbmix:categoryOptions` | mode fieldset | `optionsRootCategory`, the category whose children are the options. | `optionsSource.server.ts`, `FormFieldMetadataCollector`, `FormidableOptionsSourceService`, `ProfilePropertiesChoiceListInitializer` |
| `fmdbmix:contentOptions` | mode fieldset | `optionsRootNode` and `optionsNodeType`: the content of that type under that root. | the same four, and `FormidableContentTypesInitializer` (the types offered for the root) |

The four source settings are required by the editor only, not by the repository: a JCR-level
`mandatory` added to a deployed type is a MAJOR change that cancels the deployment. Each of these
mixins still declares the hidden `fmdb:`-prefixed spelling of its properties, the 0.4.0 names that
`MixinPropertyNamesMigration` renames at startup; they leave in 0.6
([Startup migrations](../administration/upgrade-notes.md#startup-migrations)).

## The bounds of a date field

Declared by `formidable-engine`, edited through the elements' overrides next to the date types,
resolved by `bounds.ts` at render and by `FormFieldMetadataCollector` at validation
([Date bounds: none, a fixed date, or the submission day](custom-validation.md#date-bounds-none-a-fixed-date-or-the-submission-day)).
Each bound is a mode: none, a fixed date, or the submission day shifted by an amount of days,
months or years. The modes are exclusive by construction, a bound being either fixed or relative,
never both. Two flavours only because the calendar widget differs.

| Mixin | Use | What it says | Carried by |
|---|---|---|---|
| `fmdbmix:dateBounds` | supertype | `minBoundMode` and `maxBoundMode`: `none` (default), `date`, `today`. | `fmdb:inputDate` |
| `fmdbmix:fixedMinDate`, `fmdbmix:fixedMaxDate` | mode fieldset | `min` / `max`, a date, mandatory: the mixin exists only while the mode is `date`, so the constraint applies exactly then. | — |
| `fmdbmix:relativeMinDate`, `fmdbmix:relativeMaxDate` | mode fieldset | `minRelativeAmount` / `maxRelativeAmount` (signed) and `…Unit` (`days`, `months`, `years`): the submission day shifted, for age limits and booking windows. | — |
| `fmdbmix:datetimeBounds` | supertype | the same two modes for a date-and-time field. | `fmdb:inputDatetimeLocal` |
| `fmdbmix:fixedMinDatetime`, `fmdbmix:fixedMaxDatetime` | mode fieldset | `min` / `max`, a date and time, mandatory. | — |
| `fmdbmix:relativeMinDatetime`, `fmdbmix:relativeMaxDatetime` | mode fieldset | the same relative offsets, under the same property names, so the runtime reads one set. | — |

As for the options, the relative offsets are required by the editor only, and the hidden
`fmdb:`-prefixed properties are the 0.4.0 names, gone in 0.6. `DateBoundsContentMigration` (0.4.0)
turned the fixed bounds of 0.3 content into these modes.

## Form-level features

What an author turns on at the form. The two security features are *wrappers*: the author-facing
switch lives in `formidable-elements` because it extends `fmdb:form`, declared there, and it takes
the engine's semantic as a supertype, so that the Java code reads the semantic and never the
switch ([Form-level feature mixins use a wrapper pattern](cnd-module-ownership.md#form-level-feature-mixins-use-a-wrapper-pattern)).
A third-party form type offers them through switches of its own, extending its type, over the
same two semantics.

| Mixin | Module | Use | What it says | Read by |
|---|---|---|---|---|
| `fmdbmix:captcha` | elements | switch on `fmdb:form` | CAPTCHA protection: the form injects the configured widget, the server verifies the token. The configuration is the engine's `org.jahia.modules.formidable.captcha.cfg`, nothing in the repository. | the form view (renders the widget, warns when the configuration is incomplete); the import sets it on a form that had one in Jahia Forms |
| `fmdbmix:captchaProtectedForm` | engine | supertype of the switch | the semantic the engine enforces | `FormSubmissionPipeline` (the token check), `CaptchaRenderFilter` (`applyOnNodeTypes`) |
| `fmdbmix:requireAuthentication` | elements | switch on `fmdb:form` | only signed-in visitors may submit. | the form view, through the semantic below |
| `fmdbmix:authenticatedOnlyForm` | engine | supertype of the switch | the semantic: a guest submission is rejected with `FMDB-009` before any file data is read. | `FormSubmissionPipeline`; the form view shows the sign-in notice instead of the fields |
| `fmdbmix:buttons` | elements | supertype of `fmdb:form` | the labels of the submit, reset, new-form, try-again, previous and next buttons, and whether the three optional ones show. | the form views; the elements' override lays them out in the **Buttons** section |
| `fmdbmix:responses` | elements | supertype of `fmdb:form` | the three rich-text messages: on success, on error, and the maintenance message shown while the platform is read-only. The light editor configuration of the help texts: formatting, links and lists, no structural markup. | the form views (`Form.client.tsx` for the maintenance message); the elements' override |
| `fmdbmix:multiStep` | elements | supertype of `fmdb:form` | `showStepsNav`: the step navigation of a multi-step form. | the form views; placed first in the **Step navigation** fieldset of Buttons |
| `fmdbmix:style` | elements | supertype of `fmdb:form` | `css`, a stylesheet of the form's own, inlined by the view. | the form views; the elements' override |

## Actions

Declared by `formidable-engine`. A form action runs after the submission is accepted; a field
action judges one field's value while the visitor fills the form and again at submission, before
any form action. Both kinds are concrete types declared by the module that implements them, in
Formidable or elsewhere, bound to an OSGi service by type name
([How to create a form action](../extension/how-to-create-form-action.md),
[How to create a field action](../extension/how-to-create-field-action.md)).

| Mixin | Use | What it says | Read by | Carried by |
|---|---|---|---|---|
| `fmdbmix:formAction` | supertype | What the node *is*: a form action, stored under the form's `fmdb:actionList`, which accepts nothing else. A `FormAction` service bound to the type runs it. | `ActionSummaryService` (the summary line of each action in the authoring zone); the `FormAction` and `ActionList` authoring views | `fmdb:emailNotificationAction`, `fmdb:emailContentAction`, `fmdb:forwardAction`, `fmdb:save2jcrAction`, `fmdb:redirectAction`, the samples' `fmdbsample:logSubmissionAction` |
| `fmdbmix:readOnlyCompatibleAction` | supertype | The action never writes to the repository, so it may run while the platform is in read-only mode. The polarity is inverted for safety: an action type without it, a third party's that never considered read-only mode included, is presumed to write and blocks its form during maintenance — over-blocked at worst, a submission never lost to a mid-pipeline rejection. | `FormSubmissionPipeline`; the form view, which shows the maintenance message instead of the form | the three e-mail and forward actions, `fmdb:redirectAction`; not `fmdb:save2jcrAction` |
| `fmdbmix:fieldActionFeedback` | supertype of `fieldAction` | What the contributor sets on every field action whatever its type: `rejectionMessage` (rich text, `${value}` interpolated), `trigger` (`blur` for a cheap check, `submit` for a paid one), `severity` (`block` or `warn`), `whenUnavailable` (`accept`, a provider outage never blocks visitors, or `reject`). A supertype since 0.5.0, so a third-party type gets the four unasked. | `ResolvedFieldAction`; the engine's override lays the four out in the **Configuration** section; `RedundantMixinMigration` drops it from `jcr:mixinTypes` where 0.5.0 development builds had assigned it | every field-action type |
| `fmdbmix:fieldAction` | supertype | What the node *is*: a field action, stored under the field's `fmdb:fieldActionList`, which accepts nothing else. A `FieldAction` service bound to the type answers the verdict; it never writes. | `FieldActionCollector`, `FieldActionTypesService` (the types the site offers); `fieldActions.server.ts` and the `FieldAction` and `FieldActionList` authoring views in the elements | the samples' `fmdbsample:blockedWordsAction`, `emailDomainAction`, `experianEmailAction`, `zeroBounceEmailAction` |
| `fmdbmix:fieldActions` | switch, `extends = fmdbmix:submittableField` | The **Field actions** switch at the end of the field's editor, on every field with a value and on nothing else. On, it autocreates the `actions` list (`fmdb:fieldActionList`, orderable: the first blocking refusal wins); off, it removes both. No `> jmix:dynamicFieldset`: that supertype extends `jmix:templateMixin`, which the editor renders without a switch. | `FieldActionCollector`, `fieldActions.server.ts`; the elements' override shows the switch, the engine's hides the list-ordering block it would otherwise bring | any field carrying `submittableField`, when the author turns it on |
| `fmdbmix:providerFieldAction` | — | **Deprecated, removed in 0.6.** The provider a field action picked when the services were a list of the engine's configuration; a field action now reads its service from its own module's configuration. Kept hidden so that a type or a node still carrying it deploys. | nothing | — |

[Field actions](field-actions.md#jcr-definitions) gives the full model and the decision log.

## Field settings

Declared by `formidable-elements`: what a contributor sets on a field beyond its value, read by the
views, placed by the elements' `forms/` overrides
([Content Editor layout](content-editor-layout.md#who-ships-which-file)).

| Mixin | What it says | Read by | Carried by |
|---|---|---|---|
| `fmdbmix:validationMessages` | `msgValueMissing`: the message replacing the browser's when a required field is left empty. Every message left empty keeps the browser's ([Custom validation](custom-validation.md#validation-message-mixins-cnd)). | `validationProps` of `@jahia/formidable-library`, which the views use to put each message on the input as a data attribute the client validation reads | `fmdb:checkbox`, `fmdb:radio`, `fmdb:select`, `fmdb:inputFile`, `fmdb:inputColor`, `fmdb:inputRange`, the five extended inputs |
| `fmdbmix:textValidationMessages` | the base plus `msgTypeMismatch`, `msgPatternMismatch`, `msgTooShort`, `msgTooLong`. | the same | `fmdb:inputText`, `fmdb:inputEmail`, `fmdb:textarea` |
| `fmdbmix:rangeValidationMessages` | the base plus `msgRangeUnderflow`, `msgRangeOverflow`, `msgStepMismatch`, `msgBadInput`. | the same | `fmdb:inputNumber`, `fmdb:inputDate`, `fmdb:inputDatetimeLocal` |
| `fmdbmix:advancedInputTextSettings` | `mask`, `pattern`, `title`, `readonly`, `autofocus`, `disabled`, `spellcheck`; `form`, `dirname` and `size` hidden, no effect in a Formidable form. | the text input view (the samples' replacement view too) | `fmdb:inputText` |
| `fmdbmix:advancedTextareaSettings` | `readonly`, `spellcheck`, `autofocus`, `disabled`, `wrap`; `cols`, `form`, `dirname` hidden. | the textarea view | `fmdb:textarea` |
| `fmdbmix:advancedInputNumberSettings` | `title`, `readonly`, `autofocus`, `disabled`; `form` hidden — only the attributes HTML defines for `type="number"`. | the number input view | `fmdb:inputNumber` |
| `fmdbmix:advancedInputRangeSettings` | `title`, `autofocus`, `disabled`; `form` hidden — `readonly` does not apply to a slider. | the range input view | `fmdb:inputRange` |

The four `advanced…Settings` were switches before 0.5.0 and are supertypes since, so that every
field has them with no mixin to add on save — a translator's role cannot add one. The names stay
because Jackrabbit accepts a redundant mixin but not a property declared twice, which is also why
the properties cannot move to the types themselves; `RedundantMixinMigration` drops the redundant
entry from the fields saved before.

## jExperience

The marker is the engine's, the properties are the integration module's, attached with `extends`
to the marker and never to a list of field types ([jExperience integration](jexperience-integration.md)).

| Mixin | Module | Use | What it says | Read by | Carried by |
|---|---|---|---|---|---|
| `fmdbmix:profileMappableField` | engine | supertype | The field can take part in a profile mapping, and its accepted values go to jCustomer with every submission on a tracked site, mapped or not. Declare it on every field whose value a profile could hold; leave it off a field whose value must not leave the site. Files are not mappable, hidden inputs are. | `SubmissionEventEnricher` (the fields sent with the event), `FieldShapes` (the property shapes offered), `PrefillMappings` (the fields a prefill reaches), the import's `ImportJobs` (whether a Forms prefill can be carried over as a mapping) | every input carrying `submittableField` |
| `fmdbmix:jExperienceProfileMapping` | jexperience-engine | switch, `extends = fmdbmix:profileMappableField` | The **jExperience** section of the field's editor: `jExperienceProfileProperty`, `jExperienceSetStrategy` (`alwaysSet` or `setIfMissing`), `jExperiencePrefill` and `jExperiencePrefillThen` (`editable`, `readOnly`, `hidden`), the prefill living inside the mapping because it needs one. | `FormMappingReader` and `PrefillMappings` of the jExperience module, the import's `FormsFormConverter` and `ImportJobs` (a Forms prefill written as a mapping); its `forms/` and `fieldsets/` overrides | any mappable field, when the author turns it on |
| `fmdbmix:jExperienceSensitiveField` | jexperience-engine | — | **Deprecated, removed in 0.6.** The sensitive flag's first home in the 0.5.0 development builds; nothing reads it, the declaration stays hidden and without `extends` so that a node left carrying it still deploys. | nothing | — |

## The import of Jahia Forms

Declared by `formidable-forms-import`, written by the import and read by its later runs; the engine
reads none of them ([Importing forms and results from Jahia Forms](forms-import.md#iteration-1-import)).
Source-agnostic: `sourceSystem` names the source, nothing else does. Every property is hidden.

| Mixin | Use | What it says |
|---|---|---|
| `fmdbmix:importJobsParent` | written by code | Set on the site's `fmdb:resultsFolder` by the first import: the results root may then hold the `fmdb:importJobs` node, next to the entries. A mixin rather than an `extends` because the repository checks a child against the mixins a node carries. |
| `fmdbmix:importedForm` | written by code | On a form the import created: `sourceSystem`, `sourceId` (the form in the source), `sourceResultsId` (its results entry there), `sourcePath`. A later run looks a form up by either id, hence both indexed. |
| `fmdbmix:importedField` | written by code | On a field the import created: `sourceId` and `sourceName`, the field it stands for and the name its values had. |
| `fmdbmix:importedResults` | written by code | On a results entry holding imported submissions: `sourceSystem`, `sourceFormIds` (indexed, what a later run finds the entry by), `mappings` (per source form, the mapping an attachment applied, as JSON). |
| `fmdbmix:importedSubmission` | written by code | On an imported submission: `sourceId` (indexed, what a later run skips on) and `sourceFormId`. |

Read by `ImportWriter`, `ImportedForm` and `FormsSubmissionConverter`, through the module's own
`FormsImportMixin` holder.

## Migration markers

Declared by `formidable-engine`, one-shot, written by the 0.4 content migrations so that they never
touch a node twice. Their constants live in `MigrationMarker`, not in `FmdbMixin`: no other module
has a reason to read them. The migrations leave in 0.6; the declarations outlive them, because a
declaration dropped while nodes carry it is flagged by nothing
([Startup migrations](../administration/upgrade-notes.md#startup-migrations)).

| Mixin | Written on | What it says | Read by |
|---|---|---|---|
| `fmdbmix:elementsReactivated` | a site node | `ElementsSiteReactivation` re-enabled `formidable-elements` there (the 0.3 → 0.4 module-identity healing); a marked site is never touched again, so a later deliberate deactivation sticks. | `ElementsSiteReactivation` |
| `fmdbmix:migratedChoiceOptions` | a choice field | Its options came from 0.3, whose per-language lists could translate the values, and have not yet converged to the shared-value identity. It licenses the divergent-list heuristics that must never run on native 0.4 content, and is removed the first time the field's languages realign. | `ChoiceOptionsContentMigration`, `ManualOptionsLanguageSync`, `ManualOptionEntries`, `FormLogicRuleValueRemap` |

## The samples

Declared by the test modules, each the shape a module of its own copies
([samples module](../../jahia-test-module/formidable-test-module-samples-tsx/README.md)).

| Mixin | Module | Use | What it demonstrates |
|---|---|---|---|
| `fmdbsamplemix:enrichedResponse` | samples-java | an `itemtype` with no `extends`: the Cypress spec adds it to its fixtures | The opt-in of a `SubmissionResponseEnricher`: only a form carrying it gets the sample block in the response of an accepted submission. Gating on something the author set keeps a module's key out of every other form's response ([How to enrich the submission response](../extension/how-to-enrich-the-submission-response.md)). No `extends` on purpose: a test module must not offer a switch on every form of every site. |
| `fmdbsamplemix:customStyle` | samples-tsx | switch on `fmdb:fieldset` | `customCssClassname`, read by the samples' `customStyle` fieldset view: a third-party property and a dedicated editor section on a built-in type. |
| `fmdbsamplemix:helpTextPosition` | samples-tsx | switch extending the twelve built-in field types that declare a `helpText` | `helpTextPosition` (`up`, `down`, `both`), honoured by the samples' text input view: a third-party setting surfaced in the built-in field's own editor, in the **Help & presentation** fieldset right under the help text, rather than behind a switch of its own ([Case 4](../extension/how-to-extend-views-and-elements-from-third-party-module.md#case-4-add-a-contributor-setting-to-a-built-in-field-in-the-fields-own-form)). |

## The platform mixins Formidable relies on

Jahia's own mixins, where a Formidable type uses them and what each one buys.

| Platform mixin | On | Why |
|---|---|---|
| `jmix:droppableContent`, `jmix:editorialContent`, `jmix:accessControllableContent` | `fmdbmix:component` | the form and the reference can be created in an area, are listed as editorial content, and can carry an ACL of their own |
| `jmix:visibleInContentTree` | `fmdb:form` | the form shows in jContent's tree, its lists and fields under it |
| `mix:title` | `fmdbmix:formElement`, the lists, the actions, `fmdb:step` | `jcr:title`, the i18n label the editor and the Page Builder show |
| `jmix:list` | `fmdb:fieldList`, `fmdb:actionList`, `fmdb:fieldActionList`, `fmdb:logicList` | jContent treats the node as a list: list rendering, the **Content list & ordering** block |
| `jmix:systemNameReadonly` | the three lists | `fields`, `actions` are structure the code walks by name: never renamed |
| `jmix:hideDeleteAction` | the three lists | jContent hides Delete and Cut and the Page Builder refuses to drag: the form creates and removes its two lists, the field-actions switch its list, never a contributor |
| `jmix:dynamicFieldset` | the options modes, the bound modes | the editor adds and removes the mixin with the mode, the "mode fieldset" use above; never through `jmix:templateMixin`, which renders without a switch |
| `jmix:nodeReference` | `fmdb:formReference` | `j:node`, the platform's reference contract: the form renders where the reference is placed |
| `jmix:accessControlled` | `fmdb:resultsFolder`, `fmdb:formResults`, `fmdb:importJobs` | an ACL of their own: who reads which results ([Results permissions](../administration/results-permissions.md)) |
| `jmix:unversionedBasemetadata`, `jmix:observable` | `fmdb:formSubmission` | creation and modification metadata without a version history, and observation events |
| `jmix:lockable`, `mix:lastModified`, `jmix:lastPublished`, `mix:versionable`, `jmix:observable`, `jmix:workflow` | `fmdb:logicList` | what `jnt:content` would bring, named one by one because the list extends `nt:base`: the logic storage locks, versions and publishes with the field it belongs to |

## Declaring a type of your own

What to take from the tables, by kind of type; the how-to is in the extension guides.

- **A field**: `fmdbmix:element`; exactly one of `fmdbmix:submittableField`, `fmdbmix:nonSubmittable`, `fmdbmix:fileField`; one value kind; `fmdbmix:profileMappableField` unless the value must not leave the site; the validation-message mixin matching the constraints the field renders. A choice field takes `fmdbmix:choiceField`, which brings the options contract and its editor; a date field `fmdbmix:dateBounds` or `fmdbmix:datetimeBounds`. The number bounds are named `minValue`/`maxValue`.
- **A container**: `fmdbmix:formContainer`, with `fmdbmix:formElement` when it has a title and sits in a list as the fieldset does, and child rendering delegated to the `hidden.logic` view.
- **A form type**: `> fmdbmix:formRoot`, never `addMixin`; its own switches over `fmdbmix:captchaProtectedForm` and `fmdbmix:authenticatedOnlyForm` when it offers those features.
- **A form action type**: `fmdbmix:formAction`, plus `fmdbmix:readOnlyCompatibleAction` when it never writes to the repository.
- **A field action type**: `fmdbmix:fieldAction`, which brings `fmdbmix:fieldActionFeedback`.
- **A setting on a built-in field**: a mixin of your own with `extends` naming the types and an `itemtype`, placed by a `forms/` override ([Case 4](../extension/how-to-extend-views-and-elements-from-third-party-module.md#case-4-add-a-contributor-setting-to-a-built-in-field-in-the-fields-own-form)).
- **A form-level feature**: a switch extending `fmdb:form`, over an engine semantic when the engine must enforce it, as the two wrappers do.

## Keeping this page true

A new mixin gets its row here in the change that declares it, and a row changes when the
declaration does. Four checks of the static-analysis job hold what can be held:
`check-mixin-catalogue.mjs` reads every `fmdbmix:` and `fmdbsamplemix:` mixin the CND files of
the repository declare and every name the first cell of a row here carries, and fails on a name
present on one side only — a mixin without its row, a row without its mixin;
`check-nodetype-names.mjs` keeps `FmdbMixin` and the engine's CND in parity,
`check-field-markers.mjs` keeps the three field markers exclusive, `check-doc-links.mjs` keeps
every link of this page resolving. What a row says is held by nobody but its reader: **Read by**
was measured by an exact-name grep of each mixin and of its `FmdbMixin` constant over the main
sources, so a reader added or removed comes with its edit here.
