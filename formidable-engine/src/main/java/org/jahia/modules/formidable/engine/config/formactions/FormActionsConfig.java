package org.jahia.modules.formidable.engine.config.formactions;

import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The form actions theme: the targets a forward action may send a submission to, their development list, and
 * the timeouts of that call. Deployed as {@code karaf/etc/org.jahia.modules.formidable.formActions.cfg}.
 */
@ObjectClassDefinition(
        name = "Formidable — Form actions",
        description = "The services a form action may forward a submission to, and the timeouts of that call."
)
public @interface FormActionsConfig {

    @AttributeDefinition(
            name = "Forward action targets",
            description = "Newline-separated list of allowed forward targets for fmdb:forwardAction. " +
                    "Each entry has the form: id|Label|https://target-url. " +
                    "Commas inside labels or URLs are preserved. " +
                    "The id is stored in JCR; the URL is resolved server-side and never exposed to contributors. " +
                    "Leave empty to disable all forward actions (fail-safe default).",
            type = AttributeType.STRING
    )
    String forwardTargets() default "";

    @AttributeDefinition(
            name = "Enable development forward targets",
            description = "Allows use of devForwardTargets. Disabled by default. " +
                    "When enabled, only plain HTTP targets on localhost or host.docker.internal are accepted.",
            type = AttributeType.BOOLEAN
    )
    boolean enableDevForwardTargets() default false;

    @AttributeDefinition(
            name = "Development forward action targets",
            description = "Newline-separated list of development-only forward targets for fmdb:forwardAction. " +
                    "Each entry has the form: id|Label|http://localhost/... or id|Label|http://host.docker.internal/... " +
                    "Ignored unless 'Enable development forward targets' is true.",
            type = AttributeType.STRING
    )
    String devForwardTargets() default "";

    @AttributeDefinition(
            name = "Forward action HTTP connect timeout (seconds)",
            description = "Maximum time allowed to establish the outbound connection for fmdb:forwardAction requests. Default: 5 seconds.",
            type = AttributeType.LONG
    )
    long forwardHttpConnectTimeoutSeconds() default ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS;

    @AttributeDefinition(
            name = "Forward action HTTP request timeout (seconds)",
            description = "Maximum total time allowed for fmdb:forwardAction outbound requests. Default: 10 seconds.",
            type = AttributeType.LONG
    )
    long forwardHttpRequestTimeoutSeconds() default ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS;
}
