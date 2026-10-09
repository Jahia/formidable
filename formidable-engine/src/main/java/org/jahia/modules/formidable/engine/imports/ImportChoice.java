package org.jahia.modules.formidable.engine.imports;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What receives the results of one source form, chosen by the administrator in the review state of the
 * dialog (docs/architecture/forms-import.md, "Iteration 2"): an entry alone, without a form, which is the
 * default, or a form created for them. Keyed by the name of the source form; a source the choices do not
 * name takes the default.
 */
public enum ImportChoice {
    RESULTS_ONLY("resultsOnly"), CREATE("create");

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
        throw new IllegalArgumentException("'" + json + "' is not an import choice: resultsOnly or create");
    }

    /** The choices of a job, {@code {"contact-us":"create"}}; an absent or empty text means the default for every form. */
    public static Map<String, ImportChoice> fromJson(String json) throws JSONException {
        Map<String, ImportChoice> choices = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return choices;
        }
        JSONObject object = new JSONObject(json);
        for (String sourceName : object.keySet()) {
            choices.put(sourceName, of(object.getString(sourceName)));
        }
        return choices;
    }

    public static String toJson(Map<String, ImportChoice> choices) {
        JSONObject object = new JSONObject();
        choices.forEach((sourceName, choice) -> object.put(sourceName, choice.json));
        return object.toString();
    }
}
