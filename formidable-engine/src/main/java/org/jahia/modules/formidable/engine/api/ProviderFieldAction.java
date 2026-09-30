package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * The shape of a field action behind an external service — what every check calling one has in common, so that a
 * module writes the call and the reading of the answer, nothing else. The service is the action's own: its module
 * reads the base URL and the credential from its own configuration ({@link #endpoint()}), and the contributor has no
 * service to pick. This class hands the endpoint to {@link #ask} with the request, and turns every way the call can
 * fail into an unavailable check — a service that is not configured, cannot be reached or times out, a URL the
 * endpoint rule refuses. An unavailable check is what the contributor's {@code whenUnavailable} setting decides: an
 * outage never refuses on its own.
 *
 * <p>A value the action does not judge at all ({@link #concerns}) is accepted without a call: every answer of a
 * service has a price. The gateway comes from the module, which holds the OSGi reference ({@link #gateway()}).</p>
 */
public abstract class ProviderFieldAction implements FieldAction {

    private static final Logger log = LoggerFactory.getLogger(ProviderFieldAction.class);

    @Override
    public final FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        return judge(request);
    }

    /** The verdict — the whole of the action, which is what a test drives. */
    public final FieldActionResult judge(FieldActionRequest request) {
        if (!concerns(request)) {
            return FieldActionResult.accept();
        }
        Optional<FieldActionGateway.Endpoint> endpoint = endpoint();
        if (endpoint.isEmpty()) {
            return FieldActionResult.unavailable("the service is not configured");
        }
        try {
            return ask(endpoint.get(), request);
        } catch (IOException | IllegalArgumentException e) {
            // Unreachable, timed out, or a URL the endpoint rule refuses: not a verdict on the value.
            String service = endpoint.get().name();
            String failure = e.getClass().getSimpleName();
            log.info("[ProviderFieldAction] {} could not be asked about field '{}': {}", service, request.fieldName(), failure);
            return FieldActionResult.unavailable("the service did not answer: " + failure);
        }
    }

    /**
     * Whether the value is one this action judges at all; a value it does not is accepted without a call. A blank
     * value is not, whatever the action: the field's own validation says whether it is required.
     */
    protected boolean concerns(FieldActionRequest request) {
        return request.value() != null && !request.value().isBlank();
    }

    /**
     * Where the service is, from the module's own configuration ({@link FieldActionGateway.Endpoint#of}); empty when
     * the configuration describes none usable — the check is then unavailable, the configuration's own warning says
     * why.
     */
    protected abstract Optional<FieldActionGateway.Endpoint> endpoint();

    /**
     * The service's verdict on the value: the call through {@link #gateway()} and the reading of the answer. An
     * {@link IOException} is the service not answering, which the caller reads as unavailable.
     */
    protected abstract FieldActionResult ask(FieldActionGateway.Endpoint endpoint, FieldActionRequest request) throws IOException;

    /**
     * The endpoint a module's configuration describes, for {@link #endpoint()}: empty, with a log line naming the
     * service, when the configuration has no credential yet — the check is unavailable until an administrator sets
     * one — or describes no usable endpoint ({@link FieldActionGateway.Endpoint#of} says why, never the credential).
     * Called where the configuration is read ({@code @Activate}/{@code @Modified}), so each problem is logged once per
     * change, not per value.
     *
     * @param credentialName the header or query parameter the service reads its credential from — the service's own
     *                       contract, not a setting: {@code Auth-Token}, {@code api_key}
     */
    protected static Optional<FieldActionGateway.Endpoint> endpointOf(String name, String url, String credentialName,
                                                                     String credential, String credentialIn,
                                                                     boolean development) {
        if (credential == null || credential.isBlank()) {
            log.info("[ProviderFieldAction] No credential is configured for {}: its check is unavailable until one is set", name);
            return Optional.empty();
        }
        try {
            return Optional.of(FieldActionGateway.Endpoint.of(name, url, credentialName, credential, credentialIn, development));
        } catch (IllegalArgumentException e) {
            log.warn("[ProviderFieldAction] {}: its check is unavailable until the configuration is fixed", e.getMessage());
            return Optional.empty();
        }
    }

    /** The engine's gateway, held by the module as an OSGi reference. */
    protected abstract FieldActionGateway gateway();

    /** The body of an answer as one JSON object; empty when it is not one, which the caller reads as unavailable. */
    protected static Optional<JSONObject> json(FieldActionGateway.Response response) {
        if (response == null || response.body() == null || response.body().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new JSONObject(response.body()));
        } catch (JSONException e) {
            return Optional.empty();
        }
    }
}
