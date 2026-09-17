package org.jahia.modules.formidable.engine.api;

/**
 * The names of the items — properties and child nodes — another module reads on Formidable
 * content: a field's business identity and its conditional logic, a choice field's options, and
 * the way from a results node to the form and to one submission's data. A property name is never
 * namespaced, whichever type declares it (docs/architecture/cnd-module-ownership.md, "Naming a
 * property").
 * <p>
 * Unlike {@link FormidableNodeTypes} and {@link FormidableMixins}, this class is not the whole
 * vocabulary of the engine's CND, and is not meant to become it: a mixin is how a module opts
 * into engine behaviour, so every one of them is contract, while a property is local to the type
 * that declares it until something outside reads it. A name reaches this class when it does.
 * <p>
 * These are compile-time constants — see {@link FormidableNodeTypes} for what that implies.
 */
public final class FormidableProperties {

    /**
     * Stable business identity of a form element, independent from the JCR UUID (changes on
     * import or copy), the node name (changes on rename) and the visible label. Conditional-logic
     * rules persist it as their source reference.
     */
    public static final String FIELD_KEY_PROPERTY = "fieldKey";

    /** The conditional-logic rules of an element, one JSON string per rule. */
    public static final String LOGICS_PROPERTY = "logics";

    /** On a logic source node, the weakreference to the element the rule observes. */
    public static final String LOGIC_NODE_SOURCE_PROPERTY = "logicNodeSource";

    /** The manually authored choices of a choice field, in the site's default language. */
    public static final String OPTIONS_PROPERTY = "options";

    /** Which options mode a choice field uses, hence which dynamic-fieldset mixin it carries. */
    public static final String OPTIONS_MODE_PROPERTY = "optionsMode";

    /** On a results node, the weakreference to the form whose submissions it holds. */
    public static final String PARENT_FORM_PROPERTY = "parentForm";

    // Child node names
    public static final String LOGICS_SRC_NODE = "logicsSrc";
    public static final String SUBMISSIONS_NODE = "submissions";
    public static final String DATA_NODE = "data";
    public static final String FILES_NODE = "files";

    private FormidableProperties() {
    }
}
