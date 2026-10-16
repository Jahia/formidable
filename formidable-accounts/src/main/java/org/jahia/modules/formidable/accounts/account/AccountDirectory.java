package org.jahia.modules.formidable.accounts.account;

import org.jahia.modules.formidable.accounts.activation.ActivationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the actions need from the platform's user directory, as a seam: the production implementation sits on Jahia's
 * user and group services, the tests on a fake.
 *
 * <p>Implementations are singletons shared by every request, keep no per-request state, and open and close their own
 * JCR sessions.</p>
 */
public interface AccountDirectory {

    /** Why an account was not created. Never shown to the visitor: every one of them reads the same sentence. */
    enum Refusal {
        /** The user name exists (or is held by an account awaiting its activation e-mail). */
        TAKEN,
        /** The group of the action is not in the allowed groups, does not exist, or gives back-office access. */
        GROUP,
        /** Anything else: the platform refused, the repository failed. */
        FAILED
    }

    /** Raised by {@link #create}; carries the reason for the log only. */
    final class AccountException extends Exception {
        private static final long serialVersionUID = 1L;
        private final Refusal refusal;

        public AccountException(Refusal refusal, String message, Throwable cause) {
            super(message, cause);
            this.refusal = refusal;
        }

        public AccountException(Refusal refusal, String message) {
            this(refusal, message, null);
        }

        /** @return why the account was not created */
        public Refusal refusal() {
            return refusal;
        }
    }

    /** What the visitor typed, once validated: the account to create. */
    record NewAccount(String username, String password, String email, String firstName, String lastName, String groupReference, String siteKey) {
        /** Never prints the password. */
        @Override
        public String toString() {
            return "NewAccount[username=" + username + "]";
        }
    }

    /** The activation of an account: valid until {@code expires}, a hash to match, where to send the visitor after. */
    record Pending(ActivationToken token, Instant expires, String redirectPath) {
    }

    /** What {@link #create} made: the user node's identifier (what the activation link carries) and path. */
    record Created(String identifier, String path) {
    }

    /** @return whether the platform accepts this user name ({@code JahiaUserManagerService#isUsernameSyntaxCorrect}) */
    boolean isValidUsername(String username);

    /**
     * Whether an account has access to the back office: root, a member (directly or through other groups) of
     * {@code privileged}, {@code administrators}, a site's {@code site-privileged} or {@code site-administrators}, holding
     * a server role, or holding anywhere a role that gives privileged access ({@code j:privilegedAccess}: editor,
     * translator, site administrator...), itself or through a group. The form sign-in is for site visitors: such an account signs in on the platform's own
     * login, where its second factor and SSO apply.
     *
     * @param username the user name to check
     * @return {@code true} for a back-office account; {@code false} for any other, and for a user name that does not exist
     */
    boolean isBackOfficeUser(String username);

    /**
     * Whether a user holds a permission on a node, read in a session of that user.
     *
     * @param username   the user, typically the last author of an action node; root and the system account hold every
     *                   permission
     * @param path       the node's path
     * @param workspace  the workspace to read it in
     * @param permission the permission's name
     * @return {@code false} as well when the user or the node does not exist or cannot be read
     */
    boolean holdsPermission(String username, String path, String workspace, String permission);

    /**
     * Creates the account in one system session: refuses an existing user name (an account whose activation expired is
     * replaced), creates the user so the platform hashes the password, adds the visitor to the group, and, when
     * {@code pending} is given, creates it locked with its activation record. A failure after the user was created
     * removes the user again.
     *
     * @param account the account to create
     * @param pending given the identifier of the new user, answers its activation record; {@code null} for an account
     *                that is active at once
     * @return the new user
     * @throws AccountException with the reason, for the log
     */
    Created create(NewAccount account, Function<String, Pending> pending) throws AccountException;

    /**
     * Removes an account created by {@link #create}, for the case where what came after it failed (the activation
     * e-mail could not be sent).
     *
     * @param identifier the identifier {@link #create} answered
     */
    void delete(String identifier);

    /**
     * Activates an account whose activation link was followed.
     *
     * @param token the token of the link
     * @param now   the time to compare the expiry with
     * @return the path to send the visitor to, empty-string when none was chosen, and empty when the token is unknown,
     *         expired, already used or does not match
     */
    Optional<String> activate(ActivationToken token, Instant now);
}
