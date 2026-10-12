package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.util.function.Predicate;

/**
 * What the instance the import runs on offers, which decides what the converter carries over: the node types
 * it registers (the extended inputs), the options sources it declares (the country field), the field types
 * that take a mapping to a visitor profile property (the prefills), and whether a captcha is configured.
 *
 * @param registeredTypes whether the repository registers a node type
 * @param declaredOptionsSources whether the instance declares an options source by key
 * @param profileMappable whether a field type takes a mapping to a visitor profile property: the jExperience
 *                        integration is installed and its mixin reaches the type
 * @param captchaConfigured whether the instance configures a captcha, for the forms that displayed one
 */
public record ImportEnvironment(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources,
                                Predicate<String> profileMappable, boolean captchaConfigured) {

    /** An instance that offers everything but the jExperience integration, the default of the earlier callers. */
    public static ImportEnvironment of(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources, boolean captchaConfigured) {
        return new ImportEnvironment(registeredTypes, declaredOptionsSources, type -> false, captchaConfigured);
    }
}
