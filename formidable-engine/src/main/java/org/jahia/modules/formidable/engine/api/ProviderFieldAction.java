package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.Optional;

/**
 * The shape of a field action behind a provider — what every check calling an external service has in common, so
 * that a module writes the call and the reading of the answer, nothing else. The type takes
 * {@code fmdbmix:providerFieldAction} beside {@code fmdbmix:fieldAction}: the contributor picks one of the providers
 * the administrator declared under {@code fieldActionProviders}, and this class reads the id off the node
 * ({@link #providerIdOf}), hands it to {@link #ask} with the request, and turns every way the call can fail into an
 * unavailable check — a provider that cannot be reached or times out, an id the configuration no longer declares, a
 * node naming no provider. An unavailable check is what the contributor's {@code whenUnavailable} setting decides:
 * an outage never refuses on its own.
 *
 * <p>A value the action does not judge at all ({@link #concerns}) is accepted without a call: every answer of a
 * provider has a price. The gateway comes from the module, which holds the OSGi reference ({@link #gateway()}).</p>
 */
public abstract class ProviderFieldAction implements FieldAction {

    private static final Logger log = LoggerFactory.getLogger(ProviderFieldAction.class);

    @Override
    public final FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
        return judge(providerIdOf(actionNode), request);
    }

    /**
     * The verdict once the provider is known — the whole of the action but the reading of the node, which is what a
     * test drives; the id is null when the node names no provider.
     */
    public final FieldActionResult judge(String providerId, FieldActionRequest request) {
        if (!concerns(request)) {
            return FieldActionResult.accept();
        }
        if (providerId == null) {
            return FieldActionResult.unavailable("no provider is set on the action node");
        }
        try {
            return ask(providerId, request);
        } catch (IOException | IllegalArgumentException e) {
            // Unreachable, timed out, or an id the configuration no longer declares: not a verdict on the value.
            log.info("[ProviderFieldAction] Provider '{}' could not be asked about field '{}': {}", providerId, request.fieldName(), e.getClass().getSimpleName());
            return FieldActionResult.unavailable("the provider did not answer: " + e.getClass().getSimpleName());
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
     * The provider's verdict on the value: the call through {@link #gateway()} and the reading of the answer. An
     * {@link IOException} is the provider not answering, which the caller reads as unavailable.
     */
    protected abstract FieldActionResult ask(String providerId, FieldActionRequest request) throws IOException;

    /** The engine's gateway, held by the module as an OSGi reference. */
    protected abstract FieldActionGateway gateway();

    /** The provider the contributor picked on the node ({@code providerId}); null when the node lacks it or cannot be read. */
    public static String providerIdOf(JCRNodeWrapper actionNode) {
        try {
            if (actionNode == null || !actionNode.hasProperty(FmdbProperty.PROVIDER_ID)) {
                return null;
            }
            String providerId = actionNode.getProperty(FmdbProperty.PROVIDER_ID).getString().trim();
            return providerId.isEmpty() ? null : providerId;
        } catch (RepositoryException e) {
            return null;
        }
    }

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
