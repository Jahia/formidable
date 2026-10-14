package org.jahia.modules.formidable.accounts.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserNamesTest {

    /** The platform's default user-name syntax, as a stand-in for JahiaUserManagerService#isUsernameSyntaxCorrect. */
    private static final Pattern DEFAULT = Pattern.compile("[0-9a-z_A-Z\\-.@]{2,}");
    private static final Predicate<String> PLATFORM = name -> DEFAULT.matcher(name).matches();

    @Test
    void anEmailBecomesItsLowerCaseSelf() {
        // Verifies two sign-ups differing by case are one account.
        assertEquals(Optional.of("ada.lovelace@example.com"), UserNames.forRegistration("  Ada.Lovelace@Example.COM ", PLATFORM));
    }

    @Test
    void charactersThePlatformRefusesAreReplacedAndTheResultIsCheckedAgain() {
        // Verifies the sanitising: a plus-address becomes a valid name; what cannot be made valid gives nothing.
        assertEquals(Optional.of("ada_tag@example.com"), UserNames.forRegistration("ada+tag@example.com", PLATFORM));
        assertEquals(Optional.of("jos_@example.com"), UserNames.forRegistration("josé@example.com", PLATFORM));
        assertTrue(UserNames.forRegistration("ada@example.com", name -> false).isEmpty(), "the platform check has the last word");
        assertTrue(UserNames.forRegistration("", PLATFORM).isEmpty());
        assertTrue(UserNames.forRegistration(null, PLATFORM).isEmpty());
        assertTrue(UserNames.forRegistration("a".repeat(300), PLATFORM).isEmpty());
    }

    @Test
    void reservedAccountsAreNeverUsedByAPublicForm() {
        // Verifies root and guest are not visitors: not for sign-up, not for sign-in, in any case.
        assertTrue(UserNames.forRegistration("root", PLATFORM).isEmpty());
        assertTrue(UserNames.forSignIn("root", PLATFORM).isEmpty());
        assertTrue(UserNames.forSignIn(" Guest ", PLATFORM).isEmpty());
    }

    @Test
    void aSignInAppliesTheEmailRuleToAnAddressAndLeavesAUserNameAsTyped() {
        // Verifies the sign-in rule: an address is lower-cased as at registration, a user name is kept.
        assertEquals(Optional.of("ada@example.com"), UserNames.forSignIn("Ada@Example.com", PLATFORM));
        assertEquals(Optional.of("Ada_L"), UserNames.forSignIn(" Ada_L ", PLATFORM));
        assertTrue(UserNames.forSignIn("not valid!", PLATFORM).isEmpty());
        assertTrue(UserNames.forSignIn(null, PLATFORM).isEmpty());
        assertTrue(UserNames.forSignIn("   ", PLATFORM).isEmpty());
    }
}
