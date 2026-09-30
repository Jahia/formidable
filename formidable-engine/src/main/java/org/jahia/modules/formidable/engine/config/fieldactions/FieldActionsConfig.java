package org.jahia.modules.formidable.engine.config.fieldactions;

import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The field actions theme: the timeouts of the calls a field action makes to an external service — the service
 * itself is in the configuration of the module that ships the action — and the guards of the pre-check endpoint
 * (verdict cache, rate limit, value length, values per field). Deployed as
 * {@code karaf/etc/org.jahia.modules.formidable.fieldActions.cfg}.
 */
@ObjectClassDefinition(
        name = "Formidable — Field actions",
        description = "The timeouts of a field's check calling an external service, and the limits of the pre-check endpoint."
)
public @interface FieldActionsConfig {

    long DEFAULT_FIELD_ACTION_CACHE_TTL_SECONDS = 300L;
    int DEFAULT_FIELD_ACTION_RATE_LIMIT_PER_MINUTE = 30;
    int DEFAULT_FIELD_ACTION_MAX_VALUE_LENGTH = 512;
    int DEFAULT_FIELD_ACTION_MAX_VALUES_PER_FIELD = 50;

    @AttributeDefinition(
            name = "Field action HTTP connect timeout (seconds)",
            description = "Maximum time allowed to establish the connection to the service a field action calls. Default: 5 seconds.",
            type = AttributeType.LONG
    )
    long fieldActionHttpConnectTimeoutSeconds() default ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS;

    @AttributeDefinition(
            name = "Field action HTTP request timeout (seconds)",
            description = "Maximum total time allowed for a field action provider call. A provider slower than this " +
                    "counts as unavailable, and the contributor's 'If the check cannot run' setting decides. Default: 10 seconds.",
            type = AttributeType.LONG
    )
    long fieldActionHttpRequestTimeoutSeconds() default ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS;

    @AttributeDefinition(
            name = "Field action verdict cache TTL (seconds)",
            description = "How long a field action's verdict on one value is kept, per action, language and value, so that the " +
                    "check run while the visitor filled the form costs no second provider call at submission. " +
                    "0 disables the cache. Default: 300 seconds.",
            type = AttributeType.LONG
    )
    long fieldActionVerdictCacheTtlSeconds() default DEFAULT_FIELD_ACTION_CACHE_TTL_SECONDS;

    @AttributeDefinition(
            name = "Field action pre-check rate limit (calls per minute)",
            description = "Maximum number of field-action pre-checks accepted per client address and minute on the " +
                    "field-action endpoint, which is an open door to a possibly paid service for anyone on the site. " +
                    "0 disables the endpoint: the field actions then run at submission only. Default: 30.",
            type = AttributeType.INTEGER
    )
    int fieldActionRateLimitPerMinute() default DEFAULT_FIELD_ACTION_RATE_LIMIT_PER_MINUTE;

    @AttributeDefinition(
            name = "Field action max value length",
            description = "Maximum length of the value a pre-check accepts from the browser; a longer value is refused " +
                    "with HTTP 413. Default: 512 characters.",
            type = AttributeType.INTEGER
    )
    int fieldActionMaxValueLength() default DEFAULT_FIELD_ACTION_MAX_VALUE_LENGTH;

    @AttributeDefinition(
            name = "Field action values judged per field",
            description = "How many distinct values of one field the submission pipeline judges with that field's actions. "
                    + "A field name may be submitted many times over; each value may cost a provider call, so a "
                    + "submission carrying more than this for one field is refused (FMDB-017) rather than run.")
    int fieldActionMaxValuesPerField() default DEFAULT_FIELD_ACTION_MAX_VALUES_PER_FIELD;
}
