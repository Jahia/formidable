package org.jahia.modules.formidable.engine.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * The one rule of where a form may send the visitor: <strong>a path of the same site, never a host</strong>.
 * A redirect target travels in the submission response and the browser navigates to it, so a value that can be
 * influenced by the caller (a query parameter of the form's page) must not be able to name another site: that
 * would be an open redirect, a phishing gadget hosted on a trusted domain.
 *
 * <p>Stateless and thread-safe. Every method answers "no target" ({@link Optional#empty()}) rather than throwing:
 * an unusable target means no redirect, never a failed submission.</p>
 */
public final class RedirectTargets {

    /** A path longer than this is not a page of the site; it is also a cap on what the response carries. */
    static final int MAX_LENGTH = 2048;

    private RedirectTargets() {
    }

    /**
     * Accepts a same-origin relative path: it starts with exactly one {@code /}, has no scheme, no authority,
     * no backslash (some browsers read {@code /\evil.com} as {@code //evil.com}), no control character and no
     * whitespace, and parses as a URI reference.
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
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || c <= 0x20 || c == 0x7f || Character.isWhitespace(c)) {
                return Optional.empty();
            }
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
}
