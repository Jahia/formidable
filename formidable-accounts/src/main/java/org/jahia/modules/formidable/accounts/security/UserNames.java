package org.jahia.modules.formidable.accounts.security;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Turns what a visitor typed into a Jahia user name, with the platform's own syntax check as the last word.
 *
 * <p>A registration uses the e-mail address, lower-cased (an address is case-insensitive in practice, and two
 * sign-ups differing by case must be one account), characters the platform does not accept replaced by {@code _}. A
 * sign-in applies the same rule to a typed address and leaves any other user name as typed. Stateless, thread-safe;
 * the platform check is a parameter so the rule is testable without a platform.</p>
 */
public final class UserNames {

    /** Accounts a public form never signs in: they are not visitors. */
    private static final Set<String> RESERVED = Set.of("root", "guest");

    private static final int MAX_LENGTH = 255;

    private UserNames() {
    }

    /**
     * @param email          the address as typed
     * @param platformSyntax the platform's user-name check ({@code JahiaUserManagerService#isUsernameSyntaxCorrect})
     * @return the user name for a new account, empty when no valid one can be made of the input
     */
    public static Optional<String> forRegistration(String email, Predicate<String> platformSyntax) {
        if (email == null) {
            return Optional.empty();
        }
        String candidate = email.trim().toLowerCase(Locale.ROOT);
        if (candidate.isEmpty() || candidate.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (!platformSyntax.test(candidate)) {
            candidate = sanitise(candidate);
        }
        return candidate.isEmpty() || RESERVED.contains(candidate) || !platformSyntax.test(candidate)
                ? Optional.empty() : Optional.of(candidate);
    }

    /**
     * @param typed          the e-mail address or user name as typed
     * @param platformSyntax the platform's user-name check
     * @return the user name to authenticate, empty when the input cannot be one or names a reserved account
     */
    public static Optional<String> forSignIn(String typed, Predicate<String> platformSyntax) {
        if (typed == null) {
            return Optional.empty();
        }
        String trimmed = typed.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (trimmed.indexOf('@') >= 0) {
            return forRegistration(trimmed, platformSyntax);
        }
        return RESERVED.contains(trimmed.toLowerCase(Locale.ROOT)) || !platformSyntax.test(trimmed)
                ? Optional.empty() : Optional.of(trimmed);
    }

    private static String sanitise(String lowerCased) {
        StringBuilder out = new StringBuilder(lowerCased.length());
        for (int i = 0; i < lowerCased.length(); i++) {
            char c = lowerCased.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-' || c == '@';
            out.append(allowed ? c : '_');
        }
        return out.toString();
    }
}
