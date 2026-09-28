package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The choice options theme: the sources a contributor may pick to fill a choice field, their cache, and the
 * cap on a content-mode field. Deployed as {@code karaf/etc/org.jahia.modules.formidable.choiceOptions.cfg}.
 */
@ObjectClassDefinition(
        name = "Formidable — Choice options",
        description = "The sources a choice field may take its options from, how long they are cached, and how many options a content query may return."
)
public @interface ChoiceOptionsConfig {

    long DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS = 300L;
    int DEFAULT_OPTIONS_QUERY_MAX_RESULTS = 100;

    @AttributeDefinition(
            name = "Options sources",
            description = "Newline-separated list of options sources a contributor can pick to fill a choice field. " +
                    "Each entry has the form: id|Label|initializerKey or id|Label|initializerKey|param, " +
                    "where initializerKey is the key of a Jahia choicelist initializer (for example country, language) " +
                    "and param its optional parameter. A Label of the form module:resource.key is resolved against " +
                    "that module's resource bundle in the editor's UI language. The id is stored in JCR; " +
                    "only sources listed here are exposed. Leave empty to disable sourced options (fail-safe default).",
            type = AttributeType.STRING
    )
    String optionsSources() default "";

    @AttributeDefinition(
            name = "Options sources cache TTL (seconds)",
            description = "How long a resolved option list is served from the in-memory cache before the source " +
                    "is asked again, per source and language. Default: 300 seconds.",
            type = AttributeType.LONG
    )
    long optionsSourcesCacheTtlSeconds() default DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS;

    @AttributeDefinition(
            name = "Options query max results",
            description = "Maximum number of options a content-mode choice field may resolve. Above the limit the " +
                    "field fails explicitly like a failing source, so contributors re-scope their root instead of " +
                    "visitors silently missing options. Default: 100.",
            type = AttributeType.INTEGER
    )
    int optionsQueryMaxResults() default DEFAULT_OPTIONS_QUERY_MAX_RESULTS;
}
