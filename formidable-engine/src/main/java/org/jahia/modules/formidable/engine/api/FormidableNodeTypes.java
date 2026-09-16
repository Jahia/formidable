package org.jahia.modules.formidable.engine.api;

/**
 * The primary node types the engine's own CND declares, for the modules that read or write
 * Formidable content — the jExperience integration, a third-party action, a content-integrity
 * check. The names of the authoring model (the form, the fieldsets, the concrete field types)
 * are deliberately absent: formidable-elements declares them, and server-side code reads a
 * mixin of {@link FormidableMixins} rather than a concrete type name. See
 * docs/architecture/cnd-module-ownership.md, "Naming these types from Java".
 * <p>
 * These are compile-time constants, so a consumer's bytecode carries the value rather than a
 * reference to this class. That is sound for a node type name: it is a persistence contract,
 * and changing one is a content migration, never a silent update.
 */
public final class FormidableNodeTypes {

    // Conditional-logic storage
    public static final String LOGIC_SRC_NODE_TYPE = "fmdb:logicSrc";
    public static final String LOGIC_LIST_NODE_TYPE = "fmdb:logicList";

    // Built-in actions
    public static final String EMAIL_NOTIFICATION_ACTION_NODE_TYPE = "fmdb:emailNotificationAction";
    public static final String EMAIL_CONTENT_ACTION_NODE_TYPE = "fmdb:emailContentAction";
    public static final String FORWARD_ACTION_NODE_TYPE = "fmdb:forwardAction";
    public static final String SAVE_TO_JCR_ACTION_NODE_TYPE = "fmdb:save2jcrAction";

    // Submission storage
    public static final String RESULTS_FOLDER_NODE_TYPE = "fmdb:resultsFolder";
    public static final String FORM_RESULTS_NODE_TYPE = "fmdb:formResults";
    public static final String SUBMISSIONS_NODE_TYPE = "fmdb:submissions";
    public static final String SPLITTED_SUBMISSION_NODE_TYPE = "fmdb:splittedSubmission";
    public static final String FORM_SUBMISSION_NODE_TYPE = "fmdb:formSubmission";
    public static final String SUBMISSION_DATA_NODE_TYPE = "fmdb:submissionData";

    private FormidableNodeTypes() {
    }
}
