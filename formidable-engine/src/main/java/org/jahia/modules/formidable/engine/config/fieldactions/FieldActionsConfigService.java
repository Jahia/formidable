package org.jahia.modules.formidable.engine.config.fieldactions;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.jahia.modules.formidable.engine.config.common.EndpointRule;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The field actions theme read from {@code org.jahia.modules.formidable.fieldActions.cfg}: the providers a
 * field action may call, resolved by their stable id, the HTTP client and timeouts the gateway calls them
 * with, and the guards of the pre-check endpoint — everything the field actions read, in one piece.
 */
@Component(service = FieldActionsConfigService.class, configurationPid = FieldActionsConfigService.PID, immediate = true)
@Designate(ocd = FieldActionsConfig.class)
public class FieldActionsConfigService {

    public static final String PID = "org.jahia.modules.formidable.fieldActions";

    /** Where a provider's credential goes, as the sixth part of its line says: a request header (the default) or a query parameter. */
    private static final String CREDENTIAL_IN_HEADER = "header";
    private static final String CREDENTIAL_IN_QUERY = "query";

    /**
     * An external service a field action may call through the {@code FieldActionGateway}, from
     * {@code fieldActionProviders=id|Label|https://base-url|Credential-name|credential[|header|query]}.
     *
     * @param id                stored in JCR on the field-action node
     * @param label             shown in the provider picker of the editor
     * @param baseUri           the HTTPS base the gateway appends a relative path to
     * @param credentialHeader  the header, or the query parameter, carrying the credential; empty when the provider needs none
     * @param credential        the secret; never logged, never returned to a caller — {@link #toString()} hides it
     * @param credentialInQuery whether the credential travels as a query parameter rather than a request header
     */
    public record FieldActionProvider(String id, String label, URI baseUri, String credentialHeader, String credential, boolean credentialInQuery) {
        /** A provider whose credential travels as a request header, the default. */
        public FieldActionProvider(String id, String label, URI baseUri, String credentialHeader, String credential) {
            this(id, label, baseUri, credentialHeader, credential, false);
        }

        @Override
        public String toString() {
            return "FieldActionProvider[id=" + id + ", baseUri=" + baseUri + ", credentialHeader=" + credentialHeader
                    + ", credentialIn=" + (credentialInQuery ? CREDENTIAL_IN_QUERY : CREDENTIAL_IN_HEADER)
                    + ", credential=" + (credential == null || credential.isEmpty() ? "none" : "***") + "]";
        }
    }

    /**
     * Everything the field actions read from the configuration, in one piece: the providers and the HTTP client that
     * reaches them, and the guards of the pre-check endpoint (verdict cache, rate limit, value length, values per field).
     */
    public record FieldActionSettings(
            Map<String, FieldActionProvider> providers,
            Duration httpConnectTimeout,
            Duration httpRequestTimeout,
            HttpClient httpClient,
            Duration verdictCacheTtl,
            int rateLimitPerMinute,
            int maxValueLength,
            int maxValuesPerField
    ) {}

    private static final Logger log = LoggerFactory.getLogger(FieldActionsConfigService.class);

