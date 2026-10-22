package org.jahia.modules.formidable.engine.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * The rules of where a form may send the visitor. A redirect target travels in the submission response and the
 * browser navigates to it, so a value that can be influenced by the caller (a query parameter of the form's page)
 * must not be able to name another host: that would be an open redirect, a phishing gadget hosted on a trusted
 * domain. Two rules, one per kind of {@link FormActionResult} redirect:
 * <ul>
 *   <li>{@link #sameOriginPath}: a path of this server, for any target;</li>
 *   <li>{@link #httpUrl}: an absolute {@code http} or {@code https} URL, for a target read from the configuration only.</li>
 * </ul>
 *
 * <p>Stateless and thread-safe. Every method answers "no target" ({@link Optional#empty()}) rather than throwing:
 * an unusable target means no redirect, never a failed submission.</p>
 */
public final class RedirectTargets {

    /** A target longer than this is not a page; it is also a cap on what the response carries. */
    static final int MAX_LENGTH = 2048;

    private RedirectTargets() {
    }

    /**
     * Accepts a same-origin relative path: it starts with exactly one {@code /}, has no scheme, no authority, no
     * forbidden character ({@link #hasForbiddenCharacter}), and parses as a URI reference. The check is the origin,
     * not the site: a path of another site served by this server passes.
     *
     * @param candidate the path as read from the configuration or from a request, may be {@code null}
     * @return the path, trimmed, when it is safe to send to the browser
     */
    public static Optional<String> sameOriginPath(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String path = candidate.trim();
        if (path.isEmpty() || path.length() > MAX_LENGTH || path.charAt(0) != '/') {
            return Optional.empty();
        }
        if (path.length() > 1 && (path.charAt(1) == '/' || path.charAt(1) == '\\')) {
            return Optional.empty();
        }
        if (hasForbiddenCharacter(path)) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(path);
            if (uri.getScheme() != null || uri.getRawAuthority() != null || uri.getHost() != null) {
                return Optional.empty();
            }
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    /**
     * Accepts an absolute {@code http} or {@code https} URL with a host and no user info ({@code https://user@host}
     * reads as another host to a visitor), no forbidden character ({@link #hasForbiddenCharacter}), at most
     * {@value #MAX_LENGTH} characters. It says nothing of where the value came from: a caller hands it a URL read
     * from the configuration, never one taken from the request.
     *
     * @param candidate the URL as read from the configuration, may be {@code null}
     * @return the URL, trimmed, when it is safe to send to the browser
     */
    public static Optional<String> httpUrl(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String url = candidate.trim();
        if (url.isEmpty() || url.length() > MAX_LENGTH || hasForbiddenCharacter(url)) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getHost().isEmpty() || uri.getRawUserInfo() != null) {
                return Optional.empty();
            }
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        return Optional.of(url);
    }

    /**
     * A backslash (some browsers read {@code /\evil.com} as {@code //evil.com}), a control character, a space or line
     * separator of any kind, or an invisible format character such as U+FEFF: the browser refuses the same set
     * ({@code useFormSubmission.ts}), so the two checks agree on what is sent.
     */
    private static boolean hasForbiddenCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c <= 0x20 || Character.isISOControl(c) || Character.isWhitespace(c)
                    || Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT) {
                return true;
            }
        }
        return false;
    }
}
