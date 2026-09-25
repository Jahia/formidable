package org.jahia.modules.formidable.engine.api;

import java.util.Locale;

/**
 * What a field action makes of a value meant to be an email address: its domain, or nothing when the value is not
 * one address. Shared by every check of an address — the domain lookup and the provider-backed verifications of the
 * samples — so that "not an address" means the same thing everywhere: such a value is the field's own validation's
 * business, at step 9, and an action spends no call on it.
 */
public final class EmailAddress {

    /** What the longest legal domain name measures. */
    public static final int MAX_DOMAIN_LENGTH = 253;
    /** What one label of a domain name may measure. */
    private static final int MAX_LABEL_LENGTH = 63;

    private EmailAddress() {
    }

    /** The domain of an address, lower-cased and without its trailing dot; null when the value is not one address. */
    public static String domainOf(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int at = trimmed.lastIndexOf('@');
        if (at < 1 || at == trimmed.length() - 1 || trimmed.indexOf('@') != at) {
            return null;
        }
        String domain = trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain.length() <= MAX_DOMAIN_LENGTH && isDomainName(domain) ? domain : null;
    }

    /**
     * Whether this reads as a domain name: at least two labels, each one to sixty-three characters of letters,
     * digits and hyphens, neither starting nor ending with a hyphen.
     */
    static boolean isDomainName(String domain) {
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH || label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
                if (!allowed) {
                    return false;
                }
            }
        }
        return true;
    }
}