    private final ThemeLifecycle<FieldActionsConfig, FieldActionSettings> lifecycle = new ThemeLifecycle<>(PID, FieldActionsConfig.class, FieldActionsConfigService::read);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
    }

    @Activate
    @Modified
    public void configure(FieldActionsConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, config);
    }

    /** Reads the configuration into the settings the getters serve, no file behind it; public for the tests. */
    public void activate(FieldActionsConfig config) {
        lifecycle.configure(null, config);
    }

    /**
     * Everything the field actions read, guarded: a non-positive TTL disables the verdict cache, a non-positive
     * rate limit disables the pre-check endpoint, a non-positive length or count falls back to the default.
     */
    private static FieldActionSettings read(FieldActionsConfig config) {
        Duration connectTimeout = ConfigurationValues.timeoutSeconds("fieldActionHttpConnectTimeoutSeconds",
                config.fieldActionHttpConnectTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS);
        Duration requestTimeout = ConfigurationValues.timeoutSeconds("fieldActionHttpRequestTimeoutSeconds",
                config.fieldActionHttpRequestTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS);
        Map<String, FieldActionProvider> providers = parseProviders(config.fieldActionProviders(), false);
        if (config.enableDevFieldActionProviders()) {
            // A development provider never shadows a standard one: the first occurrence of an id wins, as within one list.
            parseProviders(config.devFieldActionProviders(), true).forEach((id, provider) -> {
                if (providers.putIfAbsent(id, provider) != null) {
                    log.warn("[FieldActionsConfigService] Duplicate field action provider id '{}' across fieldActionProviders and devFieldActionProviders, keeping the standard provider.", id);
                }
            });
        } else if (config.devFieldActionProviders() != null && !config.devFieldActionProviders().isBlank()) {
            log.info("[FieldActionsConfigService] Ignoring devFieldActionProviders because enableDevFieldActionProviders=false.");
        }
        FieldActionSettings settings = new FieldActionSettings(
                Collections.unmodifiableMap(providers),
                connectTimeout,
                requestTimeout,
                ConfigurationValues.httpClient(connectTimeout),
                Duration.ofSeconds(Math.max(0L, config.fieldActionVerdictCacheTtlSeconds())),
                Math.max(0, config.fieldActionRateLimitPerMinute()),
                ConfigurationValues.positiveOrDefault(config.fieldActionMaxValueLength(), FieldActionsConfig.DEFAULT_FIELD_ACTION_MAX_VALUE_LENGTH),
                ConfigurationValues.positiveOrDefault(config.fieldActionMaxValuesPerField(), FieldActionsConfig.DEFAULT_FIELD_ACTION_MAX_VALUES_PER_FIELD)
        );
        log.info("FieldActionsConfigService configured: {} provider(s), devProvidersEnabled={}, connectTimeout={}s, requestTimeout={}s, verdictCacheTtl={}s, preCheckRateLimit={}/min, maxValueLength={}, maxValuesPerField={}",
                settings.providers().size(),
                config.enableDevFieldActionProviders(),
                connectTimeout.toSeconds(),
                requestTimeout.toSeconds(),
                settings.verdictCacheTtl().toSeconds(),
                settings.rateLimitPerMinute(),
                settings.maxValueLength(),
                settings.maxValuesPerField());
        return settings;
    }

    /**
     * Parses a provider list: one {@code id|Label|https://base-url|Credential-name|credential} per line, the last
     * two optional together, with an optional sixth part saying where the credential goes — {@code header}, the
     * default, or {@code query}, a parameter of that name, for a provider that reads its key off the URL. The base
     * URL obeys the endpoint rule — HTTPS, a host, no embedded credentials; a development list obeys the
     * development rule instead, plain HTTP on localhost or host.docker.internal. A malformed entry is logged
     * without its credential and skipped; the first occurrence of a duplicate id wins.
     */
    private static Map<String, FieldActionProvider> parseProviders(String raw, boolean development) {
        Map<String, FieldActionProvider> result = new LinkedHashMap<>();
        for (String line : ConfigurationValues.lines(raw)) {
            parseProvider(line, development).ifPresent(provider -> {
                if (result.putIfAbsent(provider.id(), provider) != null) {
                    log.warn("[FieldActionsConfigService] Duplicate fieldActionProviders id '{}', keeping first occurrence.", provider.id());
                }
            });
        }
        return result;
    }

    private static Optional<FieldActionProvider> parseProvider(String line, boolean development) {
        String[] parts = line.split("\\|", 6);
        if (parts.length < 3) {
            if (log.isWarnEnabled()) {
                log.warn("[FieldActionsConfigService] Skipping malformed fieldActionProviders entry (expected id|Label|https://base-url|Header|credential[|header|query]): '{}'",
                        redacted(parts));
            }
            return Optional.empty();
        }
        String id = parts[0].trim();
        String label = parts[1].trim();
        String url = parts[2].trim();
        String header = parts.length > 3 ? parts[3].trim() : "";
        String credential = parts.length > 4 ? parts[4].trim() : "";
        if (id.isEmpty() || url.isEmpty()) {
            if (log.isWarnEnabled()) {
                log.warn("[FieldActionsConfigService] Skipping fieldActionProviders entry with an empty id or base URL: '{}'", redacted(parts));
            }
            return Optional.empty();
        }
        if (header.isEmpty() != credential.isEmpty()) {
            log.warn("[FieldActionsConfigService] Skipping fieldActionProviders entry '{}': a credential needs its header name, and a header name its credential.", id);
            return Optional.empty();
        }
        Optional<Boolean> credentialInQuery = credentialPlacement(id, parts.length > 5 ? parts[5] : CREDENTIAL_IN_HEADER, credential);
        Optional<URI> baseUri = baseUri(id, url, development);
        if (credentialInQuery.isEmpty() || baseUri.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FieldActionProvider(id, label.isEmpty() ? id : label, baseUri.get(), header, credential, credentialInQuery.get()));
    }

    /**
     * Where the credential goes, as the sixth part says — the header by default, or the query; empty, with a
     * warning, when the part says something else or names a query credential without a value to send.
     */
    private static Optional<Boolean> credentialPlacement(String id, String placement, String credential) {
        String where = placement.trim().toLowerCase(Locale.ROOT);
        if (!CREDENTIAL_IN_HEADER.equals(where) && !CREDENTIAL_IN_QUERY.equals(where)) {
            // The part is not echoed: past the fifth '|' it may be the tail of a credential that carries one.
            log.warn("[FieldActionsConfigService] Skipping fieldActionProviders entry '{}': the sixth part must be '{}' or '{}'.",
                    id, CREDENTIAL_IN_HEADER, CREDENTIAL_IN_QUERY);
            return Optional.empty();
        }
        if (CREDENTIAL_IN_QUERY.equals(where) && credential.isEmpty()) {
            log.warn("[FieldActionsConfigService] Skipping fieldActionProviders entry '{}': a credential in the query needs its parameter name and its value.", id);
            return Optional.empty();
        }
        return Optional.of(CREDENTIAL_IN_QUERY.equals(where));
    }

    /** The base URI of a provider line under the endpoint rule; empty, with a warning, when malformed or refused. */
    private static Optional<URI> baseUri(String id, String url, boolean development) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            log.warn("[FieldActionsConfigService] Skipping fieldActionProviders entry '{}': malformed URI '{}'", id, url);
            return Optional.empty();
        }
        String reason = EndpointRule.unsupportedReason(uri, development);
        if (reason != null) {
            log.warn("[FieldActionsConfigService] Skipping {} entry '{}': {}", development ? "devFieldActionProviders" : "fieldActionProviders", id, reason);
            return Optional.empty();
        }
        return Optional.of(uri);
    }

    /** An entry as a log line may show it: its first three parts, never a credential. */
    private static String redacted(String[] parts) {
        return String.join("|", Arrays.copyOf(parts, Math.min(parts.length, 3)));
    }

    /** What the field actions read from the configuration — providers, HTTP client, the pre-check endpoint's guards. */
    public FieldActionSettings getFieldActionSettings() {
        return lifecycle.current();
    }

    /** The configured provider of that id, for the gateway and the editor's provider list. */
    public Optional<FieldActionProvider> resolveFieldActionProvider(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(lifecycle.current().providers().get(id));
    }
}
