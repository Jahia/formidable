package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.config.common.EndpointRule;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionSettings;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The {@link FieldActionGateway}: the endpoint an action hands over, reached with the configured HTTP client and
 * timeouts, the credential injected here and nowhere else. The endpoint's URL obeys the endpoint rule on every call;
 * the path a caller hands over is appended to its base URL and must stay under it — no scheme, no leading slash, no
 * {@code ..} segment, no control character — so that an action only ever talks to the service its configuration
 * names. The response body is capped at {@value #MAX_BODY_CHARS} characters: a service is asked a verdict, not handed
 * a channel.
 */
@Component(service = FieldActionGateway.class, immediate = true)
public class FieldActionGatewayImpl implements FieldActionGateway {

    static final int MAX_BODY_CHARS = 1_000_000;
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]");

    private static final Logger log = LoggerFactory.getLogger(FieldActionGatewayImpl.class);

    private Supplier<FieldActionSettings> settings;
    /**
     * The endpoint refusals already logged, by service and reason: said once, not once per value checked — and again
     * under new settings, which may have caused them anew ({@link #refusalsUnder}).
     */
    private final Set<String> refusalsLogged = ConcurrentHashMap.newKeySet();
    /** The settings in force when the refusals were logged: a new snapshot — a configuration change — clears them. */
    private final AtomicReference<FieldActionSettings> refusalsUnder = new AtomicReference<>();

    public FieldActionGatewayImpl() {
    }

    FieldActionGatewayImpl(Supplier<FieldActionSettings> settings) {
        this.settings = settings;
    }

    @Reference
    public void setConfigService(FieldActionsConfigService configService) {
        this.settings = configService::getFieldActionSettings;
    }

    @Override
    public Response post(Endpoint endpoint, String path, String jsonBody) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(target(checked(endpoint, settings.get()), path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody, StandardCharsets.UTF_8));
        return send(endpoint, request);
    }

    @Override
    public Response get(Endpoint endpoint, String path) throws IOException {
        return send(endpoint, HttpRequest.newBuilder(target(checked(endpoint, settings.get()), path)).GET());
    }

    /**
     * The endpoint, its URL under the endpoint rule — a record built without {@link Endpoint#of} is checked here —, a
     * development endpoint only while the administrator's switch allows them: an action's own file saying
     * {@code development=true} is not enough.
     */
    private Endpoint checked(Endpoint endpoint, FieldActionSettings current) {
        if (refusalsUnder.getAndSet(current) != current) {
            refusalsLogged.clear();
        }
        if (endpoint == null || endpoint.baseUri() == null) {
            throw new IllegalArgumentException("No endpoint is given");
        }
        if (endpoint.development() && (current == null || !current.developmentEndpoints())) {
            throw refused(endpoint.name() + " is a development endpoint, and they are switched off "
                    + "(enableDevFieldActionEndpoints in org.jahia.modules.formidable.fieldActions.cfg)");
        }
        String reason = EndpointRule.unsupportedReason(endpoint.baseUri(), endpoint.development());
        if (reason != null) {
            throw refused("The URL of " + endpoint.name() + " is refused: " + reason);
        }
        return endpoint;
    }

    /**
     * The refusal of an endpoint, logged once per configuration with its reason — the gateway's own words, no secret and no
     * value in them —: the caller only logs an exception's type, the right thing for one the JDK raised.
     */
    private IllegalArgumentException refused(String reason) {
        if (refusalsLogged.add(reason)) {
            log.warn("[FieldActionGateway] {}: the field action's check cannot run", reason);
        }
        return new IllegalArgumentException(reason);
    }

    /**
     * The endpoint's base URL with the relative path appended. The base always ends with a slash before resolving,
     * so a base of {@code https://api.example.com/v1} keeps its last segment; the result must stay on the base's
     * host, which a well-formed relative path cannot leave — checked all the same.
     */
    static URI target(Endpoint endpoint, String path) {
        if (path == null) {
            throw new IllegalArgumentException("The path is missing");
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.startsWith("//")
                || SCHEME.matcher(trimmed).matches() || CONTROL.matcher(trimmed).find()) {
            throw new IllegalArgumentException("The path must be relative to the endpoint's base URL: '" + trimmed + "'");
        }
        for (String segment : trimmed.split("[?#]", 2)[0].split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("The path must not climb out of the endpoint's base URL: '" + trimmed + "'");
            }
        }
        URI base = endpoint.baseUri();
        String prefix = base.getRawPath() == null || base.getRawPath().isEmpty() ? "/" : base.getRawPath();
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        URI resolved;
        try {
            resolved = new URI(base.getScheme(), base.getRawAuthority(), prefix, null, null).resolve(trimmed);
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IllegalArgumentException("The path does not form a valid URL under the endpoint's base: '" + trimmed + "'", e);
        }
        if (resolved.getHost() == null || !resolved.getHost().equalsIgnoreCase(base.getHost())
                || !resolved.getScheme().equalsIgnoreCase(base.getScheme())) {
            throw new IllegalArgumentException("The path leaves the endpoint's host: '" + trimmed + "'");
        }
        return endpoint.credentialInQuery() ? withCredentialParameter(resolved, endpoint) : resolved;
    }

    /**
     * The target with the credential as a query parameter, for a service that reads its key off the URL: appended
     * after the path's own query, before a fragment, both encoded. The URI holds a secret from here on and is never
     * logged; an exception raised before this point has none to leak.
     */
    private static URI withCredentialParameter(URI resolved, Endpoint endpoint) {
        String parameter = URLEncoder.encode(endpoint.credentialName(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(endpoint.credential(), StandardCharsets.UTF_8);
        String text = resolved.toString();
        int hash = text.indexOf('#');
        String head = hash < 0 ? text : text.substring(0, hash);
        String tail = hash < 0 ? "" : text.substring(hash);
        String joiner = resolved.getRawQuery() == null || resolved.getRawQuery().isEmpty() ? "?" : "&";
        return URI.create(head + joiner + parameter + tail);
    }

    private Response send(Endpoint endpoint, HttpRequest.Builder request) throws IOException {
        FieldActionSettings current = settings.get();
        request.timeout(current.httpRequestTimeout()).header("Accept", "application/json");
        if (!endpoint.credentialInQuery() && endpoint.credentialName() != null && !endpoint.credentialName().isEmpty()) {
            request.header(endpoint.credentialName(), endpoint.credential());
        }
        HttpClient client = current.httpClient();
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body() == null ? "" : response.body();
            if (body.length() > MAX_BODY_CHARS) {
                log.warn("[FieldActionGateway] {} answered {} characters; the body is cut at {}", endpoint.name(), body.length(), MAX_BODY_CHARS);
                body = body.substring(0, MAX_BODY_CHARS);
            }
            return new Response(response.statusCode(), body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling " + endpoint.name(), e);
        }
    }

}
