package org.jahia.modules.formidable.jexperience.engine.model;

/**
 * The mixins this module's CND declares, named once — the way the engine names its own in
 * {@code FmdbMixin}: the class states the kind, the constant states the thing, and a use is always
 * qualified. Nothing here is exported: the module's model is read by the module alone, and
 * {@code DefinitionsCndTest} keeps this class and {@code META-INF/definitions.cnd} saying the same.
 */
public final class JxpMixin {

    /** The mapping of a field to a visitor profile property, with its write strategy and its prefill. */
    public static final String MAPPING = "fmdbmix:jExperienceProfileMapping";

    /**
     * DEPRECATED, removed in 0.6: the sensitive flag's first home. The flag is the engine's {@code FmdbProperty.PROFILE_SENSITIVE}
     * on its marker since #369; the mixin stays declared, hidden, until the engine's migration has run everywhere.
     */
    public static final String SENSITIVE_FIELD = "fmdbmix:jExperienceSensitiveField";

    private JxpMixin() {
    }
}
