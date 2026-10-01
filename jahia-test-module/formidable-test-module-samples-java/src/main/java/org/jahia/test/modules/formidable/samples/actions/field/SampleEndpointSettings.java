package org.jahia.test.modules.formidable.samples.actions.field;

/**
 * The wording the two sample checks share for the three settings of their configuration. Each check carries its own
 * {@code @ObjectClassDefinition}, named after its service, so that the configuration manager tells the two apart;
 * what the settings mean is the same. A project copying a check copies the definition into its module: the metatype
 * of a component is generated from a definition in the component's own bundle.
 *
 * <p>The credential is the private property {@code .credential}: Declarative Services publishes a component's
 * configuration as the properties of the service it registers — readable by anyone listing services — except the
 * names starting with a dot. {@code AttributeType.PASSWORD} only masks the configuration form.</p>
 */
final class SampleEndpointSettings {

    static final String URL_NAME = "URL";
    static final String URL_DESCRIPTION = "The service's base URL: HTTPS, or plain HTTP on localhost or host.docker.internal "
            + "for a development double. No query string: a key the service reads off the URL is the credential.";
    static final String CREDENTIAL_NAME = "Credential";
    static final String CREDENTIAL_DESCRIPTION = "The secret the service gave you. Never logged, never shown to a contributor, "
            + "never published with the service. Empty: the check does not run.";
    static final String DEVELOPMENT_NAME = "Development double";
    static final String DEVELOPMENT_DESCRIPTION = "The URL is a double of the service on this machine. Honoured only while "
            + "enableDevFieldActionEndpoints is on in org.jahia.modules.formidable.fieldActions.cfg. Never in production.";

    private SampleEndpointSettings() {
    }
}
