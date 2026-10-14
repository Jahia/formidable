package org.jahia.modules.formidable.engine.imports.model;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One line of the import report about a form or a field (docs/architecture/forms-import.md, "Running it"):
 * a code the dialog translates, its parameters, and whether the administrator has something to do about it.
 *
 * @param code   the key of the sentence under {@code formResults.import.note} in the locales of the dialog
 * @param level  {@link Level#INFO} for what was carried over and is only worth knowing, {@link Level#ACTION}
 *               for what was lost or approximated and is to be redone or checked by hand
 * @param params the values the sentence names, by placeholder
 */
public record ImportNote(String code, Level level, Map<String, String> params) {

    /** Whether the line asks for something to be done by hand. */
    public enum Level {
        INFO("info"), ACTION("action");

        private final String json;

        Level(String json) {
            this.json = json;
        }
    }

    public ImportNote {
        params = Map.copyOf(params);
    }

    /** A line to act on, with its parameters as name, value pairs. */
    public static ImportNote action(String code, String... params) {
        return new ImportNote(code, Level.ACTION, pairs(params));
    }

    /** A line only worth knowing, with its parameters as name, value pairs. */
    public static ImportNote info(String code, String... params) {
        return new ImportNote(code, Level.INFO, pairs(params));
    }

    /** The value of a parameter, or null. */
    public String param(String name) {
        return params.get(name);
    }

    public JSONObject toJson() {
        return new JSONObject().put("code", code).put("level", level.json).put("params", new JSONObject(params));
    }

    @Override
    public String toString() {
        return code + params;
    }

    private static Map<String, String> pairs(String... params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("the parameters of a note go by name, value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            map.put(params[i], params[i + 1] == null ? "" : params[i + 1]);
        }
        return map;
    }
}
