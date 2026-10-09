package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * What the import writes into {@code data} for a Forms answer (docs/architecture/forms-import.md, "The
 * fields", third column): most values unchanged, a date as {@code yyyy-MM-dd}, a country as its code, a
 * rating as its number, a matrix as one line per row, a ticked consent as {@code true}; the file JSON and
 * the password placeholder dropped. The JSON shapes are those the Forms directives submit
 * ({@code forms-core/src/main/resources/fcnt_*Definition/js}).
 */
final class FormsValues {

    static final String PASSWORD_PLACEHOLDER = "**********";
    private static final Duration HALF_DAY = Duration.ofHours(12);
    private static final String RENDERER_NAME = "rendererName";
    /** The number of a rating, in the JSON the rating directive submits with its renderer, css and type. */
    private static final String RATING_VALUE = "value";
    private static final String ACCEPTED = "true";
    private static final String REFUSED = "false";

    /** The converted values, and a note when something was dropped or could not be converted. */
    record Converted(List<String> values, String note) {
        static Converted of(List<String> values) {
            return new Converted(values, null);
        }

        static Converted dropped(String note) {
            return new Converted(List.of(), note);
        }
    }

    /**
     * The texts an accept-terms box submits: its {@code yes} label when ticked, its {@code no} label when
     * not (forms-extended-inputs, {@code ng-false-value="'{{input.no}}'"}), each in the languages of the form.
     */
    record ConsentLabels(Set<String> accepted, Set<String> refused) {
        static final ConsentLabels UNKNOWN = new ConsentLabels(Set.of(), Set.of());
    }

    private FormsValues() {
    }

    /**
     * @param kind the Forms kind of the field, {@code datePicker}; null when the field is unknown, which
     *             keeps the values as they are
     * @param consent the labels of an accept-terms box, for that kind alone; null when the definition is gone
     */
    static Converted convert(String kind, List<String> values, ConsentLabels consent) {
        if (kind == null) {
            return Converted.of(values);
        }
        return switch (kind) {
            case "password" -> Converted.dropped("password dropped");
            case "fileUpload" -> Converted.of(List.of());
            case "datePicker", "simpleDate" -> each(values, FormsValues::date);
            case "countryList" -> each(values, FormsValues::country);
            case "rating" -> each(values, FormsValues::rating);
            case "matrixRadios", "matrixCheckBoxes" -> each(values, FormsValues::matrix);
            case "acceptTermCheckbox" -> consent(values, consent == null ? ConsentLabels.UNKNOWN : consent);
            default -> Converted.of(values);
        };
    }

    private static Converted each(List<String> values, Function<String, Optional<String>> one) {
        List<String> converted = new ArrayList<>();
        int failed = 0;
        for (String value : values) {
            Optional<String> result = one.apply(value);
            if (result.isPresent()) {
                converted.add(result.get());
            } else {
                converted.add(value);
                failed++;
            }
        }
        return new Converted(converted, failed == 0 ? null : failed + " value(s) kept as stored because they could not be converted");
    }

    /**
     * The day the visitor chose, from the UTC instant Forms stored for its local midnight
     * ({@code moment().toISOString()}): the instant plus twelve hours, truncated to the UTC day. Right for
     * every offset above −12 and up to +12 hours, one day early at +13 and +14
     * (docs/architecture/forms-import.md, "The date rule"). A value that is already a day is kept.
     */
    static Optional<String> date(String stored) {
        if (stored == null) {
            return Optional.empty();
        }
        String value = stored.trim();
        try {
            return Optional.of(LocalDate.parse(value).toString());
        } catch (DateTimeParseException e) {
            // not a bare day: an instant, with Z or an offset
        }
        try {
            Instant instant = OffsetDateTime.parse(value).toInstant();
            return Optional.of(instant.plus(HALF_DAY).atOffset(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** {@code {"country":{"key":"FR","name":"France"},"rendererName":"country"}} gives {@code FR}. */
    static Optional<String> country(String json) {
        return object(json).map(o -> o.optJSONObject("country")).map(c -> c.optString("key", null));
    }

    /**
     * {@code {"rendererName":"rating","css":"fa-star rated","type":"…","value":4}} gives {@code 4}
     * ({@code ratingDefinition.directive.jsp}, {@code scope.rate}); a bare number is kept.
     */
    static Optional<String> rating(String value) {
        Optional<JSONObject> object = object(value);
        if (object.isEmpty()) {
            return value.matches("\\d+(\\.\\d+)?") ? Optional.of(value) : Optional.empty();
        }
        JSONObject rating = object.get();
        if (!rating.has(RATING_VALUE)) {
            return Optional.empty();
        }
        Object number = rating.get(RATING_VALUE);
        return Optional.of(number instanceof JSONObject nested ? nested.optString(RATING_VALUE, nested.toString()) : String.valueOf(number));
    }

    /**
     * {@code {"Price":"Good","Service":"Poor","rendererName":"matrixRadios"}} gives {@code Price: Good} and
     * {@code Service: Poor} on two lines: the matrix directives keep one key per row beside the renderer name
     * ({@code ng-model="input.value[rowv.key]"}); a row with several answers lists them.
     */
    static Optional<String> matrix(String json) {
        Optional<JSONObject> object = object(json);
        if (object.isEmpty() || !object.get().has(RENDERER_NAME)) {
            return Optional.empty();
        }
        JSONObject matrix = object.get();
        List<String> lines = new ArrayList<>();
        for (String row : rowsInTextOrder(matrix, json)) {
            if (RENDERER_NAME.equals(row)) {
                continue;
            }
            Object answer = matrix.get(row);
            lines.add(row + ": " + (answer instanceof JSONArray several ? join(several) : String.valueOf(answer)));
        }
        return lines.isEmpty() ? Optional.empty() : Optional.of(String.join("\n", lines));
    }

    /**
     * A ticked box stores {@code true}; a box left unticked, which Forms stored as its refused label,
     * stores nothing; a text that is neither label is kept, with a note.
     */
    private static Converted consent(List<String> values, ConsentLabels labels) {
        List<String> converted = new ArrayList<>();
        int unknown = 0;
        for (String value : values) {
            String text = value == null ? "" : value.trim();
            if (text.isEmpty() || REFUSED.equalsIgnoreCase(text) || labels.refused().contains(text)) {
                continue;
            }
            if (ACCEPTED.equalsIgnoreCase(text) || labels.accepted().contains(text)) {
                converted.add(ACCEPTED);
            } else {
                converted.add(value);
                unknown++;
            }
        }
        return new Converted(converted, unknown == 0 ? null
                : unknown + " value(s) kept as stored because they are neither the accepted nor the refused label");
    }

    /** The rows as the JSON text lists them: a {@link JSONObject} keeps no order of its own. */
    private static List<String> rowsInTextOrder(JSONObject rows, String json) {
        List<String> ordered = new ArrayList<>(rows.keySet());
        ordered.sort(java.util.Comparator.comparingInt(row -> json.indexOf(JSONObject.quote(row))));
        return ordered;
    }

    private static String join(JSONArray values) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            items.add(String.valueOf(values.get(i)));
        }
        return String.join(", ", items);
    }

    private static Optional<JSONObject> object(String json) {
        if (json == null || !json.trim().startsWith("{")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JSONObject(json));
        } catch (JSONException e) {
            return Optional.empty();
        }
    }
}
