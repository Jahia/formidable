package org.jahia.modules.formidable.accounts.activation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The secret of an activation link: {@code <identifier>.<secret>}, where the identifier is the user node's UUID (so the
 * servlet finds the account without a query, and without the e-mail address ever appearing in a URL) and the secret is
 * 32 random bytes. Only the SHA-256 hash of the secret is stored, so a reader of the repository cannot activate an
 * account.
 *
 * <p>Stateless and thread-safe ({@link SecureRandom} is).</p>
 *
 * @param identifier the user node's identifier
 * @param secret     the random part, URL-safe base64 without padding
 */
public record ActivationToken(String identifier, String secret) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern UUID_SHAPE = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SECRET_SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

    /** @return a new token for the given user node, with a fresh secret */
    public static ActivationToken generate(String identifier) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new ActivationToken(identifier, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * @param raw the {@code t} parameter of the link, untrusted
     * @return the token, empty when the shape is not exactly an identifier, a dot and a 43-character secret
     */
    public static Optional<ActivationToken> parse(String raw) {
        if (raw == null || raw.length() != 36 + 1 + 43) {
            return Optional.empty();
        }
        String identifier = raw.substring(0, 36);
        String secret = raw.substring(37);
        if (raw.charAt(36) != '.' || !UUID_SHAPE.matcher(identifier).matches() || !SECRET_SHAPE.matcher(secret).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ActivationToken(identifier, secret));
    }

    /** @return {@code identifier.secret}, the value of the link's {@code t} parameter */
    public String asParameter() {
        return identifier + "." + secret;
    }

    /** @return the hash stored in the repository: lower-case hex SHA-256 of the secret */
    public String hash() {
        return hash(secret);
    }

    /**
     * Compares a presented secret with the stored hash in constant time.
     *
     * @param storedHash the hash read from the repository, may be {@code null}
     * @return {@code true} when this token's secret hashes to it
     */
    public boolean matches(String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash().getBytes(StandardCharsets.US_ASCII), storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }

    /** Never prints the secret. */
    @Override
    public String toString() {
        return "ActivationToken[identifier=" + identifier + "]";
    }
}
