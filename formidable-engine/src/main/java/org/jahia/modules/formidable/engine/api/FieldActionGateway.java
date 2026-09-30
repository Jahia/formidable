package org.jahia.modules.formidable.engine.api;

import org.jahia.modules.formidable.engine.config.common.EndpointRule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The one way a field action reaches an external service. The action owns where it calls: its module reads the
 * service's base URL and credential from its own configuration and hands them over as an {@link Endpoint}; the
 * gateway does the rest the same way for every action.
 *
 * <p>It appends a <strong>relative</strong> path to the endpoint's base URL — a path that is absolute, carries a
 * scheme, climbs with {@code ..} or holds a control character is refused, so an action cannot be pointed at another
 * host by a value — sends the credential as the request header or the query parameter the endpoint names, applies
 * the timeouts of {@code org.jahia.modules.formidable.fieldActions.cfg}, caps the response body, and never writes the
 * credential in a log line or in the object it returns.</p>
 *
 * @see ProviderFieldAction
 */
public interface FieldActionGateway {

    /**
     * A service's answer.
     *
     * @param status the HTTP status
     * @param body   the response body as text, capped to the configured size; empty when the service sent none
     */
    record Response(int status, String body) {
    }

    /**
     * Where a field action calls, as its module's configuration describes it. Build it with {@link #of}, which checks
     * it; the gateway checks the URL again on every call.
     *
     * @param name              what the service is called in log lines ({@code "experian"}), never a secret
     * @param baseUri           the base the gateway appends a relative path to: HTTPS with a host and no embedded
     *                          credentials, or — for a development endpoint — plain HTTP on localhost or
     *                          host.docker.internal
     * @param credentialName    the request header, or the query parameter, carrying the credential; empty when the
     *                          service needs none
     * @param credential        the secret; never logged, never returned — {@link #toString()} hides it
     * @param credentialInQuery whether the credential travels as a query parameter rather than a request header
     * @param development       whether the endpoint is a double of the service on this machine
     */
    record Endpoint(String name, URI baseUri, String credentialName, String credential, boolean credentialInQuery,
                    boolean development) {

        /** Where a credential goes: a request header of its name (the default), or a query parameter of its name. */
        public static final String CREDENTIAL_IN_HEADER = "header";
        public static final String CREDENTIAL_IN_QUERY = "query";

        /**
         * An endpoint from a module's configuration values, checked: a base URL that parses and obeys the rule above,
         * a credential's name and value together or neither, a placement that is {@value #CREDENTIAL_IN_HEADER} or
         * {@value #CREDENTIAL_IN_QUERY}, a query credential with its value.
         *
         * @throws IllegalArgumentException naming what is wrong — never the credential
         */
        public static Endpoint of(String name, String url, String credentialName, String credential, String credentialIn,
                                  boolean development) {
            String header = credentialName == null ? "" : credentialName.trim();
            String secret = credential == null ? "" : credential.trim();
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("the URL of " + name + " is missing");
            }
            if (header.isEmpty() != secret.isEmpty()) {
                throw new IllegalArgumentException("the credential of " + name + " needs its name, and a name its credential");
            }
            String where = credentialIn == null || credentialIn.isBlank() ? CREDENTIAL_IN_HEADER : credentialIn.trim().toLowerCase(Locale.ROOT);
            if (!CREDENTIAL_IN_HEADER.equals(where) && !CREDENTIAL_IN_QUERY.equals(where)) {
                throw new IllegalArgumentException("the credential of " + name + " goes in '" + CREDENTIAL_IN_HEADER + "' or in '"
                        + CREDENTIAL_IN_QUERY + "'");
            }
            if (CREDENTIAL_IN_QUERY.equals(where) && secret.isEmpty()) {
                throw new IllegalArgumentException("a credential of " + name + " in the query needs its parameter name and its value");
            }
            URI base;
            try {
                base = new URI(url.trim());
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("the URL of " + name + " is malformed: '" + url.trim() + "'", e);
            }
            String reason = EndpointRule.unsupportedReason(base, development);
            if (reason != null) {
                throw new IllegalArgumentException("the URL of " + name + " is refused: " + reason);
            }
            return new Endpoint(name, base, header, secret, CREDENTIAL_IN_QUERY.equals(where), development);
        }

        @Override
        public String toString() {
            return "Endpoint[name=" + name + ", baseUri=" + baseUri + ", credentialName=" + credentialName
                    + ", credentialIn=" + (credentialInQuery ? CREDENTIAL_IN_QUERY : CREDENTIAL_IN_HEADER)
                    + ", credential=" + (credential == null || credential.isEmpty() ? "none" : "***")
                    + ", development=" + development + "]";
        }
    }

    /**
     * Posts a JSON body to the service.
     *
     * @param endpoint where the service is, from the action's configuration
     * @param path     a relative path under the endpoint's base URL, for example {@code "email/validate"}
     * @param jsonBody the request body, sent as {@code application/json}
     * @throws IllegalArgumentException when the endpoint's URL is refused or the path is not relative
     * @throws IOException              when the service does not answer in time or the connection fails
     */
    Response post(Endpoint endpoint, String path, String jsonBody) throws IOException;

    /**
     * Gets a resource from the service.
     *
     * @param endpoint where the service is, from the action's configuration
     * @param path     a relative path under the endpoint's base URL, query string included if needed
     * @throws IllegalArgumentException when the endpoint's URL is refused or the path is not relative
     * @throws IOException              when the service does not answer in time or the connection fails
     */
    Response get(Endpoint endpoint, String path) throws IOException;
}
