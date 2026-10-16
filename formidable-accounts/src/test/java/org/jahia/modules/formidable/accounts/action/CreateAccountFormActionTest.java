package org.jahia.modules.formidable.accounts.action;

import org.jahia.modules.formidable.accounts.TestSupport;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.AccountException;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Refusal;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateAccountFormActionTest {

    private static final String PASSWORD = "Sup3r-Secret!";
    private static final Locale FRENCH = Locale.FRENCH;

    private Fakes.Directory directory;
    private Fakes.Auth auth;
    private Fakes.Mailer mailer;
    private AccountsConfigService config;
    private CreateAccountFormAction action;
    private Map<String, String> settings;

    @BeforeEach
    void setUp() {
        directory = new Fakes.Directory();
        auth = new Fakes.Auth();
        mailer = new Fakes.Mailer();
        config = TestSupport.config(true, 100, 100);
        action = wire(new CreateAccountFormAction(new Redirects((page, req, resp) -> Optional.of("/sites/mysite/welcome.html")), () -> ""));
        settings = new HashMap<>(Map.of("emailField", "email", "passwordField", "password", "firstNameField", "first", "lastNameField", "last",
                "group", "customers"));
    }

    private CreateAccountFormAction wire(CreateAccountFormAction wired) {
        wired.setDirectory(directory);
        wired.setAuthenticator(auth);
        wired.setMailer(mailer);
        wired.setConfig(config);
        AccountsGuard guard = new AccountsGuard();
        guard.setConfig(config);
        guard.setDirectory(directory);
        wired.setGuard(guard);
        return wired;
    }

    private FormActionContext context(JCRNodeWrapper node, boolean captcha, String email, String password, String referer) {
        Map<String, String> values = new HashMap<>(Map.of("first", "Ada", "last", "Lovelace"));
        if (email != null) {
            values.put("email", email);
        }
        Map<String, String> secret = password == null ? Map.of() : Map.of("password", password);
        return TestSupport.context(node, TestSupport.form(captcha, true), TestSupport.request("203.0.113.7", referer), values, secret);
    }

    private FormActionContext context(String email, String password) {
        return context(TestSupport.actionNode(settings), true, email, password, null);
    }

    private FormActionException refusal(FormActionContext context) {
        return assertThrows(FormActionException.class, () -> action.execute(context));
    }

    @Test
    void anAccountIsCreatedFromTheEmailAndSignedInThenTheVisitorIsSentToTheChosenPage() throws Exception {
        // Verifies the happy path: lower-cased e-mail as user name, names and group passed on, password handed to the
        // directory once, the platform sign-in used, the redirect is the page's path.
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withPage(node, "redirectPage");

        FormActionResult result = action.execute(context(node, true, "Ada@Example.COM", PASSWORD, null));

        assertEquals("ada@example.com", directory.created.get(0).username());
        assertEquals("Ada", directory.created.get(0).firstName());
        assertEquals("Lovelace", directory.created.get(0).lastName());
        assertEquals("customers", directory.created.get(0).groupReference());
        assertEquals("mysite", directory.created.get(0).siteKey());
        assertEquals(PASSWORD, directory.created.get(0).password());
        assertEquals(java.util.List.of("ada@example.com|mysite"), auth.calls);
        assertEquals("/sites/mysite/welcome.html", result.redirectPath());
        assertNull(result.message());
    }

    @Test
    void theActionDeclaresItConsumesSensitiveValuesAndHandlesItsNodeType() {
        // Verifies the SPI declarations the engine relies on.
        assertTrue(action.consumesSensitiveValues());
        assertEquals("fmdbacc:createAccountAction", action.getNodeType());
    }

    @Test
    void signingInCanBeTurnedOffAndTheAccountIsStillCreated() throws Exception {
        // Verifies signIn=false: no platform sign-in, and the return parameter is not used for a visitor not signed in.
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withBool(node, "signIn", false);

        action.execute(context(node, true, "ada@example.com", PASSWORD, null));

        assertEquals(1, directory.created.size());
        assertTrue(auth.calls.isEmpty());
    }

    @Test
    void aSignInThatTheAutomaticLoginRefusesDoesNotFailTheSignUp() throws Exception {
        // Verifies the account exists: the visitor can sign in by hand, the submission is accepted without a redirect.
        auth.outcome = Authenticator.Outcome.REJECTED;

        FormActionResult result = action.execute(context("ada@example.com", PASSWORD));

        assertTrue(result.isEmpty());
        assertEquals(1, directory.created.size());
    }

    @Test
    void theReturnParameterOfThePageIsUsedForAVisitorWhoIsSignedIn() throws Exception {
        // Verifies "back to where the visitor was going", validated as a same-origin path.
        settings.put("redirectParameter", "redirect");

        FormActionResult result = action.execute(context(TestSupport.actionNode(settings), true, "ada@example.com", PASSWORD,
                "https://www.example.com/sign-up?redirect=%2Fmembers%2Fprofile"));

        assertEquals("/members/profile", result.redirectPath());
    }

    @Test
    void anAddressAlreadyRegisteredAndAnyOtherFailureReadTheSameSentence() throws Exception {
        // Verifies the no-enumeration rule: a taken address, a refused group and a platform failure are one sentence,
        // one status, and no sign-in happens.
        String sentence = null;
        for (Refusal reason : Refusal.values()) {
            directory.failure = new AccountException(reason, "reason");
            FormActionException e = refusal(context("ada@example.com", PASSWORD));
            assertEquals(422, e.getHttpStatus());
            assertEquals(VisitorText.GENERIC_FAILURE.in(FRENCH), e.getVisitorMessage());
            sentence = sentence == null ? e.getVisitorMessage() : sentence;
            assertEquals(sentence, e.getVisitorMessage());
        }
        assertTrue(auth.calls.isEmpty());
    }

    @Test
    void aPasswordThatBreaksTheRuleIsRefusedWithoutTouchingThePlatform() {
        // Verifies the policy sentence, and that it does not depend on whether the address exists (nothing was looked up).
        FormActionException e = refusal(context("ada@example.com", "weak"));

        assertEquals(VisitorText.PASSWORD_POLICY.in(FRENCH), e.getVisitorMessage());
        assertTrue(directory.created.isEmpty());
    }

    @Test
    void aContributorsRuleReplacesTheDefault() throws Exception {
        // Verifies passwordPattern is honoured.
        settings.put("passwordPattern", "[a-z]{12}");

        action.execute(context("ada@example.com", "abcdefghijkl"));
        assertEquals(1, directory.created.size());
        assertEquals(VisitorText.PASSWORD_POLICY.in(FRENCH), refusal(context("other@example.com", PASSWORD)).getVisitorMessage());
    }

    @Test
    void anEmailThatIsNotAnAddressIsRefused() {
        // Verifies the e-mail check: missing, malformed, and one the platform could not turn into a user name.
        for (String bad : new String[]{null, "", "not-an-address", "a@b", "@example.com"}) {
            assertEquals(VisitorText.INVALID_EMAIL.in(FRENCH), refusal(context(bad, PASSWORD)).getVisitorMessage(), String.valueOf(bad));
        }
        assertTrue(directory.created.isEmpty());
    }

    @Test
    void aPasswordFieldThatIsNotSensitiveIsRefusedAsAConfigurationError() {
        // Verifies the action will not take a password from an ordinary field: the engine kept nothing secret there.
        Map<String, String> values = new HashMap<>(Map.of("email", "ada@example.com", "password", PASSWORD));
        FormActionContext plain = TestSupport.context(TestSupport.actionNode(settings), TestSupport.form(true, true),
                TestSupport.request("203.0.113.7", null), values, Map.of());

        FormActionException e = refusal(plain);

        assertEquals(503, e.getHttpStatus());
        assertEquals(VisitorText.UNAVAILABLE.in(FRENCH), e.getVisitorMessage());
        assertTrue(directory.created.isEmpty());
    }

    @Test
    void anActionWithoutItsFieldsChosenIsRefusedAsAConfigurationError() {
        // Verifies the mandatory settings are checked at run time too (every property is optional there).
        settings.remove("passwordField");

        assertEquals(503, refusal(context("ada@example.com", PASSWORD)).getHttpStatus());
    }

    @Test
    void aFormWithoutCaptchaIsRefusedBeforeAnythingIsRead() {
        // Verifies the required CAPTCHA: nothing is created on an unprotected form.
        FormActionContext unprotected = context(TestSupport.actionNode(settings), false, "ada@example.com", PASSWORD, null);

        assertEquals(503, refusal(unprotected).getHttpStatus());
        assertTrue(directory.created.isEmpty());
    }

    @Test
    void theAddressIsLimited() throws Exception {
        // Verifies the rate limit answers the visitor with a 429 once the address used its attempts.
        config = TestSupport.config(true, 2, 100);
        action = wire(new CreateAccountFormAction(new Redirects((page, req, resp) -> Optional.empty()), () -> ""));
        action.execute(context("a1@example.com", PASSWORD));
        action.execute(context("a2@example.com", PASSWORD));

        FormActionException e = refusal(context("a3@example.com", PASSWORD));

        assertEquals(429, e.getHttpStatus());
        assertEquals(VisitorText.TOO_MANY_ATTEMPTS.in(FRENCH), e.getVisitorMessage());
        assertEquals(2, directory.created.size());
    }

    @Test
    void withActivationTheAccountIsCreatedLockedTheLinkIsMailedAndNobodyIsSignedIn() throws Exception {
        // Verifies the activation flow: pending record with expiry in 48 hours, link built on the verification servlet,
        // mailed to the address typed, the visitor told to check the mailbox, no sign-in.
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withBool(node, "activationRequired", true);

        FormActionResult result = action.execute(context(node, true, "ada@example.com", PASSWORD, null));

        assertNotNull(directory.lastPending);
        long hours = java.time.Duration.between(java.time.Instant.now(), directory.lastPending.expires()).toHours();
        assertTrue(hours == 47 || hours == 48, "expires in " + hours + " hours");
        assertEquals(1, mailer.sent.size());
        String[] mail = mailer.sent.get(0);
        assertEquals("ada@example.com", mail[1]);
        String link = "https://www.example.com/modules/formidable-accounts/verify?t=" + directory.lastPending.token().asParameter();
        assertTrue(mail[3].contains(link), "the plain-text body carries the link");
        assertFalse(mail[3].contains(PASSWORD) || mail[4].contains(PASSWORD));
        assertEquals(VisitorText.CHECK_MAILBOX.in(FRENCH), result.message());
        assertTrue(auth.calls.isEmpty());
    }

    @Test
    void theActivationLinkPointsAtTheChosenPageAndTheHoursAreConfigurable() throws Exception {
        // Verifies the chosen page is stored with the pending record (the servlet redirects there) and the validity applies.
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withBool(node, "activationRequired", true);
        TestSupport.withNumber(node, "activationHours", 2);
        TestSupport.withPage(node, "activationPage");

        action.execute(context(node, true, "ada@example.com", PASSWORD, null));

        assertEquals("/sites/mysite/welcome.html", directory.lastPending.redirectPath());
        assertTrue(java.time.Duration.between(java.time.Instant.now(), directory.lastPending.expires()).toMinutes() >= 118);
    }

    @Test
    void anActivationEmailThatCannotBeSentRemovesTheAccountAndTellsTheVisitorItIsUnavailable() throws Exception {
        // Verifies the compensation: an account nobody can activate is removed so the visitor can try again.
        mailer.failSend = true;
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withBool(node, "activationRequired", true);

        FormActionException e = assertThrows(FormActionException.class, () -> action.execute(context(node, true, "ada@example.com", PASSWORD, null)));

        assertEquals(503, e.getHttpStatus());
        assertEquals(1, directory.deleted.size());
    }

    @Test
    void withActivationATakenAddressReadsTheSameAnswerAsANewOneAndNothingIsSent() throws Exception {
        // Verifies finding 3 of jahia-private#5909: the form does not tell who is registered. A taken address answers
        // "check your mailbox" like a new one, with the same redirect, and no mail leaves.
        directory.failure = new AccountException(Refusal.TAKEN, "the user name is taken");
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withBool(node, "activationRequired", true);
        TestSupport.withPage(node, "redirectPage");

        FormActionResult result = action.execute(context(node, true, "ada@example.com", PASSWORD, null));

        assertEquals(VisitorText.CHECK_MAILBOX.in(FRENCH), result.message());
        assertEquals("/sites/mysite/welcome.html", result.redirectPath());
        assertTrue(mailer.sent.isEmpty());
        assertTrue(auth.calls.isEmpty());
    }

    @Test
    void activationWithoutAConfiguredPublicOriginIsRefusedBeforeCreatingAnything() {
        // Verifies finding 2 of jahia-private#5909: the link is never built from the request's Host header; without
        // publicBaseUrl the action refuses as a configuration error, before any account exists.
        config = TestSupport.config(true, 100, 100, "");
        action = wire(new CreateAccountFormAction(new Redirects((page, req, resp) -> Optional.of("/sites/mysite/welcome.html")), () -> ""));
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withBool(node, "activationRequired", true);

        FormActionException e = assertThrows(FormActionException.class, () -> action.execute(context(node, true, "ada@example.com", PASSWORD, null)));

        assertEquals(503, e.getHttpStatus());
        assertTrue(directory.created.isEmpty());
        assertTrue(mailer.sent.isEmpty());
    }

    @Test
    void anActionLastSavedByAContributorWithoutThePermissionCreatesNothing() {
        // Verifies finding 6 of jahia-private#5909: the action runs only when its last author holds
        // formidableAccountsActions; otherwise the visitor reads "unavailable" and no account is made.
        directory.withoutPermission.add("contributor");
        settings.put("jcr:lastModifiedBy", "contributor");

        FormActionException e = refusal(context("ada@example.com", PASSWORD));

        assertEquals(503, e.getHttpStatus());
        assertTrue(directory.created.isEmpty());
    }

    @Test
    void activationWithoutAWorkingMailServiceIsRefusedBeforeCreatingAnything() {
        // Verifies the check is made ahead of the creation, so nothing has to be rolled back.
        mailer.available = false;
        JCRNodeWrapper node = TestSupport.actionNode(settings);
        TestSupport.withBool(node, "activationRequired", true);

        FormActionException e = assertThrows(FormActionException.class, () -> action.execute(context(node, true, "ada@example.com", PASSWORD, null)));

        assertEquals(503, e.getHttpStatus());
        assertTrue(directory.created.isEmpty());
    }

    @Test
    void thePasswordIsNeverInARefusalNorInTheContextPrint() {
        // Verifies the password reaches neither a visitor message, nor an exception message, nor what a log line prints.
        directory.failure = new AccountException(Refusal.FAILED, "boom");
        FormActionContext context = context("ada@example.com", PASSWORD);

        FormActionException e = refusal(context);

        assertFalse(String.valueOf(e.getMessage()).contains(PASSWORD));
        assertFalse(String.valueOf(e.getVisitorMessage()).contains(PASSWORD));
        assertFalse(context.toString().contains(PASSWORD));
    }
}
