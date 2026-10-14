package org.jahia.modules.formidable.accounts.account;

import org.jahia.services.security.AuthenticationOptions;
import org.jahia.services.security.AuthenticationRequest;
import org.jahia.services.security.AuthenticationService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.AccountLockedException;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link Authenticator} on {@code org.jahia.services.security.AuthenticationService} (an OSGi service since Jahia
 * 8.2.3.0), the service the platform's own login uses. It verifies the password, refuses a locked account, replaces the
 * HTTP session, attaches the user to it, and publishes the platform's login event: jExperience's listener then sends a
 * {@code login} event to jCustomer, scoped by the request attribute {@code site}, which merges the visitor's anonymous
 * profile into the user's.
 *
 * <p>Threading: a singleton with no state. The password is passed through to the service and never logged or kept.</p>
 */
@Component(service = Authenticator.class, immediate = true)
public class PlatformAuthenticator implements Authenticator {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthenticator.class);

    private AuthenticationService authentication;

    @Reference
    public void setAuthentication(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @Override
    public Outcome signIn(String username, String password, String siteKey, HttpServletRequest request, HttpServletResponse response) {
        // The scope jExperience's login listener reads: the request parameter, else this attribute, else "systemsite"
        request.setAttribute("site", siteKey);
        // The same options as the platform's login form, the login event on: it is the point of signing in this way.
        // The builder's default is remember-me ON (a persistent login cookie): a visitor who did not ask for it gets none.
        AuthenticationOptions options = AuthenticationOptions.Builder.withDefaults()
                .triggerLoginEventEnabled(true)
                .sessionValidityCheckEnabled(false)
                .shouldRememberMe(false)
                .build();
        try {
            authentication.authenticate(new AuthenticationRequest(username, password, siteKey, true), options, request, response);
            return Outcome.SIGNED_IN;
        } catch (AccountLockedException e) {
            // thrown only after the password was verified: the visitor proved who they are
            return Outcome.NOT_ACTIVE;
        } catch (LoginException | IllegalArgumentException e) {
            // account not found, wrong password, limit of logged-in users, invalid session: the same answer
            log.debug("[PlatformAuthenticator] A sign-in was refused ({}).", e.getClass().getSimpleName());
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            log.warn("[PlatformAuthenticator] A sign-in failed unexpectedly.", e);
            return Outcome.REJECTED;
        }
    }
}
