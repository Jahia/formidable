package org.jahia.modules.formidable.formsimport;

/** The node types this module declares (META-INF/definitions.cnd), named once: the class states the kind, the constant the thing. */
public final class FormsImportNodeType {

    /** The jobs of the imports of a site, a child of the results root. */
    public static final String IMPORT_JOBS = "fmdb:importJobs";
    /** One import: the uploaded export, the state and the report. */
    public static final String IMPORT_JOB = "fmdb:importJob";

    private FormsImportNodeType() {
    }
}
