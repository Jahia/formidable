package org.jahia.modules.formidable.accounts.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rule a new password must satisfy: a regular expression the contributor sets on the Create account action, with a
 * default of at least one digit, one lower-case and one upper-case letter, one symbol, 8 to 64 characters.
 *
 * <p>Stateless apart from a bounded cache of compiled patterns; thread-safe. Two guards because the expression is
 * contributor-supplied and the password visitor-supplied: the password is capped at {@link #MAX_LENGTH} characters
 * before any matching, and the match gives up after a bounded number of character reads, so a catastrophic
 * expression ends the attempt (the password is then refused) instead of the thread. The password is never logged.</p>
 */
public final class PasswordPolicy {

    /** The old registration module's rule, kept as the default. */
    public static final String DEFAULT_PATTERN = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[a-zA-Z])(?=.*\\W).{8,64}$";

    /** The longest password considered at all: it is hashed server-side, and an unbounded one is a cost for nothing. */
    public static final int MAX_LENGTH = 256;

    /** Character reads one match may cost: orders of magnitude over any sane expression on a 256-character input. */
    static final int MAX_CHAR_READS = 200_000;

    private static final int MAX_CACHED_PATTERNS = 50;
    private static final Logger log = LoggerFactory.getLogger(PasswordPolicy.class);
    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    private PasswordPolicy() {
    }

    /**
     * @param password the candidate, never logged
     * @param pattern  the regular expression the whole password must match; blank for {@link #DEFAULT_PATTERN}
     * @return {@code true} when the password is acceptable; {@code false} when it is empty, too long, does not match,
     *         or the expression is invalid or too expensive (a configuration error, logged without the password)
     */
    public static boolean accepts(String password, String pattern) {
        if (password == null || password.isEmpty() || password.length() > MAX_LENGTH) {
            return false;
        }
        String expression = pattern == null || pattern.isBlank() ? DEFAULT_PATTERN : pattern.trim();
        Pattern compiled = compile(expression);
        if (compiled == null) {
            return false;
        }
        try {
            return compiled.matcher(new BoundedCharSequence(password, MAX_CHAR_READS)).matches();
        } catch (BudgetExceededException e) {
            log.warn("[PasswordPolicy] The password pattern is too expensive to evaluate: the attempt is refused. Simplify the expression.");
            return false;
        }
    }

    private static Pattern compile(String expression) {
        Pattern cached = COMPILED.get(expression);
        if (cached != null) {
            return cached;
        }
        try {
            Pattern compiled = Pattern.compile(expression);
            if (COMPILED.size() < MAX_CACHED_PATTERNS) {
                COMPILED.put(expression, compiled);
            }
            return compiled;
        } catch (PatternSyntaxException e) {
            log.warn("[PasswordPolicy] The password pattern of an action is not a valid regular expression: every password is refused until it is fixed.");
            return null;
        }
    }

    /** Thrown when a match reads more characters than its budget. */
    private static final class BudgetExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BudgetExceededException() {
            super("regex budget exceeded", null, false, false);
        }
    }

    /** A view of the password that counts the characters the matcher reads and gives up past the budget. */
    private static final class BoundedCharSequence implements CharSequence {
        private final CharSequence delegate;
        private final int[] remaining;

        BoundedCharSequence(CharSequence delegate, int budget) {
            this(delegate, new int[]{budget});
        }

        private BoundedCharSequence(CharSequence delegate, int[] remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (--remaining[0] < 0) {
                throw new BudgetExceededException();
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new BoundedCharSequence(delegate.subSequence(start, end), remaining);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
