package org.jahia.modules.formidable.engine.actions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates submitted text values against the field semantics and constraints
 * collected from JCR before multipart parsing starts.
 */
final class FieldValidator {

    private static final Logger log = LoggerFactory.getLogger(FieldValidator.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private static final DateTimeFormatter DATETIME_LOCAL_FMT =
            new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE)
                    .appendLiteral('T')
                    .appendPattern("HH:mm[:ss[.SSS]]")
                    .toFormatter();

    private FieldValidator() {
    }

    static void validateTextField(
            String fieldName,
            String value,
            FormDataParser.FieldMetadata metadata
    ) throws FormDataParser.ParseException {
        if (value == null || value.isEmpty()) {
            return;
        }

        Set<String> choices = metadata.allowedChoices(fieldName);
        if (!choices.isEmpty() && !choices.contains(value)) {
            log.warn("[FieldValidator] Rejected field '{}': value not in allowed choices", fieldName);
            throw new FormDataParser.ParseException(
                    "Field '" + fieldName + "': submitted value is not an allowed choice.", 400, true);
        }

        FormDataParser.FieldInfo fieldInfo = metadata.field(fieldName);
        if (fieldInfo != null) {
            if (fieldInfo.emailField()) {
                validateEmail(fieldName, value);
            }
            if (fieldInfo.dateField()) {
                validateDate(fieldName, value);
            }
            if (fieldInfo.datetimeLocalField()) {
                validateDatetimeLocal(fieldName, value);
            }
            if (fieldInfo.colorField()) {
                validateColor(fieldName, value);
            }
        }

        FormDataParser.FieldConstraints constraints = metadata.constraints(fieldName);
        if (constraints != null) {
            validateConstraints(fieldName, value, constraints, fieldInfo);
        }
    }

    private static void validateConstraints(
            String fieldName,
            String value,
            FormDataParser.FieldConstraints constraints,
            FormDataParser.FieldInfo fieldInfo
    ) throws FormDataParser.ParseException {
        if (constraints.minLength() >= 0 && value.length() < constraints.minLength()) {
            log.warn("[FieldValidator] Rejected field '{}': too short ({} < {})",
                    fieldName, value.length(), constraints.minLength());
            throw new FormDataParser.ParseException(
                    "Field '" + fieldName + "': value too short (min " + constraints.minLength() + " chars).",
                    400, true);
        }
        if (constraints.maxLength() >= 0 && value.length() > constraints.maxLength()) {
            log.warn("[FieldValidator] Rejected field '{}': too long ({} > {})",
                    fieldName, value.length(), constraints.maxLength());
            throw new FormDataParser.ParseException(
                    "Field '" + fieldName + "': value too long (max " + constraints.maxLength() + " chars).",
                    400, true);
        }
        if (constraints.pattern() != null && !constraints.pattern().isBlank()) {
            try {
                if (!value.matches(constraints.pattern())) {
                    log.warn("[FieldValidator] Rejected field '{}': value does not match pattern", fieldName);
                    throw new FormDataParser.ParseException(
                            "Field '" + fieldName + "': value does not match required format.", 400, true);
                }
            } catch (PatternSyntaxException e) {
                log.warn("[FieldValidator] Invalid pattern on field '{}': {}", fieldName, e.getMessage());
            }
        }
        if (fieldInfo != null && constraints.minDate() != null) {
            validateDateBound(fieldName, value, constraints.minDate(), fieldInfo, true);
        }
        if (fieldInfo != null && constraints.maxDate() != null) {
            validateDateBound(fieldName, value, constraints.maxDate(), fieldInfo, false);
        }
    }

    private static void validateDateBound(
            String fieldName,
            String value,
            String bound,
            FormDataParser.FieldInfo fieldInfo,
            boolean minBound
    ) throws FormDataParser.ParseException {
        try {
            if (fieldInfo.dateField()) {
                LocalDate submitted = LocalDate.parse(value);
                LocalDate limit = LocalDate.parse(bound);
                boolean violation = minBound ? submitted.isBefore(limit) : submitted.isAfter(limit);
                if (violation) {
                    log.warn("[FieldValidator] Rejected field '{}': date {} bound '{}'",
                            fieldName, minBound ? "before min" : "after max", bound);
                    throw new FormDataParser.ParseException(
                            "Field '" + fieldName + "': date is "
                                    + (minBound ? "before minimum" : "after maximum") + ".",
                            400, true);
                }
            } else if (fieldInfo.datetimeLocalField()) {
                LocalDateTime submitted = LocalDateTime.parse(value, DATETIME_LOCAL_FMT);
                LocalDateTime limit = LocalDateTime.parse(bound, DATETIME_LOCAL_FMT);
                boolean violation = minBound ? submitted.isBefore(limit) : submitted.isAfter(limit);
                if (violation) {
                    log.warn("[FieldValidator] Rejected field '{}': datetime {} bound '{}'",
                            fieldName, minBound ? "before min" : "after max", bound);
                    throw new FormDataParser.ParseException(
                            "Field '" + fieldName + "': datetime is "
                                    + (minBound ? "before minimum" : "after maximum") + ".",
                            400, true);
                }
            }
        } catch (DateTimeParseException e) {
            // Type-specific validation already handles malformed submitted values.
        }
    }

    private static void validateEmail(String fieldName, String value) throws FormDataParser.ParseException {
        String[] parts = value.contains(",") ? value.split(",") : new String[]{value};
        for (String part : parts) {
            String email = part.trim();
            if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
                log.warn("[FieldValidator] Rejected field '{}': invalid email '{}'", fieldName, email);
                throw new FormDataParser.ParseException(
                        "Field '" + fieldName + "': invalid email format.", 400, true);
            }
        }
    }

    private static void validateDate(String fieldName, String value) throws FormDataParser.ParseException {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("[FieldValidator] Rejected field '{}': invalid date '{}'", fieldName, value);
            throw new FormDataParser.ParseException(
                    "Field '" + fieldName + "': invalid date format (expected yyyy-MM-dd).", 400, true);
        }
    }

    private static void validateDatetimeLocal(String fieldName, String value) throws FormDataParser.ParseException {
        try {
            LocalDateTime.parse(value, DATETIME_LOCAL_FMT);
        } catch (DateTimeParseException e) {
            log.warn("[FieldValidator] Rejected field '{}': invalid datetime-local '{}'", fieldName, value);
            throw new FormDataParser.ParseException(
                    "Field '" + fieldName + "': invalid datetime format.", 400, true);
        }
    }

    private static void validateColor(String fieldName, String value) throws FormDataParser.ParseException {
        if (!COLOR_PATTERN.matcher(value).matches()) {
            log.warn("[FieldValidator] Rejected field '{}': invalid color '{}'", fieldName, value);
            throw new FormDataParser.ParseException(
                    "Field '" + fieldName + "': invalid color format (expected #rrggbb).", 400, true);
        }
    }
}
