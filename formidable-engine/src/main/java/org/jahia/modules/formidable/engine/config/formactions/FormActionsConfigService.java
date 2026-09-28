package org.jahia.modules.formidable.engine.config.formactions;

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
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The form actions theme read from {@code org.jahia.modules.formidable.formActions.cfg}: the forward targets,
 * resolved by their stable id, and the HTTP client and timeouts a forward action calls them with.
 */
@Component(service = FormActionsConfigService.class, configurationPid = FormActionsConfigService.PID, immediate = true)
@Designate(ocd = FormActionsConfig.class)
public class FormActionsConfigService {

    public static final String PID = "org.jahia.modules.formidable.formActions";

    /**
     * A resolved forward target entry from the operator configuration.
     *
     * @param id          stable identifier stored in JCR (e.g. {@code salesforce-prod})
     * @param label       human-readable label shown in the CMS editor
     * @param uri         resolved target URI; guaranteed to use HTTPS for standard targets,
     *                    or HTTP on localhost / host.docker.internal for explicit dev targets
     * @param development whether this target comes from {@code devForwardTargets}
     */
    public record ForwardTarget(String id, String label, URI uri, boolean development) {}

    private record Snapshot(Map<String, ForwardTarget> targets, Duration connectTimeout, Duration requestTimeout, HttpClient httpClient) {}

    private static final Logger log = LoggerFactory.getLogger(FormActionsConfigService.class);

    private final ThemeLifecycle<Snapshot> lifecycle = new ThemeLifecycle<>(PID, FormActionsConfig.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
    }

    @Activate
    @Modified
    public void configure(FormActionsConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, read(config));
    }

    /** Reads the configuration into the snapshot the getters serve, no file behind it; public for the tests. */
    public void activate(FormActionsConfig config) {
        lifecycle.configure(null, read(config));
    }

    private static Snapshot read(FormActionsConfig config) {
        Duration connectTimeout = ConfigurationValues.timeoutSeconds("forwardHttpConnectTimeoutSeconds",
                config.forwardHttpConnectTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS);
        Duration requestTimeout = ConfigurationValues.timeoutSeconds("forwardHttpRequestTimeoutSeconds",
                config.forwardHttpRequestTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS);

        Map<String, ForwardTarget> targets = parseTargets(config.forwardTargets(), "forwardTargets", false);
        int developmentCount = 0;
        if (config.enableDevForwardTargets()) {
            // A development target never shadows a standard one: the first occurrence of an id wins, as within one list.
            Map<String, ForwardTarget> development = parseTargets(config.devForwardTargets(), "devForwardTargets", true);
            developmentCount = development.size();
            development.forEach((id, target) -> {
                if (targets.putIfAbsent(id, target) != null) {
                    log.warn("[FormActionsConfigService] Duplicate forward target id '{}' across forwardTargets and devForwardTargets, keeping the standard target.", id);
                }
            });
        } else if (config.devForwardTargets() != null && !config.devForwardTargets().isBlank()) {
            log.info("[FormActionsConfigService] Ignoring devForwardTargets because enableDevForwardTargets=false.");
        }

        Snapshot snapshot = new Snapshot(Collections.unmodifiableMap(targets), connectTimeout, requestTimeout,
                ConfigurationValues.httpClient(connectTimeout));
        log.info("FormActionsConfigService configured: forwardTargets={}, devForwardTargetsEnabled={}, devForwardTargets={}, connectTimeout={}s, requestTimeout={}s",
                targets.size(), config.enableDevForwardTargets(), developmentCount, connectTimeout.toSeconds(), requestTimeout.toSeconds());
        return snapshot;
    }

    /**
     * Parses a target registry: one {@code id|Label|url} per line, the URL under the endpoint rule (HTTPS, or
     * plain HTTP on localhost / host.docker.internal for the development list). A defective line is logged and
     * skipped; the first occurrence of a duplicate id wins.
     */
    private static Map<String, ForwardTarget> parseTargets(String raw, String propertyName, boolean development) {
        Map<String, ForwardTarget> result = new LinkedHashMap<>();
        for (String line : ConfigurationValues.lines(raw)) {
            parseTarget(line, propertyName, development).ifPresent(target -> {
                if (result.putIfAbsent(target.id(), target) != null) {
                    log.warn("[FormActionsConfigService] Duplicate {} id '{}', keeping first occurrence.", propertyName, target.id());
                }
            });
        }
        return result;
    }

    private static Optional<ForwardTarget> parseTarget(String line, String propertyName, boolean development) {
        String[] parts = line.split("\\|", 3);
        if (parts.length != 3) {
            log.warn("[FormActionsConfigService] Skipping malformed {} entry (expected id|label|url): '{}'", propertyName, line);
            return Optional.empty();
        }
        String id = parts[0].trim();
        String label = parts[1].trim();
        String url = parts[2].trim();
        if (id.isEmpty() || url.isEmpty()) {
            log.warn("[FormActionsConfigService] Skipping {} entry with empty id or url: '{}'", propertyName, line);
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            log.warn("[FormActionsConfigService] Skipping {} entry '{}': malformed URI '{}'", propertyName, id, url);
            return Optional.empty();
        }
        String reason = EndpointRule.unsupportedReason(uri, development);
        if (reason != null) {
            log.warn("[FormActionsConfigService] Skipping {} entry '{}': {}", propertyName, id, reason);
            return Optional.empty();
        }
        return Optional.of(new ForwardTarget(id, label, uri, development));
    }

    public Duration getForwardHttpConnectTimeout() { return lifecycle.current().connectTimeout(); }
    public Duration getForwardHttpRequestTimeout() { return lifecycle.current().requestTimeout(); }
    public HttpClient getForwardHttpClient() { return lifecycle.current().httpClient(); }

    /** Every configured forward target, in declaration order. */
    public Collection<ForwardTarget> getForwardTargets() {
        return lifecycle.current().targets().values();
    }

    /**
     * Resolves a forward target by its stable id.
     *
     * @param id the value stored in the JCR {@code targetId} property
     * @return the configured forward target, or empty if the id is unknown
     */
    public Optional<ForwardTarget> resolveForwardTarget(String id) {
        return Optional.ofNullable(lifecycle.current().targets().get(id));
    }
}
