package org.jahia.modules.formidable.engine.config.choiceoptions;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The choice options configuration: the cache of the resolved options and the cap on a content-mode field. Deployed
 * as {@code karaf/etc/org.jahia.modules.formidable.choiceOptions.cfg}; the sources themselves are one file each,
 * {@code org.jahia.modules.formidable.choiceOptions.source-<id>.cfg} ({@link OptionsSourceConfig}).
 */
@ObjectClassDefinition(
        name = "Formidable — Choice options",
        description = "How long the options of a source are cached, and how many options a content query may return. The sources are one configuration each."
)
public @interface ChoiceOptionsConfig {

    long DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS = 300L;
    int DEFAULT_OPTIONS_QUERY_MAX_RESULTS = 100;


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
