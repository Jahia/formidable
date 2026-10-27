package org.jahia.modules.formidable.engine.api;

import java.net.IDN;
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

    /** What {@link URI} refuses after the authority and a browser accepts, there percent-encoded (a {@code %} aside). */
    private static final String ENCODED_AFTER_AUTHORITY = "\"<>[]^`{|}";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private static final int MAX_PORT = 65535;

    private RedirectTargets() {
    }

    /**
     * Accepts a same-origin relative path: it starts with exactly one {@code /}, has no scheme, no authority, no
     * forbidden character ({@link #hasForbiddenCharacter}), and parses as a URI reference once encoded as a browser
     * encodes it ({@link #encodeAfterAuthority}). The check is the origin, not the site: a path of another site served
     * by this server passes.
     *
     * @param candidate the path as read from the configuration or from a request, may be {@code null}
     * @return the path, trimmed and encoded, when it is safe to send to the browser
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
        path = encodeAfterAuthority(path, 0);
        if (path.length() > MAX_LENGTH) {
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
     * Accepts an absolute {@code http} or {@code https} URL with a host, a port of at most {@value #MAX_PORT} when it
     * has one, and no user info ({@code https://user@host} reads as another host to a visitor), no forbidden character
     * ({@link #hasForbiddenCharacter}), at most {@value #MAX_LENGTH} characters once encoded as a browser encodes it
     * ({@link #encodeAfterAuthority}). It says nothing of where the value came from: a caller hands it a URL read from
     * the configuration, never one taken from the request.
     *
     * @param candidate the URL as read from the configuration, may be {@code null}
     * @return the URL, trimmed and encoded, when it is safe to send to the browser
     */
    public static Optional<String> httpUrl(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String url = candidate.trim();
        if (url.isEmpty() || url.length() > MAX_LENGTH || hasForbiddenCharacter(url)) {
            return Optional.empty();
        }
        int authority = url.indexOf("://");
        if (authority >= 0) {
            url = encodeAfterAuthority(url, endOfAuthority(url, authority + 3));
        }
        if (url.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getRawUserInfo() != null || !hasHost(uri)) {
                return Optional.empty();
            }
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        return Optional.of(url);
    }

    /**
     * {@link URI} answers no host for a name it does not read as a server name, such as a non-ASCII name
     * ({@code bücher.example}) or one with an underscore, which the browser accepts; its authority is then an opaque
     * registry name, where user info is not split out either. Such a name is read from the authority: no {@code @},
     * no {@code %} (the browser decodes an escape in a host and refuses what it decodes to), a port of digits, and a
     * non-empty result once the port is stripped and the name converted with {@link IDN#toASCII}. {@link URI} does not range-check a port, and the browser refuses one above
     * {@value #MAX_PORT}.
     */
    private static boolean hasHost(URI uri) {
        if (uri.getHost() != null) {
            return !uri.getHost().isEmpty() && uri.getPort() <= MAX_PORT;
        }
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty() || authority.indexOf('@') >= 0 || authority.indexOf('%') >= 0) {
            return false;
        }
        String host = authority;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            if (!isPort(authority.substring(colon + 1))) {
                return false;
            }
            host = authority.substring(0, colon);
        }
        try {
            return !IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).isEmpty();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Empty, as the browser reads {@code https://host:/}, or digits worth at most {@value #MAX_PORT}. */
    private static boolean isPort(String digits) {
        if (!digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        String value = digits.replaceFirst("^0+(?=\\d)", "");
        return value.length() <= 5 && (value.isEmpty() || Integer.parseInt(value) <= MAX_PORT);
    }

    /** Where the authority that starts at {@code from} ends: at the first {@code /}, {@code ?} or {@code #}. */
    private static int endOfAuthority(String url, int from) {
        for (int i = from; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                return i;
            }
        }
        return url.length();
    }

    /**
     * Percent-encodes from {@code from} on what {@link URI} refuses and a browser accepts and encodes itself:
     * {@code " < > [ ] ^ ` { | }}, a {@code %} not followed by two hexadecimal digits, and a {@code #} inside the
     * fragment. The authority, before {@code from}, is left as it is: brackets are legal there around an IPv6 address.
     */
    private static String encodeAfterAuthority(String target, int from) {
        StringBuilder encoded = new StringBuilder(target.length() + 16).append(target, 0, from);
        boolean inFragment = false;
        for (int i = from; i < target.length(); i++) {
            char c = target.charAt(i);
            boolean lonePercentSign = c == '%' && !(i + 2 < target.length() && isHex(target.charAt(i + 1)) && isHex(target.charAt(i + 2)));
            if (lonePercentSign || ENCODED_AFTER_AUTHORITY.indexOf(c) >= 0 || (c == '#' && inFragment)) {
                encoded.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
            } else {
                inFragment |= c == '#';
                encoded.append(c);
            }
        }
        return encoded.toString();
    }

    /** An ASCII hexadecimal digit: {@link Character#digit} would take a full-width one too. */
    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * The path and query of an absolute URL on the server the request reached, so that a link the platform printed
     * absolute (the vanity URL of a site with its own server name) is sent as a path. Only the path and the query are
     * kept: never the scheme nor the host.
     *
     * @param url        an absolute URL, may be {@code null}
     * @param serverName the host name the request reached ({@code HttpServletRequest#getServerName()})
     * @return the path and query, when the URL is an {@code http}/{@code https} URL of this server and a safe path
     */
    public static Optional<String> pathOnServer(String url, String serverName) {
        if (url == null || serverName == null || url.isBlank() || url.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || !uri.getHost().equalsIgnoreCase(serverName)) {
                return Optional.empty();
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            return sameOriginPath(uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * Swaps the context path the engine's servlet reports ({@code <context>/modules}) for the platform's, which is what
     * a page link must carry once the URL rewriting has run. A URL that does not start with the servlet's context path
     * is returned as is.
     */
    public static String swapContextPath(String rewrittenUrl, String servletContextPath, String platformContextPath) {
        if (rewrittenUrl == null || servletContextPath == null || servletContextPath.isEmpty()
                || !rewrittenUrl.startsWith(servletContextPath + "/")) {
            return rewrittenUrl;
        }
        String platform = platformContextPath == null ? "" : platformContextPath;
        return platform + rewrittenUrl.substring(servletContextPath.length());
    }

    /**
     * A backslash (some browsers read {@code /\evil.com} as {@code //evil.com}), a control character, a space or line
     * separator of any kind, or an invisible format character such as U+FEFF: the browser refuses the same set
     * ({@code useFormSubmission.ts}), so the two checks agree on what is sent.
     */
    private static boolean hasForbiddenCharacter(String value) {
        // by code point, as the browser's regular expression reads it: a format character outside the BMP, such as the
        // tag characters U+E0001-U+E007F, is two surrogates here and none of them is FORMAT
        return value.codePoints().anyMatch(c -> c == '\\' || c <= 0x20 || Character.isISOControl(c)
                || Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT);
    }
}
