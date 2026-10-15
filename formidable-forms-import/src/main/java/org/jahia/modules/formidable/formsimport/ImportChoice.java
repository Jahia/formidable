package org.jahia.modules.formidable.formsimport;

/**
 * What receives the results of one source form, chosen by the administrator in the review state of the
 * dialog (docs/architecture/forms-import.md, "Iteration 2"): an entry alone, without a form, which is the
 * default; a form created for them; an existing Formidable form they attach to; or nothing, the form and
 * its results left out of this run, which a later run of the same export proposes again. The choice of a form,
 * with the form and the mapping an attachment needs, is a {@link FormChoice}.
 */
public enum ImportChoice {
    RESULTS_ONLY("resultsOnly"), CREATE("create"), ATTACH("attach"), SKIP("skip");

    private final String json;

    ImportChoice(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }

    /** The choice a value of the dialog names; refused when it names none. */
    public static ImportChoice of(String json) {
        for (ImportChoice choice : values()) {
            if (choice.json.equals(json)) {
                return choice;
            }
        }
        throw new IllegalArgumentException("'" + json + "' is not an import choice: resultsOnly, create, attach or skip");
    }
}
