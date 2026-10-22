package org.jahia.modules.formidable.formsimport.jahiaforms;

import org.jahia.modules.formidable.engine.api.VisitorProfileSchema;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * What the instance the import runs on offers, which decides what the converter carries over: the node types
 * it registers (the extended inputs), the options sources it declares (the country field), the field types
 * that take a mapping to a visitor profile property and the properties the profile server declares (the
 * prefills), whether a captcha is configured, and the pages of the site (the redirects).
 *
 * @param registeredTypes whether the repository registers a node type
 * @param declaredOptionsSources whether the instance declares an options source by key
 * @param profileMappable whether a field type takes a mapping to a visitor profile property: the jExperience
 *                        integration is installed and its mixin reaches the type
 * @param profileFit what the profile server of the site says of a property for a field
 * @param captchaConfigured whether the instance configures a captcha, for the forms that displayed one
 * @param sitePages the pages a redirect of Forms names, and the pages that display a Forms form
 */
public record ImportEnvironment(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources,
                                Predicate<String> profileMappable, ProfileFit profileFit, boolean captchaConfigured,
                                SitePages sitePages) {

    /** {@link VisitorProfileSchema#fit} for the site the import writes into. */
    @FunctionalInterface
    public interface ProfileFit {
        VisitorProfileSchema.Fit fit(String fieldType, boolean multiple, OptionalInt choices, String property);
    }

    /** What the repository knows of the pages a Forms form relates to, which the export does not hold. */
    public interface SitePages {

        /**
         * The page of the site the import writes into that a redirect of Forms names.
         *
         * @param reference the {@code redirectto} of a {@code fcnt:redirectToAPageAction}: a path of the source site, or
         *                  the identifier of the page
         * @return the identifier of the page in the edit workspace, or empty when the site holds no such page
         */
        Optional<String> pageOf(String reference);

        /**
         * The {@code fcnt:formDisplay} nodes that show a Forms form, in live: the redirect URL a display may carry
         * ({@code fcmix:formRedirectUrl}) lives there, on the page, and not in the {@code formFactory} export.
         *
         * @param formUuid the {@code jcr:uuid} of the Forms form
         * @return the displays, ordered by path; empty when Forms is not installed, or the form is shown nowhere
         */
        List<FormsDisplay> displaysOf(String formUuid);
    }

    /** A repository that holds no page: a redirect to a page is reported, and no display is found. */
    public static final SitePages NO_PAGES = new SitePages() {
        @Override
        public Optional<String> pageOf(String reference) {
            return Optional.empty();
        }

        @Override
        public List<FormsDisplay> displaysOf(String formUuid) {
            return List.of();
        }
    };

    /** A profile server that takes every property: the mapping is written as the source had it. */
    public static final ProfileFit ANY_PROPERTY = (fieldType, multiple, choices, property) -> VisitorProfileSchema.Fit.FITS;

    /** An instance whose repository holds no page, the default of the earlier callers. */
    public ImportEnvironment(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources,
                             Predicate<String> profileMappable, ProfileFit profileFit, boolean captchaConfigured) {
        this(registeredTypes, declaredOptionsSources, profileMappable, profileFit, captchaConfigured, NO_PAGES);
    }

    /** An instance that offers everything but the jExperience integration, the default of the earlier callers. */
    public static ImportEnvironment of(Predicate<String> registeredTypes, Predicate<String> declaredOptionsSources, boolean captchaConfigured) {
        return new ImportEnvironment(registeredTypes, declaredOptionsSources, type -> false, ANY_PROPERTY, captchaConfigured);
    }
}
