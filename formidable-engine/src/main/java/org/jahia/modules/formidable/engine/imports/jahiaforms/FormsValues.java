package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the import writes into {@code data} for a Forms answer (docs/architecture/forms-import.md, "The
 * fields", third column): most values unchanged, a date as {@code yyyy-MM-dd}, a country as its code, a
 * rating as its number, a matrix as one line per row, a consent as {@code true}; the file JSON and the
 * password placeholder dropped.
 */
final class FormsValues {

    static final String PASSWORD_PLACEHOLDER = "**********";
    private static final Duration HALF_DAY = Duration.ofHours(12);
    private static final String RENDERER_NAME = "rendererName";

    /** The converted values, and a note when something was dropped or could not be converted. */
    record Converted(List<String> values, String note) {
        static Converted of(List<String> values) {
            return new Converted(values, null);
        }

        static Converted dropped(String note) {
            return new Converted(List.of(), note);
        }
    }

    private FormsValues() {
    }

    /**
     * @param kind the Forms kind of the field, {@code datePicker}; null when the field is unknown, which
     *             keeps the values as they are
     */
    static Converted convert(String kind, List<String> values, boolean consent) {
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
            case "acceptTermCheckbox" -> consent ? Converted.of(List.of("true")) : Converted.of(values);
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
     * The day the visitor chose, from the UTC instant Forms stored for its local midnight: the instant
     * plus twelve hours, truncated to the UTC day. Right for every offset above −12 and up to +12 hours,
     * one day early at +13 and +14 (docs/architecture/forms-import.md, "The date rule").
     */
    static Optional<String> date(String isoInstant) {
        try {
            Instant instant = Instant.parse(isoInstant);
            return Optional.of(instant.plus(HALF_DAY).atOffset(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** {@code {"country":{"key":"FR","value":"France"},"rendererName":"country"}} gives {@code FR}. */
    static Optional<String> country(String json) {
        return object(json).map(o -> o.optJSONObject("country")).map(c -> c.optString("key", null));
    }

    /** {@code {"rating":4,"rendererName":"rating"}} gives {@code 4}; a bare number is kept. */
    static Optional<String> rating(String value) {
        Optional<JSONObject> object = object(value);
        if (object.isEmpty()) {
            return value.matches("\\d+(\\.\\d+)?") ? Optional.of(value) : Optional.empty();
        }
        JSONObject rating = object.get();
        if (!rating.has("rating")) {
            return Optional.empty();
        }
        Object number = rating.get("rating");
        return Optional.of(number instanceof JSONObject nested ? nested.optString("value", nested.toString()) : String.valueOf(number));
    }

    /**
     * {@code {"matrixRadios":{"row1":"col2","row2":"col1"},"rendererName":"matrixRadios"}} gives
     * {@code row1: col2} and {@code row2: col1} on two lines; a row with several answers lists them.
     */
    static Optional<String> matrix(String json) {
        Optional<JSONObject> object = object(json);
        if (object.isEmpty()) {
            return Optional.empty();
        }
        JSONObject matrix = object.get();
        String renderer = matrix.optString(RENDERER_NAME, null);
        JSONObject rows = renderer == null ? null : matrix.optJSONObject(renderer);
        if (rows == null) {
            return Optional.empty();
        }
        List<String> lines = new ArrayList<>();
        for (String row : rows.keySet()) {
            Object answer = rows.get(row);
            lines.add(row + ": " + (answer instanceof JSONArray several ? join(several) : String.valueOf(answer)));
        }
        return Optional.of(String.join("\n", lines));
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
