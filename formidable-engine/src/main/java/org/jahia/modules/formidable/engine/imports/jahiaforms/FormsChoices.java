package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The choices of a Forms choice field, a JSON {@code [{"key","value"}]} per language, turned into the
 * manual options Formidable stores: one JSON {@code {"value","label","selected"}} per option and per
 * language, the Forms key as the value so that the imported answers match, the Forms label as the label.
 */
final class FormsChoices {

    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String LABEL = "label";
    private static final String SELECTED = "selected";

    private FormsChoices() {
    }

    /** The options per language, from the choices per language; a language whose JSON does not parse is left out. */
    static Map<String, List<String>> options(Map<String, String> choicesByLanguage) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        choicesByLanguage.forEach((language, json) -> {
            List<String> parsed = parse(json);
            if (!parsed.isEmpty()) {
                options.put(language, parsed);
            }
        });
        return options;
    }

    private static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        try {
            JSONArray choices = new JSONArray(json);
            for (int i = 0; i < choices.length(); i++) {
                JSONObject choice = choices.getJSONObject(i);
                String key = choice.optString(KEY, null);
                if (key != null) {
                    options.add(option(key, choice.optString(VALUE, key)));
                }
            }
        } catch (JSONException e) {
            return List.of();
        }
        return options;
    }

    /** One option as {@code fmdbmix:manualOptions} stores it. */
    static String option(String value, String label) {
        return new JSONObject().put(VALUE, value).put(LABEL, label).put(SELECTED, false).toString();
    }
}
