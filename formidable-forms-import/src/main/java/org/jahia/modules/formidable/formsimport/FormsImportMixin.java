package org.jahia.modules.formidable.formsimport;

/** The mixins this module declares (META-INF/definitions.cnd): the markers of an import, source-agnostic. */
public final class FormsImportMixin {

    /** On a form the import created: what it was in the source system. */
    public static final String IMPORTED_FORM = "fmdbmix:importedForm";
    /** On a field the import created: the field it stands for in the source system. */
    public static final String IMPORTED_FIELD = "fmdbmix:importedField";
    /** On a results entry that holds imported submissions. */
    public static final String IMPORTED_RESULTS = "fmdbmix:importedResults";
    /** On an imported submission: its identity in the source system. */
    public static final String IMPORTED_SUBMISSION = "fmdbmix:importedSubmission";
    /** Set on the results root of a site by the first import: the root may then hold the jobs node. */
    public static final String IMPORT_JOBS_PARENT = "fmdbmix:importJobsParent";

    private FormsImportMixin() {
    }
}
