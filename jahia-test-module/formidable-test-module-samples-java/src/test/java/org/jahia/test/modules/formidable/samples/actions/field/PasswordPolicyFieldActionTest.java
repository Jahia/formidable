package org.jahia.test.modules.formidable.samples.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The password policy of the samples: its opt-in to sensitive values, its one rule, and a detail that never holds the value. */
class PasswordPolicyFieldActionTest {

    private static FieldActionResult judge(String value) {
        return new PasswordPolicyFieldAction().execute(null, new FieldActionRequest("form-1", "password", value, Locale.ENGLISH));
    }

    @Test
    void itOptsInToSensitiveValues() {
        // Verifies the opt-in: without it, the engine never runs the action on a password field.
        assertTrue(new PasswordPolicyFieldAction().consumesSensitiveValues());
    }

    @Test
    void aValueWithADigitPassesAndAValueWithoutOneIsRefusedByItsRule() {
        // Verifies the rule the suite drives at submission, and that the detail of the refusal names the rule only.
        assertEquals(FieldActionResult.Verdict.ACCEPT, judge("correct-horse-7").verdict());
        FieldActionResult refused = judge("correct-horse");
        assertEquals(FieldActionResult.Verdict.REJECT, refused.verdict());
        assertEquals(PasswordPolicyFieldAction.NO_DIGIT, refused.detail());
        assertFalse(refused.detail().contains("correct-horse"));
    }
}
