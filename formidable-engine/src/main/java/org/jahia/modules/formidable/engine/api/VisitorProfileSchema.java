package org.jahia.modules.formidable.engine.api;

import java.util.OptionalInt;

/**
 * The visitor profile properties a field can be mapped to, as the profile server of a site declares them:
 * offered by the jExperience integration, which owns the catalog of those properties and the rule of which
 * property fits which kind of field. The Forms import asks it before it carries a profile prefill over as
 * the mapping of a field (docs/architecture/forms-import.md, "The fields"), because a mapping to a property
 * the profile server does not declare is skipped, without a word, when the mapping rule of the form is built.
 * <p>
 * Optional: the engine runs without the integration, and then never asks.
 */
public interface VisitorProfileSchema {

    /** What the profile server says of a property for a field. */
    enum Fit {
        /** The property exists and its type fits the field. */
        FITS,
        /** The profile server declares no property of that name. */
        UNKNOWN,
        /** The property exists, but its type or cardinality does not fit the field. */
        INCOMPATIBLE,
        /** The profile server cannot be asked now: the answer is left open. */
        UNAVAILABLE
    }

    /**
     * @param siteKey   the site whose profile server is asked
     * @param fieldType the node type of the field, which decides the kind of value it holds
     * @param multiple  whether the field holds several values, by its {@code multiple} setting
     * @param choices   the number of options of a field whose cardinality follows them (a checkbox: one option is
     *                  one value, more a group); empty when unknown or irrelevant
     * @param property  the identifier of the profile property
     */
    Fit fit(String siteKey, String fieldType, boolean multiple, OptionalInt choices, String property);
}
