package org.jahia.modules.formidable.accounts.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The settings of {@code org.jahia.modules.formidable.accounts.cfg} read once into one immutable object: a component
 * replaces the whole object on a configuration change, so a reader sees the old settings or the new ones, never a mix.
 * Out-of-range values are brought back to a safe bound rather than failing the component.
 *
 * @param requireCaptcha         whether the actions refuse a form without the CAPTCHA option
 * @param addressLimit           attempts per remote address in {@code addressWindow}
 * @param addressWindow          window of the per-address limit
 * @param accountLimit           attempts per account in {@code accountWindow}
 * @param accountWindow          window of the per-account limit
 * @param maxTrackedKeys         bound on the keys the limiter remembers
 * @param deniedGroups           lower-case names of the groups a form can never add a visitor to
 * @param publicBaseUrl          scheme and host of the activation link, empty for the request's own
 * @param mailFrom               sender of the activation e-mail, empty for Jahia's default
 */
public record AccountsSettings(
        boolean requireCaptcha,
        int addressLimit,
        Duration addressWindow,
        int accountLimit,
        Duration accountWindow,
        int maxTrackedKeys,
        Set<String> deniedGroups,
        String publicBaseUrl,
        String mailFrom
) {
    public AccountsSettings {
        deniedGroups = deniedGroups == null ? Set.of() : Set.copyOf(deniedGroups);
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
        mailFrom = mailFrom == null ? "" : mailFrom;
    }

    /** What the component runs with before any configuration: every value the configuration file ships. */
    public static AccountsSettings defaults() {
        return new AccountsSettings(true, 20, Duration.ofSeconds(600), 5, Duration.ofSeconds(900), 10_000,
                parseGroups("administrators;privileged;site-administrators;site-privileged;guest;users"), "", "");
    }

    /**
     * @param config the typed configuration the framework delivers
     * @return the settings, each number held to at least 1 and the limiter's key bound to at least 100
     */
    public static AccountsSettings from(AccountsConfig config) {
        return new AccountsSettings(
                config.requireCaptcha(),
                Math.max(1, config.maxAttemptsPerAddress()),
                Duration.ofSeconds(Math.max(1L, config.addressWindowSeconds())),
                Math.max(1, config.maxAttemptsPerAccount()),
                Duration.ofSeconds(Math.max(1L, config.accountWindowSeconds())),
                Math.max(100, config.maxTrackedKeys()),
                parseGroups(config.deniedGroups()),
                trimTrailingSlash(config.publicBaseUrl()),
                config.mailFrom() == null ? "" : config.mailFrom().trim());
    }

    /** Whether a group is one a form can never add a visitor to: by name or by key, any case. */
    public boolean isDeniedGroup(String nameOrKey) {
        return nameOrKey != null && deniedGroups.contains(nameOrKey.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> parseGroups(String list) {
        if (list == null) {
            return Set.of();
        }
        return Arrays.stream(list.split("[;,]"))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
