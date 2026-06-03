package org.jahia.modules.formidable.engine.actions;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldValidatorTest {

    @Test
    void acceptsValidEmailForEmailField() {
        // Verifies the positive email-validation path for an email field.
        FormDataParser.FieldMetadata metadata = metadata("email", fieldInfo(
                "fmdb:inputEmail", false, false, false, true, false, false, false, null
        ));

        // Expected outcome: a valid email passes validation.
        assertDoesNotThrow(() -> FieldValidator.validateTextField("email", "alice@example.com", metadata));
    }

    @Test
    void rejectsInvalidEmailForEmailField() {
        // Verifies type-specific validation: an email field must reject a value
        // that does not match the supported email format.
        FormDataParser.FieldMetadata metadata = metadata("email", fieldInfo(
                "fmdb:inputEmail", false, false, false, true, false, false, false, null
        ));

        // Expected outcome: the validator rejects the malformed email value.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("email", "not-an-email", metadata));
    }

    @Test
    void acceptsValidDateWithinConfiguredBounds() {
        // Verifies the positive date-validation path with an in-range date.
        FormDataParser.FieldMetadata metadata = metadata("startDate", fieldInfo(
                "fmdb:inputDate",
                false, false, false, false, true, false, false,
                new FormDataParser.FieldConstraints(false, -1, -1, null, "2026-01-10", "2026-01-20")
        ));

        // Expected outcome: the valid in-range date passes validation.
        assertDoesNotThrow(() -> FieldValidator.validateTextField("startDate", "2026-01-15", metadata));
    }

    @Test
    void rejectsDateBeforeConfiguredMinimum() {
        // Verifies date-bound enforcement: a submitted date before the configured minimum
        // must be rejected for a date field.
        FormDataParser.FieldMetadata metadata = metadata("startDate", fieldInfo(
                "fmdb:inputDate",
                false, false, false, false, true, false, false,
                new FormDataParser.FieldConstraints(false, -1, -1, null, "2026-01-10", null)
        ));

        // Expected outcome: the validator rejects the value because it is before the min date.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("startDate", "2026-01-09", metadata));
    }

    @Test
    void rejectsDateAfterConfiguredMaximum() {
        // Verifies date-bound enforcement on the maximum side.
        FormDataParser.FieldMetadata metadata = metadata("startDate", fieldInfo(
                "fmdb:inputDate",
                false, false, false, false, true, false, false,
                new FormDataParser.FieldConstraints(false, -1, -1, null, null, "2026-01-20")
        ));

        // Expected outcome: a date after the configured maximum is rejected.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("startDate", "2026-01-21", metadata));
    }

    @Test
    void rejectsMalformedDateForDateField() {
        // Verifies date format validation for date fields.
        FormDataParser.FieldMetadata metadata = metadata("startDate", fieldInfo(
                "fmdb:inputDate", false, false, false, false, true, false, false, null
        ));

        // Expected outcome: a malformed yyyy-MM-dd value is rejected.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("startDate", "01/15/2026", metadata));
    }

    @Test
    void acceptsValidDatetimeLocalWithinConfiguredBounds() {
        // Verifies the positive datetime-local validation path with an in-range timestamp.
        FormDataParser.FieldMetadata metadata = metadata("appointment", fieldInfo(
                "fmdb:inputDatetimeLocal",
                false, false, false, false, false, true, false,
                new FormDataParser.FieldConstraints(false, -1, -1, null, "2026-01-10T09:00", "2026-01-10T18:00")
        ));

        // Expected outcome: the valid in-range datetime-local value passes validation.
        assertDoesNotThrow(() -> FieldValidator.validateTextField("appointment", "2026-01-10T10:30", metadata));
    }

    @Test
    void rejectsMalformedDatetimeLocalForDatetimeLocalField() {
        // Verifies datetime-local format validation.
        FormDataParser.FieldMetadata metadata = metadata("appointment", fieldInfo(
                "fmdb:inputDatetimeLocal", false, false, false, false, false, true, false, null
        ));

        // Expected outcome: a malformed datetime-local value is rejected.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("appointment", "2026-01-10 10:30", metadata));
    }

    @Test
    void acceptsValidColorForColorField() {
        // Verifies the positive color-validation path for #RRGGBB values.
        FormDataParser.FieldMetadata metadata = metadata("themeColor", fieldInfo(
                "fmdb:inputColor", false, false, false, false, false, false, true, null
        ));

        // Expected outcome: a valid six-digit hexadecimal color passes validation.
        assertDoesNotThrow(() -> FieldValidator.validateTextField("themeColor", "#A1b2C3", metadata));
    }

    @Test
    void rejectsInvalidColorForColorField() {
        // Verifies color format validation for color fields.
        FormDataParser.FieldMetadata metadata = metadata("themeColor", fieldInfo(
                "fmdb:inputColor", false, false, false, false, false, false, true, null
        ));

        // Expected outcome: an invalid color format is rejected.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("themeColor", "#FFF", metadata));
    }

    @Test
    void acceptsMatchingRegexConstraint() {
        // Verifies the positive regex-constraint path.
        FormDataParser.FieldMetadata metadata = metadata("username", fieldInfo(
                "fmdb:inputText",
                false, false, false, false, false, false, false,
                new FormDataParser.FieldConstraints(false, -1, -1, "^[a-z]+$", null, null)
        ));

        // Expected outcome: a value matching the configured pattern passes validation.
        assertDoesNotThrow(() -> FieldValidator.validateTextField("username", "alice", metadata));
    }

    @Test
    void rejectsNonMatchingRegexConstraint() {
        // Verifies the negative regex-constraint path.
        FormDataParser.FieldMetadata metadata = metadata("username", fieldInfo(
                "fmdb:inputText",
                false, false, false, false, false, false, false,
                new FormDataParser.FieldConstraints(false, -1, -1, "^[a-z]+$", null, null)
        ));

        // Expected outcome: a value not matching the configured pattern is rejected.
        FormDataParser.ParseException error = assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("username", "alice123", metadata));

        assertEquals(FormDataParser.ParseException.FailureType.VALIDATION, error.failureType());
    }

    @Test
    void rejectsInvalidRegexPatternAsConfigurationError() {
        // Verifies fail-closed validation: an invalid admin-configured regex pattern
        // must be treated as a form configuration error, not silently ignored.
        FormDataParser.FieldMetadata metadata = metadata("username", fieldInfo(
                "fmdb:inputText",
                false, false, false, false, false, false, false,
                new FormDataParser.FieldConstraints(false, -1, -1, "[", null, null)
        ));

        FormDataParser.ParseException error = assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("username", "alice", metadata));

        assertEquals(FormDataParser.ParseException.FailureType.CONFIGURATION, error.failureType());
    }

    @Test
    void rejectsValueShorterThanConfiguredMinimumLength() {
        // Verifies minimum-length enforcement.
        FormDataParser.FieldMetadata metadata = metadata("username", fieldInfo(
                "fmdb:inputText",
                false, false, false, false, false, false, false,
                new FormDataParser.FieldConstraints(false, 3, -1, null, null, null)
        ));

        // Expected outcome: a value shorter than the configured minimum is rejected.
        FormDataParser.ParseException error = assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("username", "ab", metadata));

        assertEquals(FormDataParser.ParseException.FailureType.VALIDATION, error.failureType());
    }

    @Test
    void rejectsValueLongerThanConfiguredMaximumLength() {
        // Verifies maximum-length enforcement.
        FormDataParser.FieldMetadata metadata = metadata("username", fieldInfo(
                "fmdb:inputText",
                false, false, false, false, false, false, false,
                new FormDataParser.FieldConstraints(false, -1, 5, null, null, null)
        ));

        // Expected outcome: a value longer than the configured maximum is rejected.
        FormDataParser.ParseException error = assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("username", "abcdef", metadata));

        assertEquals(FormDataParser.ParseException.FailureType.VALIDATION, error.failureType());
    }

    private static FormDataParser.FieldMetadata metadata(String fieldName, FormDataParser.FieldInfo info) {
        return new FormDataParser.FieldMetadata(Map.of(fieldName, info));
    }

    private static FormDataParser.FieldInfo fieldInfo(
            String nodeType,
            boolean nonSubmittable,
            boolean choiceField,
            boolean fileField,
            boolean emailField,
            boolean dateField,
            boolean datetimeLocalField,
            boolean colorField,
            FormDataParser.FieldConstraints constraints
    ) {
        return new FormDataParser.FieldInfo(
                nodeType,
                nonSubmittable,
                choiceField,
                fileField,
                emailField,
                dateField,
                datetimeLocalField,
                colorField,
                Set.of(),
                Set.of(),
                constraints
        );
    }
}
