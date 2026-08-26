package org.jahia.modules.formidable.engine.options;

/**
 * A content-mode option query resolved more results than the administrator-configured
 * {@code optionsQueryMaxResults}. Distinct from the generic resolution failure so the
 * editor preview can tell the contributor the limit — and that an administrator can
 * raise it — instead of a generic unavailability message. Rendering and validation
 * treat it like any failing source (degraded field, unverifiable values).
 */
public class OptionsQueryCapExceededException extends IllegalStateException {

    private final int limit;

    public OptionsQueryCapExceededException(String scope, int limit) {
        super("Choice field '" + scope + "' resolves more than " + limit
                + " options (optionsQueryMaxResults); narrow the root node or raise the limit");
        this.limit = limit;
    }

    public int getLimit() {
        return limit;
    }
}
