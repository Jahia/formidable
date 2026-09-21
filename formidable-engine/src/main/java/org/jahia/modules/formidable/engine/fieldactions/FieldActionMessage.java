package org.jahia.modules.formidable.engine.fieldactions;

import org.json.JSONObject;

import java.util.Locale;

/**
 * What the visitor is told about one field action's refusal, as the response carries it — in the {@code messages}
 * array of the pre-check answer and of a refused submission alike, so the browser anchors both the same way.
 *
 * @param level      {@link Level#ERROR} for a refusal that blocks, {@link Level#WARNING} for one that only warns
 * @param html       the contributor's message, interpolated and escaped server-side: ready to render
 * @param field      the field's node name, what the browser anchors the message on
 * @param actionId   the action node's UUID, for the logs and the tests
 * @param actionType the action's node type, so a page can tell one kind of refusal from another
 */
public record FieldActionMessage(Level level, String html, String field, String actionId, String actionType) {

    /** How the browser shows the message. */
    public enum Level {
        ERROR, WARNING;

        /** The JSON value: lower case, as the page's CSS hooks read it. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("level", level.key());
        json.put("html", html == null ? "" : html);
        json.put("field", field);
        json.put("actionId", actionId);
        json.put("actionType", actionType);
        return json;
    }
}
