package org.jahia.test.modules.formidable.samples.actions.field;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The configuration of a sample check's service, one PID per check ({@link ExperianEmailFieldAction#PID},
 * {@link ZeroBounceEmailFieldAction#PID}), handed to the engine's {@code ProviderFieldAction.configure}. The file the
 * samples ship ({@code META-INF/configurations/<PID>.cfg}) sets it; the administrator edits the copy in
 * {@code karaf/etc}. A project copying a check copies this definition into its module too: the metatype of a
 * component is generated from a definition in the component's own bundle.
 *
 * <p>The credential is the private property {@code .credential}: Declarative Services publishes a component's
 * configuration as the properties of the service it registers — readable by anyone listing services — except the
 * names starting with a dot. {@code AttributeType.PASSWORD} only masks the configuration form.</p>
 */
@ObjectClassDefinition(name = "Formidable samples — mailbox check service",
        description = "Where a field action calls its external service, and the credential the service gave you.")
public @interface SampleEndpointConfig {

    @AttributeDefinition(name = "URL", description = "The service's base URL: HTTPS, or plain HTTP on localhost or "
            + "host.docker.internal for a development double. No query string: a key the service reads off the URL is "
            + "the credential.")
    String url() default "";

    /** The file's {@code .credential}: the leading dot keeps it off the service registry. */
    @AttributeDefinition(name = "Credential", description = "The secret the service gave you. Never logged, never shown "
            + "to a contributor, never published with the service. Empty: the check does not run.",
            type = AttributeType.PASSWORD)
    String _credential() default "";

    @AttributeDefinition(name = "Development double", description = "The URL is a double of the service on this machine. "
            + "Honoured only while enableDevFieldActionEndpoints is on in org.jahia.modules.formidable.fieldActions.cfg. "
            + "Never in production.")
    boolean development() default false;
}
