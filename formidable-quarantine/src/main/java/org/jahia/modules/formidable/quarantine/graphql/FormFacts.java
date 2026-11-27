package org.jahia.modules.formidable.quarantine.graphql;

import java.util.Map;

/**
 * What the GraphQL API reads of the live form of a held submission: its title, whether it shows the public values of
 * its held submissions to their managers, and the node type of each check of its "Checks" list.
 *
 * @param title      the title of the form in the default language of its site, or the name of its node
 * @param showValues the property {@value #SHOW_VALUES} of the form; false when it is missing
 * @param checkTypes the primary node type of each check node of the list, by the name of the node
 */
record FormFacts(String title, boolean showValues, Map<String, String> checkTypes) {

    /** The property of the per-form policy that shows the public values (mixin {@code fmdbqmix:quarantinePolicy}). */
    static final String SHOW_VALUES = "showValues";

    /** A form that is gone, or that is not a form of the site: no title, no values shown, no check. */
    static final FormFacts NONE = new FormFacts(null, false, Map.of());

    FormFacts {
        checkTypes = Map.copyOf(checkTypes);
    }
}
