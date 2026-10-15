package org.jahia.modules.formidable.content.page;

import org.jahia.modules.formidable.engine.api.RedirectTargets;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns the {@code Referer} of a submission into the repository paths of the page it names: the page the form was
 * submitted from. Pure and stateless. The header is the visitor's, so this trusts as little of it as possible and
 * only <em>proposes</em> paths; the action resolves them with the submitter's own live session, keeps the first that is
 * a readable page or main resource of the form's site, and leaves the property empty otherwise.
 *
 * <p>What is accepted, on the host the request reached ({@link RedirectTargets#pathOfReferer}): a path under
 * {@code /sites/<siteKey>/...}, optionally behind a language ({@code /fr/sites/...}) or behind Jahia's own render
 * prefix ({@code /cms/render/live/fr/sites/...}); the query and the fragment are dropped; the extension ({@code .html},
 * and a view name before it) is dropped from the last segment. What is refused: another site, anything outside
 * {@code /sites/<siteKey>/}, a vanity URL (nothing says which site it belongs to), an empty, dotted or encoded-separator
 * segment, a character a node name cannot hold.</p>
 */
public final class RefererPages {

    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}([_-][A-Za-z0-9]{2,8})*");
    private static final Pattern EXTENSION = Pattern.compile("[A-Za-z0-9]{1,10}");
    private static final String FORBIDDEN = "/:[]|*\\";
    private static final int MAX_SEGMENTS = 24;

    private RefererPages() {
    }

    /**
     * @param referer    the {@code Referer} header, may be {@code null}
     * @param serverName the host name the request reached
     * @param siteKey    the form's site
     * @return the candidate node paths, most specific first (the name as written, then with a view name dropped); empty
     *         when the header names nothing this action accepts
     */
    public static List<String> candidatePaths(String referer, String serverName, String siteKey) {
        if (siteKey == null || siteKey.isBlank()) {
            return List.of();
        }
        Optional<String> pathAndQuery = RedirectTargets.pathOfReferer(referer, serverName);
        if (pathAndQuery.isEmpty()) {
            return List.of();
        }
        String path = pathAndQuery.get();
        int cut = indexOfAny(path, '?', '#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        List<String> segments = new ArrayList<>();
        for (String raw : path.split("/", -1)) {
            segments.add(raw);
        }
        // the path starts with '/', so the first element is empty
        segments.remove(0);
        int start = prefixLength(segments);
        if (start < 0 || segments.size() - start < 3 || segments.size() - start > MAX_SEGMENTS
                || !segments.get(start).equals("sites") || !segments.get(start + 1).equals(siteKey)) {
            return List.of();
        }
        List<String> decoded = new ArrayList<>();
        for (int i = start; i < segments.size(); i++) {
            String segment = decode(segments.get(i));
            if (segment == null) {
                return List.of();
            }
            decoded.add(segment);
        }
        String last = decoded.remove(decoded.size() - 1);
        String parent = "/" + String.join("/", decoded);
        List<String> names = new ArrayList<>();
        String name = last;
        while (true) {
            int dot = name.lastIndexOf('.');
            if (dot <= 0 || !EXTENSION.matcher(name.substring(dot + 1)).matches()) {
                break;
            }
            name = name.substring(0, dot);
            names.add(name);
        }
        if (names.isEmpty()) {
            names.add(last);
        }
        List<String> candidates = new ArrayList<>();
        for (String candidate : names) {
            candidates.add(parent + "/" + candidate);
        }
        return candidates;
    }

    /** Segments before {@code sites}: none, a language, or {@code cms/render/<workspace>/<language>}; -1 when it is none of these. */
    private static int prefixLength(List<String> segments) {
        if (segments.isEmpty()) {
            return -1;
        }
        if (segments.get(0).equals("sites")) {
            return 0;
        }
        if (segments.size() > 4 && segments.get(0).equals("cms") && segments.get(1).equals("render")
                && (segments.get(2).equals("live") || segments.get(2).equals("default")) && LANGUAGE.matcher(segments.get(3)).matches()) {
            return 4;
        }
        if (LANGUAGE.matcher(segments.get(0)).matches()) {
            return 1;
        }
        return -1;
    }

    /** Percent-decodes one segment; {@code null} when it cannot be a node name. */
    private static String decode(String segment) {
        String decoded;
        try {
            decoded = URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (decoded.isBlank() || decoded.equals(".") || decoded.equals("..") || !decoded.equals(decoded.trim())) {
            return null;
        }
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c < 0x20 || c == 0x7f || FORBIDDEN.indexOf(c) >= 0) {
                return null;
            }
        }
        return decoded;
    }

    private static int indexOfAny(String text, char first, char second) {
        int a = text.indexOf(first);
        int b = text.indexOf(second);
        if (a < 0) {
            return b;
        }
        return b < 0 ? a : Math.min(a, b);
    }
}
