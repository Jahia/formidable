package org.jahia.modules.formidable.accounts.action;

import org.jahia.modules.formidable.accounts.TestSupport;
import org.jahia.modules.formidable.accounts.account.Authenticator;
import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.modules.formidable.accounts.messages.VisitorText;
import org.jahia.modules.formidable.accounts.security.AccountsGuard;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignInFormActionTest {

    private static final String PASSWORD = "Sup3r-Secret!";
    private static final Locale FRENCH = Locale.FRENCH;

    private Fakes.Directory directory;
    private Fakes.Auth auth;
    private AccountsConfigService config;
    private SignInFormAction action;
    private Map<String, String> settings;

    @BeforeEach
    void setUp() {
        directory = new Fakes.Directory();
        auth = new Fakes.Auth();
        config = TestSupport.config(true, 100, 3);
        action = wire(new SignInFormAction(new Redirects((page, req, resp) -> Optional.empty())));
        settings = new HashMap<>(Map.of("loginField", "login", "passwordField", "password"));
    }

    private SignInFormAction wire(SignInFormAction wired) {
        wired.setDirectory(directory);
        wired.setAuthenticator(auth);
        AccountsGuard guard = new AccountsGuard();
        guard.setConfig(config);
        wired.setGuard(guard);
        return wired;
    }

    private FormActionContext context(JCRNodeWrapper node, String login, String password, String referer) {
        Map<String, String> values = login == null ? Map.of() : Map.of("login", login);
        Map<String, String> secret = password == null ? Map.of() : Map.of("password", password);
        return TestSupport.context(node, TestSupport.form(true, true), TestSupport.request("203.0.113.7", referer), values, secret);
    }

    private FormActionContext context(String login, String password) {
        return context(TestSupport.actionNode(settings), login, password, null);
    }

    private FormActionException refusal(FormActionContext context) {
        return assertThrows(FormActionException.class, () -> action.execute(context));
    }

    @Test
    void aVisitorWhoseCredentialsAreRightIsSignedInThroughThePlatformAndSentWhereTheyWereGoing() throws Exception {
        // Verifies the success: the sign-in is asked of the platform with the user name and the form's site, and the
        // return parameter of the page (validated) is the redirect.
        settings.put("redirectParameter", "redirect");

        FormActionResult result = action.execute(context(TestSupport.actionNode(settings), "Ada@Example.com", PASSWORD,
                "https://www.example.com/login?redirect=%2Fmembers"));

        assertEquals(List.of("ada@example.com|mysite"), auth.calls);
        assertEquals("/members", result.redirectPath());
        assertTrue(action.consumesSensitiveValues());
        assertEquals("fmdbacc:signInAction", action.getNodeType());
    }

    @Test
    void anUnknownAccountAndAWrongPasswordAndAnInvalidNameReadTheSameSentence() {
        // Verifies the no-oracle rule: whatever made the sign-in fail, the visitor reads one sentence and one status.
        auth.outcome = Authenticator.Outcome.REJECTED;
        FormActionException wrongPassword = refusal(context("ada@example.com", "nope"));
        FormActionException invalidName = refusal(context("not valid!", "nope"));
        FormActionException noPassword = refusal(context("ada@example.com", null));

        assertEquals(VisitorText.SIGN_IN_FAILED.in(FRENCH), wrongPassword.getVisitorMessage());
        assertEquals(wrongPassword.getVisitorMessage(), invalidName.getVisitorMessage());
        assertEquals(wrongPassword.getVisitorMessage(), noPassword.getVisitorMessage());
        assertEquals(wrongPassword.getHttpStatus(), invalidName.getHttpStatus());
        assertEquals(422, wrongPassword.getHttpStatus());
    }

    @Test
    void aLockedAccountWhosePasswordWasRightIsToldItIsNotActive() {
        // Verifies the one distinct message: the platform reports a locked account only after verifying the password.
        auth.outcome = Authenticator.Outcome.NOT_ACTIVE;

        assertEquals(VisitorText.ACCOUNT_NOT_ACTIVE.in(FRENCH), refusal(context("ada@example.com", PASSWORD)).getVisitorMessage());
    }

    @Test
    void rootAndGuestAreNeverSignedInByAPublicForm() {
        // Verifies the reserved accounts reach the platform's login nowhere through this form.
        refusal(context("root", PASSWORD));
        refusal(context("guest", PASSWORD));

        assertTrue(auth.calls.isEmpty());
    }

    @Test
    void anAuthorsFailureMessageReplacesTheModulesOwn() {
        // Verifies failureMessage: one author text for every cause of failure.
        settings.put("failureMessage", "Identifiants incorrects.");
        auth.outcome = Authenticator.Outcome.REJECTED;

        assertEquals("Identifiants incorrects.", refusal(context("ada@example.com", "nope")).getVisitorMessage());
    }

    @Test
    void anAccountIsBlockedAfterItsFailuresAndTheBlockSaysTheSameForAnUnknownOne() throws Exception {
        // Verifies the account limit end to end: three failures, then a 429 with the rate-limit sentence, existing
        // account or not; and a success before the limit clears the count.
        auth.outcome = Authenticator.Outcome.REJECTED;
        for (int i = 0; i < 3; i++) {
            refusal(context("ada@example.com", "nope"));
        }
        FormActionException blocked = refusal(context("ada@example.com", PASSWORD));
        FormActionException unknownBlocked;
        for (int i = 0; i < 3; i++) {
            refusal(context("ghost@example.com", "nope"));
        }
        unknownBlocked = refusal(context("ghost@example.com", PASSWORD));

        assertEquals(429, blocked.getHttpStatus());
        assertEquals(VisitorText.TOO_MANY_ATTEMPTS.in(FRENCH), blocked.getVisitorMessage());
        assertEquals(blocked.getVisitorMessage(), unknownBlocked.getVisitorMessage());
        assertEquals(6, auth.calls.size(), "a blocked attempt never reaches the platform");
    }

    @Test
    void aSuccessClearsTheFailuresChargedToTheAccount() throws Exception {
        // Verifies two failures then a success leave the account with its full allowance.
        auth.outcome = Authenticator.Outcome.REJECTED;
        refusal(context("ada@example.com", "nope"));
        refusal(context("ada@example.com", "nope"));
        auth.outcome = Authenticator.Outcome.SIGNED_IN;
        action.execute(context("ada@example.com", PASSWORD));
        auth.outcome = Authenticator.Outcome.REJECTED;
        refusal(context("ada@example.com", "nope"));
        refusal(context("ada@example.com", "nope"));

        assertEquals(422, refusal(context("ada@example.com", "nope")).getHttpStatus());
    }

    @Test
    void aPasswordFieldThatIsNotSensitiveIsRefusedAsAConfigurationError() {
        // Verifies the action will not read a password from an ordinary field.
        FormActionContext plain = TestSupport.context(TestSupport.actionNode(settings), TestSupport.form(true, true),
                TestSupport.request("203.0.113.7", null), Map.of("login", "ada@example.com", "password", PASSWORD), Map.of());

        assertEquals(503, refusal(plain).getHttpStatus());
        assertTrue(auth.calls.isEmpty());
    }

    @Test
    void anActionWithoutItsFieldsAndAFormWithoutCaptchaAreConfigurationErrors() {
        // Verifies the two configuration refusals: both 503, nothing sent to the platform.
        settings.remove("loginField");
        assertEquals(503, refusal(context("ada@example.com", PASSWORD)).getHttpStatus());

        settings.put("loginField", "login");
        FormActionContext unprotected = TestSupport.context(TestSupport.actionNode(settings), TestSupport.form(false, true),
                TestSupport.request("203.0.113.7", null), Map.of("login", "ada@example.com"), Map.of("password", PASSWORD));
        assertEquals(503, refusal(unprotected).getHttpStatus());
        assertTrue(auth.calls.isEmpty());
    }

    @Test
    void thePasswordIsNeverInARefusal() {
        // Verifies no refusal, visitor text or exception message carries the password.
        auth.outcome = Authenticator.Outcome.REJECTED;

        FormActionException e = refusal(context("ada@example.com", PASSWORD));

        assertFalse(String.valueOf(e.getMessage()).contains(PASSWORD));
        assertFalse(String.valueOf(e.getVisitorMessage()).contains(PASSWORD));
    }
}
