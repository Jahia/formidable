package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.engine.api.VisitorProfileSchema;

import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * What the instance the import runs on offers, which decides what the converter carries over: the node types
 * it registers (the extended inputs), the options sources it declares (the country field), the field types
 * that take a mapping to a visitor profile property and the properties the profile server declares (the
 * prefills), and whether a captcha is configured.
 *
 * @param registeredTypes whether the repository registers a node type
 * @param declaredOptionsSources whether the instance declares an options source by key
 * @param profileMappable whether a field type takes a mapping to a visitor profile property: the jExperience
 *                        integration is installed and its mixin reaches the type
 * @param profileFit what the profile server of the site says of a property for a field
 * @param captchaConfigured whether the instance configures a captcha, for the forms that displayed one
 */
public record ImportEnvironment(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources,
                                Predicate<String> profileMappable, ProfileFit profileFit, boolean captchaConfigured) {

    /** {@link VisitorProfileSchema#fit} for the site the import writes into. */
    @FunctionalInterface
    public interface ProfileFit {
        VisitorProfileSchema.Fit fit(String fieldType, boolean multiple, OptionalInt choices, String property);
    }

    /** A profile server that takes every property: the mapping is written as the source had it. */
    public static final ProfileFit ANY_PROPERTY = (fieldType, multiple, choices, property) -> VisitorProfileSchema.Fit.FITS;

    /** An instance that offers everything but the jExperience integration, the default of the earlier callers. */
    public static ImportEnvironment of(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources, boolean captchaConfigured) {
        return new ImportEnvironment(registeredTypes, declaredOptionsSources, type -> false, ANY_PROPERTY, captchaConfigured);
    }
}
