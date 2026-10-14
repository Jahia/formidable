package org.jahia.modules.formidable.engine.imports;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the administrator chose for one source form in the review state of the dialog
 * (docs/architecture/forms-import.md, "Iteration 2"): the results alone, a form created for them, or an
 * existing Formidable form the results attach to, with the mapping of the field names.
 *
 * @param formId the identifier of the form to attach to, for {@link ImportChoice#ATTACH} alone
 * @param mapping for an attachment, the name of the field of the target form each field of the source lands
 *                under, by the system name the import gives the source field; a field left out keeps its name
 */
public record FormChoice(ImportChoice choice, String formId, Map<String, String> mapping) {

    public static final FormChoice RESULTS_ONLY = new FormChoice(ImportChoice.RESULTS_ONLY, null, Map.of());
    public static final FormChoice CREATE = new FormChoice(ImportChoice.CREATE, null, Map.of());
    public static final FormChoice SKIP = new FormChoice(ImportChoice.SKIP, null, Map.of());
    private static final String CHOICE_KEY = "choice";
    private static final String FORM_KEY = "form";
    private static final String MAPPING_KEY = "mapping";

    /**
     * An attachment to the form, the fields renamed as the mapping says. A field of the form takes at most
     * one name: a submission that holds two names sent to the same field would lose one of its values.
     */
    public static FormChoice attachTo(String formId, Map<String, String> mapping) {
        if (formId == null || formId.isBlank()) {
            throw new IllegalArgumentException("an attachment names the form to attach to");
        }
        Map<String, String> sources = new LinkedHashMap<>();
        for (Map.Entry<String, String> name : mapping.entrySet()) {
            String twice = sources.put(name.getValue(), name.getKey());
            if (twice != null) {
                throw new IllegalArgumentException("the fields " + twice + " and " + name.getKey()
                        + " cannot both land under " + name.getValue());
            }
        }
        return new FormChoice(ImportChoice.ATTACH, formId, Map.copyOf(mapping));
    }

    /**
     * The name the values of a field land under: the field of the form the mapping sends it to, else its
     * own name.
     */
    public String nameOf(String field) {
        return mapping.getOrDefault(field, field);
    }

    /**
     * The choices of a job, by source form name: {@code {"contact-us":"create","survey":{"choice":"attach",
     * "form":"<uuid>","mapping":{"your-name":"name"}}}}; an absent or empty text means the default for every form.
     */
    public static Map<String, FormChoice> fromJson(String json) throws JSONException {
        Map<String, FormChoice> choices = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return choices;
        }
        JSONObject object = new JSONObject(json);
        for (String sourceName : object.keySet()) {
            choices.put(sourceName, of(object.get(sourceName)));
        }
        return choices;
    }

    private static FormChoice of(Object value) {
        if (value instanceof String name) {
            return plain(ImportChoice.of(name));
        }
        if (value instanceof JSONObject object) {
            ImportChoice choice = ImportChoice.of(object.getString(CHOICE_KEY));
            if (choice != ImportChoice.ATTACH) {
                return plain(choice);
            }
            Map<String, String> mapping = new LinkedHashMap<>();
            JSONObject names = object.optJSONObject(MAPPING_KEY);
            if (names != null) {
                for (String source : names.keySet()) {
                    String target = names.optString(source, "");
                    if (!target.isBlank()) {
                        mapping.put(source, target);
                    }
                }
            }
            return attachTo(object.optString(FORM_KEY, null), mapping);
        }
        throw new IllegalArgumentException("a choice is a name or an object, not " + value);
    }

    private static FormChoice plain(ImportChoice choice) {
        return switch (choice) {
            case CREATE -> CREATE;
            case RESULTS_ONLY -> RESULTS_ONLY;
            case SKIP -> SKIP;
            case ATTACH -> throw new IllegalArgumentException("an attachment names the form to attach to");
        };
    }

    public static String toJson(Map<String, FormChoice> choices) {
        JSONObject object = new JSONObject();
        choices.forEach((sourceName, choice) -> object.put(sourceName, choice.toJson()));
        return object.toString();
    }

    private Object toJson() {
        if (choice != ImportChoice.ATTACH) {
            return choice.json();
        }
        return new JSONObject().put(CHOICE_KEY, choice.json()).put(FORM_KEY, formId).put(MAPPING_KEY, new JSONObject(mapping));
    }
}
