package org.jahia.modules.formidable.jexperience.engine;

/**
 * A form's identity in jCustomer: {@code formidable-jxp-<form UUID>} — the event's target id,
 * the mapping rule's form id, what a marketer types in a goal. Stable across renames and
 * moves, valid for Unomi's item id pattern, and never equal to the rendered {@code <form id>}
 * (the bare UUID), so jExperience's tracker never attaches its own raw-fields listener to the
 * form.
 */
public final class FormIdentifier {

    public static final String PREFIX = "formidable-jxp-";

    /** The mixin the module adds to every form, and the read-only property showing the identifier to the author. */
    public static final String FORM_MIXIN = "fmdbmix:jExperienceForm";
    public static final String PROPERTY = "jExperienceIdentifier";

    private FormIdentifier() {
    }

    public static String of(String formUuid) {
        if (formUuid == null || formUuid.isBlank()) {
            throw new IllegalArgumentException("a form UUID is required");
        }
        return PREFIX + formUuid;
    }
}
