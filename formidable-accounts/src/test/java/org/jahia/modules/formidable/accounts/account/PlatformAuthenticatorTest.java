package org.jahia.modules.formidable.accounts.account;

import org.jahia.services.security.AuthenticationOptions;
import org.jahia.services.security.AuthenticationRequest;
import org.jahia.services.security.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.security.auth.login.AccountLockedException;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformAuthenticatorTest {

    private final AuthenticationService service = mock(AuthenticationService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final PlatformAuthenticator authenticator = new PlatformAuthenticator();

    PlatformAuthenticatorTest() {
        authenticator.setAuthentication(service);
    }

    @Test
    void aSignInGoesThroughThePlatformsServiceWithTheLoginEventOnAndTheSiteScope() throws Exception {
        // Verifies the Jahia login: the request names the site (the scope jExperience's listener reads), the options
        // fire the login event, and the credentials are the ones given, the global search included.
        Authenticator.Outcome outcome = authenticator.signIn("ada@example.com", "S3cret!pw", "mysite", request, response);

        assertEquals(Authenticator.Outcome.SIGNED_IN, outcome);
        verify(request).setAttribute("site", "mysite");
        ArgumentCaptor<AuthenticationRequest> credentials = ArgumentCaptor.forClass(AuthenticationRequest.class);
        ArgumentCaptor<AuthenticationOptions> options = ArgumentCaptor.forClass(AuthenticationOptions.class);
        verify(service).authenticate(credentials.capture(), options.capture(), org.mockito.ArgumentMatchers.same(request), org.mockito.ArgumentMatchers.same(response));
        assertEquals("ada@example.com", credentials.getValue().getUsername());
        assertEquals("S3cret!pw", credentials.getValue().getPassword());
        assertEquals("mysite", credentials.getValue().getSite());
        assertTrue(credentials.getValue().isGlobalSearchIncluded());
        assertTrue(options.getValue().isTriggerLoginEventEnabled());
        assertTrue(options.getValue().isStateful());
        assertTrue(!options.getValue().shouldRememberMe());
    }

    @Test
    void anUnknownAccountAndAWrongPasswordAreOneOutcome() throws Exception {
        // Verifies the no-oracle rule at its source: the two causes cannot be told apart from what is returned.
        doThrow(new AccountNotFoundException("no")).when(service).authenticate(any(AuthenticationRequest.class), any(), any(), any());
        assertEquals(Authenticator.Outcome.REJECTED, authenticator.signIn("ghost", "x", "mysite", request, response));

        AuthenticationService wrong = mock(AuthenticationService.class);
        doThrow(new FailedLoginException("bad")).when(wrong).authenticate(any(AuthenticationRequest.class), any(), any(), any());
        PlatformAuthenticator other = new PlatformAuthenticator();
        other.setAuthentication(wrong);
        assertEquals(Authenticator.Outcome.REJECTED, other.signIn("ada", "x", "mysite", request, response));
    }

    @Test
    void aLockedAccountIsReportedBecauseThePasswordWasVerifiedFirst() throws Exception {
        // Verifies the one distinct outcome: the platform throws it only after the password matched.
        doThrow(new AccountLockedException("locked")).when(service).authenticate(any(AuthenticationRequest.class), any(), any(), any());

        assertEquals(Authenticator.Outcome.NOT_ACTIVE, authenticator.signIn("ada", "x", "mysite", request, response));
    }

    @Test
    void anUnexpectedFailureIsARejectionNotAnError() throws Exception {
        // Verifies the sign-in never fails the submission with something the visitor could use: it is a rejection.
        doThrow(new IllegalStateException("boom")).when(service).authenticate(any(AuthenticationRequest.class), any(), any(), any());

        assertEquals(Authenticator.Outcome.REJECTED, authenticator.signIn("ada", "x", "mysite", request, response));
    }
}
