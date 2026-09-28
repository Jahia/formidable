package org.jahia.modules.formidable.engine.api;

import java.io.IOException;

/**
 * The one way a field action reaches an external service: a provider declared by the administrator in
 * {@code org.jahia.modules.formidable.cfg}
 * ({@code fieldActionProviders=id|Label|https://base-url|Credential-name|credential[|header|query]}), addressed by
 * its id. The base URL, the credential's name and the credential stay in the engine; the field-action node stores the
 * provider <em>id</em>, and a Java action — {@link ProviderFieldAction} reads that id off the node — calls this service.
 *
 * <p>The gateway appends a <strong>relative</strong> path to the provider's base URL — a path that is absolute,
 * carries a scheme or climbs with {@code ..} is refused, so a validator cannot be pointed at another host — sends
 * the credential as the request header or the query parameter the provider line names, applies the configured
 * timeouts, caps the response body, and never writes the credential in a log line or in the object it returns.</p>
 *
 * @see FieldAction
 */
public interface FieldActionGateway {

    /**
     * A provider's answer.
     *
     * @param status the HTTP status
     * @param body   the response body as text, capped to the configured size; empty when the provider sent none
     */
    record Response(int status, String body) {
    }

    /**
     * Posts a JSON body to the provider.
     *
     * @param providerId the id of a configured provider
     * @param path       a relative path under the provider's base URL, for example {@code "email/validate"}
     * @param jsonBody   the request body, sent as {@code application/json}
     * @throws IllegalArgumentException when the provider is unknown or the path is not relative
     * @throws IOException              when the provider does not answer in time or the connection fails
     */
    Response post(String providerId, String path, String jsonBody) throws IOException;

    /**
     * Gets a resource from the provider.
     *
     * @param providerId the id of a configured provider
     * @param path       a relative path under the provider's base URL, query string included if needed
     * @throws IllegalArgumentException when the provider is unknown or the path is not relative
     * @throws IOException              when the provider does not answer in time or the connection fails
     */
    Response get(String providerId, String path) throws IOException;
}
