package org.jahia.modules.formidable.accounts.security;

import org.jahia.modules.formidable.accounts.TestSupport;
import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.modules.formidable.accounts.messages.VisitorText;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AccountsGuardTest {

    private final AtomicLong now = new AtomicLong(0);

    private AccountsGuard guard(boolean requireCaptcha, int perAddress, int perAccount) {
        AccountsConfigService config = TestSupport.config(requireCaptcha, perAddress, perAccount);
        return new AccountsGuard(config, TestSupport.directory(Set.of("contributor")), now::get);
    }

    private static FormActionContext context(JCRNodeWrapper form) {
        return TestSupport.context(TestSupport.actionNode(Map.of()), form, TestSupport.request("203.0.113.7", null), Map.of(), Map.of());
    }

    @Test
    void aCaptchaProtectedFormOfASiteThatEnabledTheModulePasses() {
        // Verifies the happy path of the form gate.
        assertDoesNotThrow(() -> guard(true, 20, 5).requireProtectedForm(context(TestSupport.form(true, true))));
    }

    @Test
    void aFormWithoutCaptchaIsRefusedGenericallyWithAnUnavailableStatusUnlessTheOperatorAllowedIt() {
        // Verifies the default of the task: required, refused as a configuration error the visitor cannot act on;
        // and the explicit development switch.
        FormActionException refusal = assertThrows(FormActionException.class,
                () -> guard(true, 20, 5).requireProtectedForm(context(TestSupport.form(false, true))));

        assertEquals(503, refusal.getHttpStatus());
        assertEquals(VisitorText.UNAVAILABLE.in(Locale.FRENCH), refusal.getVisitorMessage());
        assertDoesNotThrow(() -> guard(false, 20, 5).requireProtectedForm(context(TestSupport.form(false, true))));
    }

    @Test
    void aSiteThatDidNotEnableTheModuleIsRefusedWhateverTheCaptchaSays() {
        // Verifies the per-site gate: the module must be enabled on the form's site.
        assertEquals(503, assertThrows(FormActionException.class,
                () -> guard(false, 20, 5).requireProtectedForm(context(TestSupport.form(true, false)))).getHttpStatus());
    }

    @Test
    void aFormThatCannotBeInspectedIsRefused() {
        // Verifies fail-closed: no form in the context means no CAPTCHA to be found.
        assertThrows(FormActionException.class, () -> guard(true, 20, 5).requireProtectedForm(context(null)));
    }

    @Test
    void aSignUpIsRefusedOnceTheAddressHasUsedItsAttemptsWhateverTheAccount() throws Exception {
        // Verifies the per-address limit counts every attempt, and answers the same sentence with a 429.
        AccountsGuard guard = guard(true, 3, 100);
        HttpServletRequest request = TestSupport.request("203.0.113.7", null);
        for (int i = 0; i < 3; i++) {
            guard.admitSignUp(request, "user" + i + "@example.com", Locale.ENGLISH);
        }

        FormActionException refusal = assertThrows(FormActionException.class, () -> guard.admitSignUp(request, "another@example.com", Locale.ENGLISH));

        assertEquals(429, refusal.getHttpStatus());
        assertEquals(VisitorText.TOO_MANY_ATTEMPTS.english(), refusal.getVisitorMessage());
        guard.admitSignUp(TestSupport.request("203.0.113.8", null), "another@example.com", Locale.ENGLISH);
    }

    @Test
    void aSignUpIsRefusedOnceAnAccountHasBeenAttemptedTooOftenFromAnyAddress() throws Exception {
        // Verifies the per-account limit on sign-ups, the guard against flooding one mailbox with activation e-mails.
        AccountsGuard guard = guard(true, 100, 2);
        guard.admitSignUp(TestSupport.request("203.0.113.1", null), "Victim@Example.com", Locale.ENGLISH);
        guard.admitSignUp(TestSupport.request("203.0.113.2", null), "victim@example.com ", Locale.ENGLISH);

        assertEquals(429, assertThrows(FormActionException.class,
                () -> guard.admitSignUp(TestSupport.request("203.0.113.3", null), "victim@example.com", Locale.ENGLISH)).getHttpStatus());
    }

    @Test
    void aSignInChargesTheAccountOnlyForFailuresAndASuccessClearsThem() throws Exception {
        // Verifies the sign-in rule: three failures block the account; a success resets the count first.
        AccountsGuard guard = guard(true, 100, 3);
        HttpServletRequest request = TestSupport.request("203.0.113.7", null);
        for (int i = 0; i < 2; i++) {
            guard.admitSignIn(request, "ada@example.com", Locale.ENGLISH);
            guard.signInFailed(request, "ada@example.com");
        }
        guard.admitSignIn(request, "ada@example.com", Locale.ENGLISH);
        guard.signInSucceeded(request, "ada@example.com");
        for (int i = 0; i < 3; i++) {
            guard.admitSignIn(request, "ada@example.com", Locale.ENGLISH);
            guard.signInFailed(request, "ADA@example.com");
        }

        assertEquals(429, assertThrows(FormActionException.class, () -> guard.admitSignIn(request, "ada@example.com", Locale.ENGLISH)).getHttpStatus());
    }

    @Test
    void anAccountThatDoesNotExistIsLimitedExactlyLikeOneThatDoes() throws Exception {
        // Verifies the limit is no oracle: it is keyed by what was typed, existing or not, and says the same thing.
        AccountsGuard guard = guard(true, 100, 1);
        HttpServletRequest request = TestSupport.request("203.0.113.7", null);
        guard.admitSignIn(request, "ghost@example.com", Locale.ENGLISH);
        guard.signInFailed(request, "ghost@example.com");

        FormActionException refusal = assertThrows(FormActionException.class, () -> guard.admitSignIn(request, "ghost@example.com", Locale.ENGLISH));

        assertEquals(VisitorText.TOO_MANY_ATTEMPTS.english(), refusal.getVisitorMessage());
    }

    @Test
    void theWindowEndsAndTheAddressIsAdmittedAgain() throws Exception {
        // Verifies expiry through the guard's own clock.
        AccountsGuard guard = guard(true, 1, 100);
        HttpServletRequest request = TestSupport.request("203.0.113.7", null);
        guard.admitSignIn(request, "a@example.com", Locale.ENGLISH);
        assertThrows(FormActionException.class, () -> guard.admitSignIn(request, "a@example.com", Locale.ENGLISH));

        now.addAndGet(601_000);

        assertDoesNotThrow(() -> guard.admitSignIn(request, "a@example.com", Locale.ENGLISH));
    }

    @Test
    void theVerificationEndpointIsLimitedPerAddress() {
        // Verifies the servlet's brake, kept apart from the actions' counts.
        AccountsGuard guard = guard(true, 2, 5);
        HttpServletRequest request = TestSupport.request("203.0.113.7", null);

        assertTrue(guard.admitVerification(request));
        assertTrue(guard.admitVerification(request));
        assertFalse(guard.admitVerification(request));
        assertTrue(guard.admitVerification(TestSupport.request("203.0.113.9", null)));
    }

    @Test
    void anUnknownAddressAndABlankAccountDoNotBreakTheCounting() throws Exception {
        // Verifies the edge cases: no remote address, no account typed (counted per address only).
        AccountsGuard guard = guard(true, 2, 1);
        HttpServletRequest nobody = TestSupport.request(null, null);

        guard.admitSignUp(nobody, null, Locale.ENGLISH);
        guard.admitSignUp(nobody, "  ", Locale.ENGLISH);
        assertThrows(FormActionException.class, () -> guard.admitSignUp(nobody, "", Locale.ENGLISH));
        guard.signInFailed(nobody, null);
        guard.signInSucceeded(nobody, "");
    }

    @Test
    void anActionLastSavedBySomeoneWithoutThePermissionIsRefusedAsAConfigurationError() {
        // Verifies finding 6 of jahia-private#5909: any editor of the form can add or change the action, but it only
        // runs when its last author holds formidableAccountsActions on it.
        JCRNodeWrapper action = TestSupport.actionNode(Map.of("jcr:lastModifiedBy", "contributor"));
        FormActionContext byContributor = TestSupport.context(action, TestSupport.form(true, true), TestSupport.request("203.0.113.7", null), Map.of(), Map.of());

        FormActionException refusal = assertThrows(FormActionException.class, () -> guard(true, 20, 5).requireProtectedForm(byContributor));

        assertEquals(503, refusal.getHttpStatus());
        assertEquals(VisitorText.UNAVAILABLE.in(Locale.FRENCH), refusal.getVisitorMessage());
    }

    @Test
    void anActionWithNoAuthorIsRefused() {
        // Verifies fail-closed: no jcr:lastModifiedBy means nobody authorised it.
        JCRNodeWrapper action = TestSupport.actionNode(Map.of());
        try {
            when(action.hasProperty("jcr:lastModifiedBy")).thenReturn(false);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        FormActionContext context = TestSupport.context(action, TestSupport.form(true, true), TestSupport.request("203.0.113.7", null), Map.of(), Map.of());

        assertThrows(FormActionException.class, () -> guard(true, 20, 5).requireProtectedForm(context));
    }

    @Test
    void failedSignInsFromOneAddressNeverLockTheOwnerOutFromAnother() throws Exception {
        // Verifies finding 5 of jahia-private#5909: the account counter is per account AND address, so a stranger's
        // wrong guesses exhaust the stranger's counter only.
        AccountsGuard guard = guard(true, 100, 3);
        HttpServletRequest stranger = TestSupport.request("198.51.100.9", null);
        for (int i = 0; i < 3; i++) {
            guard.admitSignIn(stranger, "ada@example.com", Locale.ENGLISH);
            guard.signInFailed(stranger, "ada@example.com");
        }

        assertEquals(429, assertThrows(FormActionException.class, () -> guard.admitSignIn(stranger, "ada@example.com", Locale.ENGLISH)).getHttpStatus());
        assertDoesNotThrow(() -> guard.admitSignIn(TestSupport.request("203.0.113.7", null), "ada@example.com", Locale.ENGLISH));
    }
}
