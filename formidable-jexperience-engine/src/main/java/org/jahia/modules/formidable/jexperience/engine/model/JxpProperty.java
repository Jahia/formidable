package org.jahia.modules.formidable.jexperience.engine.model;

/**
 * The properties this module's CND declares, named once (see {@link JxpMixin}). A property name is
 * never namespaced, whichever mixin declares it.
 */
public final class JxpProperty {

    /** On a mapped field, the visitor profile property it feeds — the one piece a mapping cannot do without. */
    public static final String PROFILE_PROPERTY = "jExperienceProfileProperty";

    /** Whether the field is pre-filled from the visitor's profile when the page opens. */
    public static final String PREFILL = "jExperiencePrefillFromProfile";

    /**
     * Whether the profile's value may replace a default value the author gave the field. Off, the prefill
     * fills empty fields only; a value the visitor typed is never replaced either way.
     */
    public static final String PREFILL_OVERRIDES_DEFAULT = "jExperiencePrefillOverridesDefault";

    /** How the profile property is written on submission: always, or only while it is still empty. */
    public static final String SET_STRATEGY = "jExperienceSetStrategy";

    /** The sensitive flag — a property rather than a switch, so that the dropdown can depend on it unsaved. */
    public static final String SENSITIVE = "jExperienceSensitive";

    private JxpProperty() {
    }
}
