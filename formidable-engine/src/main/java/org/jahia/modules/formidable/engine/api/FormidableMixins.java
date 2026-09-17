package org.jahia.modules.formidable.engine.api;

/**
 * The mixins the engine's own CND declares — the extension surface of Formidable. A field type,
 * an action or a container declared in another module takes part in engine behaviour by applying
 * one of these, and server-side code asks for the mixin rather than for a concrete type name:
 * a third-party number field is a number field to the engine exactly as the built-in one is.
 * See docs/architecture/cnd-module-ownership.md.
 * <p>
 * Two mixins are missing on purpose: the one-shot markers of the 0.4 content migrations
 * ({@code fmdbmix:elementsReactivated}, {@code fmdbmix:migratedChoiceOptions}). Each records
 * that a migration has already healed a node, which is the engine talking to itself; their
 * declarations outlive the migrations, so that content still carrying one stays valid, but
 * publishing the names would freeze as contract something no other module has a reason to
 * read. They stay with the migrations, in {@code migration/MigrationMarkers}.
 * <p>
 * These are compile-time constants — see {@link FormidableNodeTypes} for what that implies.
 */
public final class FormidableMixins {

    // Actions
    public static final String FORM_ACTION_MIXIN = "fmdbmix:formAction";
    public static final String READ_ONLY_COMPATIBLE_ACTION_MIXIN = "fmdbmix:readOnlyCompatibleAction";

    // Form tree: the root, what carries logic, what contains, what submits
    public static final String FORM_ROOT_MIXIN = "fmdbmix:formRoot";
    public static final String FORM_LOGIC_ELEMENT_MIXIN = "fmdbmix:formLogicElement";
    public static final String FORM_CONTAINER_MIXIN = "fmdbmix:formContainer";
    public static final String FORM_STEP_MIXIN = "fmdbmix:formStep";
    public static final String FORM_ELEMENT_MIXIN = "fmdbmix:formElement";
    public static final String NON_SUBMITTABLE_MIXIN = "fmdbmix:nonSubmittable";

    // Form-level semantics the engine enforces at submission
    public static final String CAPTCHA_PROTECTED_FORM_MIXIN = "fmdbmix:captchaProtectedForm";
    public static final String AUTHENTICATED_ONLY_FORM_MIXIN = "fmdbmix:authenticatedOnlyForm";

    // Where a choice field takes its options from: the base mixin carries the mode switch,
    // one dynamic-fieldset mixin per mode carries that mode's properties
    public static final String OPTIONS_SOURCE_MIXIN = "fmdbmix:optionsSource";
    public static final String MANUAL_OPTIONS_MIXIN = "fmdbmix:manualOptions";
    public static final String SOURCED_OPTIONS_MIXIN = "fmdbmix:sourcedOptions";
    public static final String CATEGORY_OPTIONS_MIXIN = "fmdbmix:categoryOptions";
    public static final String CONTENT_OPTIONS_MIXIN = "fmdbmix:contentOptions";

    // The kind of value a field holds, which decides the conditional-logic operators it offers
    public static final String CHOICE_FIELD_MIXIN = "fmdbmix:choiceField";
    public static final String CARDINALITY_FROM_CHOICES_MIXIN = "fmdbmix:cardinalityFromChoices";
    public static final String NUMBER_FIELD_MIXIN = "fmdbmix:numberField";
    public static final String BOOLEAN_FIELD_MIXIN = "fmdbmix:booleanField";
    public static final String TEXT_FIELD_MIXIN = "fmdbmix:textField";
    public static final String FILE_FIELD_MIXIN = "fmdbmix:fileField";
    public static final String EMAIL_FIELD_MIXIN = "fmdbmix:emailField";
    public static final String DATE_FIELD_MIXIN = "fmdbmix:dateField";
    public static final String DATETIME_LOCAL_FIELD_MIXIN = "fmdbmix:datetimeLocalField";
    public static final String COLOR_FIELD_MIXIN = "fmdbmix:colorField";

    // A field type claims a jExperience profile mapping by applying this one marker
    public static final String PROFILE_MAPPABLE_FIELD_MIXIN = "fmdbmix:profileMappableField";

    // Date bounds, and the same contract for a date-and-time field
    public static final String DATE_BOUNDS_MIXIN = "fmdbmix:dateBounds";
    public static final String FIXED_MIN_DATE_MIXIN = "fmdbmix:fixedMinDate";
    public static final String FIXED_MAX_DATE_MIXIN = "fmdbmix:fixedMaxDate";
    public static final String RELATIVE_MIN_DATE_MIXIN = "fmdbmix:relativeMinDate";
    public static final String RELATIVE_MAX_DATE_MIXIN = "fmdbmix:relativeMaxDate";
    public static final String DATETIME_BOUNDS_MIXIN = "fmdbmix:datetimeBounds";
    public static final String FIXED_MIN_DATETIME_MIXIN = "fmdbmix:fixedMinDatetime";
    public static final String FIXED_MAX_DATETIME_MIXIN = "fmdbmix:fixedMaxDatetime";
    public static final String RELATIVE_MIN_DATETIME_MIXIN = "fmdbmix:relativeMinDatetime";
    public static final String RELATIVE_MAX_DATETIME_MIXIN = "fmdbmix:relativeMaxDatetime";

    private FormidableMixins() {
    }
}
