package org.jahia.modules.formidable.accounts.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void theDefaultRuleWantsDigitLowerUpperSymbolAndEightToSixtyFourCharacters() {
        // Verifies the default of the old registration module, one requirement at a time.
        assertTrue(PasswordPolicy.accepts("Abcdef1!", null));
        assertTrue(PasswordPolicy.accepts("Abcdef1!", "  "));
        assertFalse(PasswordPolicy.accepts("Abcdef1", null), "seven characters, no symbol");
        assertFalse(PasswordPolicy.accepts("abcdef1!", null), "no upper-case letter");
        assertFalse(PasswordPolicy.accepts("ABCDEF1!", null), "no lower-case letter");
        assertFalse(PasswordPolicy.accepts("Abcdefg!", null), "no digit");
        assertFalse(PasswordPolicy.accepts("Abcdefg1", null), "no symbol");
        assertFalse(PasswordPolicy.accepts("Aa1!" + "x".repeat(61), null), "sixty-five characters");
        assertTrue(PasswordPolicy.accepts("Aa1!" + "x".repeat(60), null), "sixty-four characters");
    }

    @Test
    void aContributorRuleReplacesTheDefault() {
        // Verifies the per-action expression: it is matched against the whole password.
        assertTrue(PasswordPolicy.accepts("hunter22", "[a-z]+[0-9]+"));
        assertFalse(PasswordPolicy.accepts("hunter22!", "[a-z]+[0-9]+"));
    }

    @Test
    void nothingIsAcceptedForAnEmptyNullOrOverlongPassword() {
        // Verifies the hard cap before any matching: the password is hashed server-side.
        assertFalse(PasswordPolicy.accepts(null, ".*"));
        assertFalse(PasswordPolicy.accepts("", ".*"));
        assertFalse(PasswordPolicy.accepts("a".repeat(PasswordPolicy.MAX_LENGTH + 1), ".*"));
        assertTrue(PasswordPolicy.accepts("a".repeat(PasswordPolicy.MAX_LENGTH), ".*"));
    }

    @Test
    void anInvalidExpressionRefusesEveryPasswordInsteadOfFailing() {
        // Verifies a mistyped rule is a configuration error that locks sign-ups out, never an exception or an open door.
        assertFalse(PasswordPolicy.accepts("Abcdef1!", "([a-z"));
    }

    @Test
    void aCatastrophicExpressionEndsTheAttemptInsteadOfTheThread() {
        // Verifies the evaluation budget: a contributor's (a+)+$ on a hostile input costs a bounded number of
        // character reads, and the password is refused.
        long started = System.nanoTime();
        boolean accepted = PasswordPolicy.accepts("a".repeat(100) + "!", "^(a+)+$");

        assertFalse(accepted);
        assertTrue((System.nanoTime() - started) < 5_000_000_000L, "the match did not give up");
    }
}
