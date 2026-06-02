package org.jahia.modules.formidable.engine.actions;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldValidatorTest {

    @Test
    void rejectsInvalidEmailForEmailField() {
        // Verifies type-specific validation: an email field must reject a value
        // that does not match the supported email format.
        FormDataParser.FieldMetadata metadata = new FormDataParser.FieldMetadata(
                Map.of("email", new FormDataParser.FieldInfo(
                        "fmdb:inputEmail",
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false,
                        Set.of(),
                        Set.of(),
                        null
                ))
        );

        // Expected outcome: the validator rejects the malformed email value.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("email", "not-an-email", metadata));
    }

    @Test
    void rejectsDateBeforeConfiguredMinimum() {
        // Verifies date-bound enforcement: a submitted date before the configured minimum
        // must be rejected for a date field.
        FormDataParser.FieldMetadata metadata = new FormDataParser.FieldMetadata(
                Map.of("startDate", new FormDataParser.FieldInfo(
                        "fmdb:inputDate",
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        Set.of(),
                        Set.of(),
                        new FormDataParser.FieldConstraints(false, -1, -1, null, "2026-01-10", null)
                ))
        );

        // Expected outcome: the validator rejects the value because it is before the min date.
        assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("startDate", "2026-01-09", metadata));
    }

    @Test
    void rejectsInvalidRegexPatternAsConfigurationError() {
        // Verifies fail-closed validation: an invalid admin-configured regex pattern
        // must be treated as a form configuration error, not silently ignored.
        FormDataParser.FieldMetadata metadata = new FormDataParser.FieldMetadata(
                Map.of("username", new FormDataParser.FieldInfo(
                        "fmdb:inputText",
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        Set.of(),
                        Set.of(),
                        new FormDataParser.FieldConstraints(false, -1, -1, "[", null, null)
                ))
        );

        FormDataParser.ParseException error = assertThrows(FormDataParser.ParseException.class,
                () -> FieldValidator.validateTextField("username", "alice", metadata));

        assertEquals(FormDataParser.ParseException.FailureType.CONFIGURATION, error.failureType());
    }
}
