package org.jahia.modules.formidable.engine.config.formactions;

import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The form actions configuration: the switch of the development targets, and the timeouts of the forward call.
 * Deployed as {@code karaf/etc/org.jahia.modules.formidable.formActions.cfg}; the targets themselves are one file
 * each, {@code org.jahia.modules.formidable.formActions.target-<id>.cfg} ({@link ForwardTargetConfig}).
 */
@ObjectClassDefinition(
        name = "Formidable — Form actions",
        description = "The services a form action may forward a submission to, and the timeouts of that call."
)
public @interface FormActionsConfig {


    @AttributeDefinition(
            name = "Enable development forward targets",
            description = "Honours the forward target files marked as development targets (plain HTTP on localhost or " +
                    "host.docker.internal). Disabled by default; never in production.",
            type = AttributeType.BOOLEAN
    )
    boolean enableDevForwardTargets() default false;


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
