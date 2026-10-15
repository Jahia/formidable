package org.jahia.modules.formidable.engine.api;

/**
 * The options sources the configuration declares, the ones a sourced choice field may point at by its
 * {@code optionsSourceKey}: read by a module that creates choice fields (an import) before it points one at a
 * source. An OSGi service of the engine.
 */
public interface ChoiceOptionsSources {

    /** Whether the configuration declares an options source under that key. */
    boolean declares(String key);
}
