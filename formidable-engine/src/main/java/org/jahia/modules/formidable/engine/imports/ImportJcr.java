package org.jahia.modules.formidable.engine.imports;

/** The facts of the repository layout the import needs: the two workspaces, and the path of a site, under which everything it writes sits. */
final class ImportJcr {

    static final String EDIT_WORKSPACE = "default";
    static final String LIVE_WORKSPACE = "live";
    private static final String SITES = "/sites/";

    private ImportJcr() {
    }

    /** {@code /sites/<siteKey>}. */
    static String site(String siteKey) {
        return SITES + siteKey;
    }
}
