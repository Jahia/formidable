package org.jahia.modules.formidable.formsimport.jahiaforms;

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
 * The rows and the columns of a matrix have the same shape, with a {@code visible} flag the design view
 * sets when a row or a column is hidden.
 */
final class FormsChoices {

    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String LABEL = "label";
    private static final String SELECTED = "selected";
    private static final String VISIBLE = "visible";

    /** One choice as Forms stored it: its key, what the visitor's answer holds, and its label. */
    record Choice(String key, String label) {
    }

    private FormsChoices() {
    }

    /** The options per language, from the choices per language; a language whose JSON does not parse is left out. */
    static Map<String, List<String>> options(Map<String, String> choicesByLanguage) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        choices(choicesByLanguage).forEach((language, parsed) ->
                options.put(language, parsed.stream().map(choice -> option(choice.key(), choice.label())).toList()));
        return options;
    }

    /** The visible choices per language; a language whose JSON does not parse, or holds none, is left out. */
    static Map<String, List<Choice>> choices(Map<String, String> choicesByLanguage) {
        Map<String, List<Choice>> choices = new LinkedHashMap<>();
        choicesByLanguage.forEach((language, json) -> {
            List<Choice> parsed = parse(json);
            if (!parsed.isEmpty()) {
                choices.put(language, parsed);
            }
        });
        return choices;
    }

    private static List<Choice> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Choice> choices = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject choice = array.getJSONObject(i);
                String key = choice.optString(KEY, null);
                if (key != null && choice.optBoolean(VISIBLE, true)) {
                    choices.add(new Choice(key, choice.optString(VALUE, key)));
                }
            }
        } catch (JSONException e) {
            return List.of();
        }
        return choices;
    }

    /** One option as {@code fmdbmix:manualOptions} stores it. */
    static String option(String value, String label) {
        return new JSONObject().put(VALUE, value).put(LABEL, label).put(SELECTED, false).toString();
    }
}
