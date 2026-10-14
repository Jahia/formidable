package org.jahia.modules.formidable.engine.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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

    /**
     * The path and query of the page the visitor submitted from, read from the {@code Referer} header, on the
     * condition that the page is on the server the request reached. The Jahia security filter already refuses a
     * submission whose Origin or Referer is not hosted by the platform; this repeats the host comparison so the
     * helper is safe on its own, and keeps only the path and the query: never the scheme nor the host.
     *
     * @param referer    the header value, may be {@code null}
     * @param serverName the host name the request reached ({@code HttpServletRequest#getServerName()})
     * @return the path and query of the page, when it is a page of this server and a safe path
     */
    public static Optional<String> pathOfReferer(String referer, String serverName) {
        if (referer == null || serverName == null || referer.isBlank() || referer.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(referer.trim());
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
     * The value of one query parameter of the page the visitor submitted from, accepted only when it is a safe
     * same-origin path: the "send me back where I was going" pattern ({@code /login?redirect=/account}).
     *
     * @param referer       the {@code Referer} header, may be {@code null}
     * @param serverName    the host name the request reached
     * @param parameterName the query parameter to read, for instance {@code redirect}
     * @return the path, when the page is on this server, the parameter is present and its value is a safe path
     */
    public static Optional<String> returnPath(String referer, String serverName, String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return Optional.empty();
        }
        Optional<String> page = pathOfReferer(referer, serverName);
        if (page.isEmpty()) {
            return Optional.empty();
        }
        String pagePath = page.get();
        int question = pagePath.indexOf('?');
        if (question < 0) {
            return Optional.empty();
        }
        for (String pair : pagePath.substring(question + 1).split("&")) {
            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            if (parameterName.equals(name)) {
                return sameOriginPath(equals < 0 ? "" : decode(pair.substring(equals + 1)));
            }
        }
        return Optional.empty();
    }

    /**
     * Swaps the context path the OSGi servlet reports ({@code /modules}) for the platform's, which is what a page
     * link must carry once the URL rewriting has run. A URL that does not start with the servlet's context path is
     * returned as is.
     */
    public static String swapContextPath(String rewrittenUrl, String servletContextPath, String platformContextPath) {
        if (rewrittenUrl == null || servletContextPath == null || servletContextPath.isEmpty()
                || !rewrittenUrl.startsWith(servletContextPath + "/")) {
            return rewrittenUrl;
        }
        String platform = platformContextPath == null ? "" : platformContextPath;
        return platform + rewrittenUrl.substring(servletContextPath.length());
    }

    private static String decode(String raw) {
        try {
            return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
