package org.jahia.modules.formidable.accounts.account;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Signs a visitor in through the platform. A seam over Jahia's authentication service so the actions are testable and
 * so that nothing in this module sets a session attribute by hand.
 *
 * <p>Implementations are singletons; the request and response are the submission's, valid for the call only.</p>
 */
public interface Authenticator {

    /** What happened; every cause that is not a success or a locked account reads as one: the visitor learns nothing more. */
    enum Outcome {
        /** The session of the visitor now holds the user, and the platform's login event was fired. */
        SIGNED_IN,
        /** Unknown account, wrong password, limit of logged-in users: indistinguishable on purpose. */
        REJECTED,
        /** The password was right and the account is locked (awaiting its activation, or locked by an administrator). */
        NOT_ACTIVE
    }

    /**
     * @param username the user name, already resolved from what the visitor typed
     * @param password the password, never logged
     * @param siteKey  the site the form belongs to: the scope of the sign-in and of the login event jExperience reads
     * @param request  the submission request; the session is attached to it
     * @param response the submission response; the session cookie is set on it
     * @return the outcome, never {@code null}
     */
    Outcome signIn(String username, String password, String siteKey, HttpServletRequest request, HttpServletResponse response);
}
