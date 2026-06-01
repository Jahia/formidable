package org.jahia.modules.formidable.engine.util;

public final class FormidableJcrConstants {

    public static final String WORKSPACE_LIVE = "live";

    public static final String ACL_NODE_TYPE = "jnt:acl";
    public static final String ACE_NODE_TYPE = "jnt:ace";
    public static final String FORM_NODE_TYPE = "fmdb:form";
    public static final String FORM_RESULTS_NODE_TYPE = "fmdb:formResults";

    public static final String AUTHENTICATED_ONLY_FORM_MIXIN = "fmdbmix:authenticatedOnlyForm";
    public static final String CAPTCHA_PROTECTED_FORM_MIXIN = "fmdbmix:captchaProtectedForm";
    public static final String FORM_CONTAINER_MIXIN = "fmdbmix:formContainer";
    public static final String FORM_ELEMENT_MIXIN = "fmdbmix:formElement";
    public static final String FORM_LOGIC_ELEMENT_MIXIN = "fmdbmix:formLogicElement";
    public static final String FORM_STEP_MIXIN = "fmdbmix:formStep";
    public static final String NON_SUBMITTABLE_MIXIN = "fmdbmix:nonSubmittable";

    public static final String ACL_NODE = "j:acl";
    public static final String FIELDS_NODE = "fields";
    public static final String INHERIT_PROPERTY = "j:inherit";
    public static final String LOGIC_NODE_SOURCE_PROPERTY = "logicNodeSource";
    public static final String LOGICS_PROPERTY = "logics";
    public static final String LOGICS_SRC_NODE = "logicsSrc";
    public static final String PARENT_FORM_PROPERTY = "parentForm";
    public static final String ROLES_PROPERTY = "j:roles";

    private FormidableJcrConstants() {
    }
}
