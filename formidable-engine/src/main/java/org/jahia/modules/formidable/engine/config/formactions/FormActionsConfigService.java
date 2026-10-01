package org.jahia.modules.formidable.engine.config.formactions;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.jahia.modules.formidable.engine.config.common.FactoryEntries;
import org.jahia.services.modulemanager.spi.ConfigService;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The form actions configuration read from {@code org.jahia.modules.formidable.formActions.cfg} — the switch of the
 * development targets and the timeouts of the call — together with the forward targets, one file each
 * ({@link ForwardTargetComponent}), resolved by their id.
 * <p>
 * The lists {@code forwardTargets} and {@code devForwardTargets} of earlier builds are turned into one target file
 * each, once, then removed from the configuration's file ({@link FactoryEntries}).
 */
@Component(service = FormActionsConfigService.class, configurationPid = FormActionsConfigService.PID, immediate = true)
@Designate(ocd = FormActionsConfig.class)
public class FormActionsConfigService {

    public static final String PID = "org.jahia.modules.formidable.formActions";

    static final String STANDARD_LINES = "forwardTargets";
    static final String DEVELOPMENT_LINES = "devForwardTargets";

    /**
     * A forward target from its file.
     *
     * @param id          stable identifier stored in JCR (e.g. {@code salesforce-prod})
     * @param label       human-readable label shown in the CMS editor
     * @param uri         resolved target URI; guaranteed to use HTTPS for standard targets,
     *                    or HTTP on localhost / host.docker.internal for explicit dev targets
     * @param development whether the file declares a development target
     */
    public record ForwardTarget(String id, String label, URI uri, boolean development) {}

    /** The configuration's own file: everything but the targets. */
    private record Theme(boolean developmentEnabled, Duration connectTimeout, Duration requestTimeout, HttpClient httpClient) {}

    private static final Logger log = LoggerFactory.getLogger(FormActionsConfigService.class);

    private final ThemeLifecycle<FormActionsConfig, Theme> lifecycle = new ThemeLifecycle<>(PID, FormActionsConfig.class, FormActionsConfigService::read);
    /** The target files: bound, adopted from the console, converted from the lines of earlier builds. */
    private final FactoryEntries<ForwardTargetComponent> targetFiles = new FactoryEntries<>(PID,
            ForwardTargetComponent.FACTORY_PID, "forward target", ForwardTargetComponent.SETTINGS, this::merge);
    /**
     * The targets by id and the theme they were merged with, recomputed whenever the configuration or a file changes
     * — and on read when the theme moved on without a callback (a migration attempt that gave up).
     */
    private final AtomicReference<Built> targets = new AtomicReference<>();

    private record Built(Theme theme, Map<String, ForwardTarget> byId) {}

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
        targetFiles.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
        targetFiles.unsetConfigurationAdmin(admin);
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigService")
    public void setConfigService(ConfigService service) {
        targetFiles.setConfigService(service);
    }

    public void unsetConfigService(ConfigService service) {
        targetFiles.unsetConfigService(service);
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY, unbind = "unbindTarget", updated = "updatedTarget")
    public void bindTarget(ForwardTargetComponent target) {
        targetFiles.bind(target);
    }

    public void updatedTarget(ForwardTargetComponent target) {
        targetFiles.updated(target);
    }

    public void unbindTarget(ForwardTargetComponent target) {
        targetFiles.unbind(target);
    }

    @Activate
    @Modified
    public void configure(FormActionsConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, config);
        merge();
        targetFiles.themeConfigured(properties, List.of(STANDARD_LINES, DEVELOPMENT_LINES),
                texts -> entries(texts.getOrDefault(STANDARD_LINES, ""), texts.getOrDefault(DEVELOPMENT_LINES, "")));
    }

    /** Reads the configuration into the snapshot the getters serve, no file behind it; public for the tests. */
    public void activate(FormActionsConfig config) {
        lifecycle.configure(null, config);
        merge();
    }

    /** The tests' seam: adoptions run in the calling thread. */
    void useForTests(Executor executor) {
        targetFiles.useForTests(executor);
    }

    private static Theme read(FormActionsConfig config) {
        Duration connectTimeout = ConfigurationValues.timeoutSeconds("forwardHttpConnectTimeoutSeconds",
                config.forwardHttpConnectTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS);
        Duration requestTimeout = ConfigurationValues.timeoutSeconds("forwardHttpRequestTimeoutSeconds",
                config.forwardHttpRequestTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS);
        return new Theme(config.enableDevForwardTargets(), connectTimeout, requestTimeout, ConfigurationValues.httpClient(connectTimeout));
    }

    private synchronized void merge() {
        Theme theme;
        try {
            theme = lifecycle.current();
        } catch (IllegalStateException notYet) {
            return;
        }
        FactoryEntries.Merged<ForwardTarget> files = targetFiles.merged(theme.developmentEnabled(),
                ForwardTargetComponent::target, ForwardTargetComponent::development, ForwardTarget::id);
        targets.set(new Built(theme, files.byId()));
        log.info("FormActionsConfigService configured: {} forward target(s) {}, devForwardTargetsEnabled={}{}, connectTimeout={}s, requestTimeout={}s",
                files.byId().size(), files.byId().keySet(), theme.developmentEnabled(),
                files.ignoredDevelopment() > 0 ? " (" + files.ignoredDevelopment() + " development target(s) ignored)" : "",
                theme.connectTimeout().toSeconds(), theme.requestTimeout().toSeconds());
    }

    /** The target files the lines of the former lists describe, {@code id|Label|url} each, the development list's marked. */
    static List<Map<String, String>> entries(String standardLines, String developmentLines) {
        List<Map<String, String>> entries = new ArrayList<>();
        for (boolean development : new boolean[] {false, true}) {
            for (String line : ConfigurationValues.lines(development ? developmentLines : standardLines)) {
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3 || parts[0].isBlank()) {
                    log.warn("[FormActionsConfigService] Skipping malformed forward target line (expected id|label|url): '{}'", line);
                    continue;
                }
                Map<String, String> settings = new LinkedHashMap<>();
                settings.put("id", parts[0].trim());
                settings.put("label", parts[1].trim());
                settings.put("url", parts[2].trim());
                settings.put("development", String.valueOf(development));
                entries.add(settings);
            }
        }
        return entries;
    }

    public Duration getForwardHttpConnectTimeout() { return lifecycle.current().connectTimeout(); }
    public Duration getForwardHttpRequestTimeout() { return lifecycle.current().requestTimeout(); }
    public HttpClient getForwardHttpClient() { return lifecycle.current().httpClient(); }

    /** Every configured forward target, by id. */
    public Collection<ForwardTarget> getForwardTargets() {
        return current().values();
    }

    /**
     * Resolves a forward target by its stable id.
     *
     * @param id the value stored in the JCR {@code targetId} property
     * @return the configured forward target, or empty if the id is unknown
     */
    public Optional<ForwardTarget> resolveForwardTarget(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(current().get(id));
    }

    private Map<String, ForwardTarget> current() {
        Theme theme = lifecycle.current();
        Built current = targets.get();
        if (current == null || current.theme() != theme) {
            merge();
            current = targets.get();
        }
        return current.byId();
    }
}
