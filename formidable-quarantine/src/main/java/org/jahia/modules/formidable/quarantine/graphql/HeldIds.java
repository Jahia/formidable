package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.formidable.quarantine.store.QuarantineTree;

import java.util.regex.Pattern;

/**
 * The identifier of a held submission in the GraphQL API: its path below the quarantine root of its site,
 * {@code <formId>/YYYY/MM/DD/held-<uuid>}. The API always reads it below the root of the site of the query, so an
 * identifier of another site names nothing there.
 */
final class HeldIds {

    /** A site key: no path separator, so the key names one child of {@code /sites}. */
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** A JCR identifier of a form: no path separator. */
    private static final Pattern FORM_ID = Pattern.compile("[A-Fa-f0-9-]{1,64}");
    /** The path of a held submission below the root, as the store writes it: no other node, no relative segment. */
    private static final Pattern HELD_ID = Pattern.compile(
            "[A-Fa-f0-9-]{1,64}/\\d{4}/\\d{2}/\\d{2}/held-[A-Fa-f0-9-]{36}");

    private HeldIds() {
    }

    static boolean isSiteKey(String siteKey) {
        return siteKey != null && SITE_KEY.matcher(siteKey).matches();
    }

    static boolean isFormId(String formId) {
        return formId != null && FORM_ID.matcher(formId).matches();
    }

    /** @return the path of the held submission below the root of the site, or {@code null} for any other value */
    static String pathOf(String siteKey, String id) {
        if (!isSiteKey(siteKey) || id == null || !HELD_ID.matcher(id).matches()) {
            return null;
        }
        return QuarantineTree.rootPath(siteKey) + "/" + id;
    }

    /** @return the identifier of the held submission at the path, which lies below the root of the site */
    static String idOf(String siteKey, String path) {
        String root = QuarantineTree.rootPath(siteKey) + "/";
        if (path == null || !path.startsWith(root)) {
            throw new IllegalArgumentException("Not a path of the quarantine tree of site " + siteKey + ": " + path);
        }
        return path.substring(root.length());
    }
}
