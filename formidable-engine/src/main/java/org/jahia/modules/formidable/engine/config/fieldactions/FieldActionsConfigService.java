package org.jahia.modules.formidable.engine.config.fieldactions;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The field actions theme read from {@code org.jahia.modules.formidable.fieldActions.cfg}: the HTTP client and
 * timeouts the gateway calls a field action's service with — the service itself is in the configuration of the
 * module that ships the action — and the guards of the pre-check endpoint: everything the field actions read, in
 * one piece.
 */
@Component(service = FieldActionsConfigService.class, configurationPid = FieldActionsConfigService.PID, immediate = true)
@Designate(ocd = FieldActionsConfig.class)
public class FieldActionsConfigService {

    public static final String PID = "org.jahia.modules.formidable.fieldActions";

    /**
     * Everything the field actions read from the configuration, in one piece: the HTTP client that reaches a field
     * action's service, and the guards of the pre-check endpoint (verdict cache, rate limit, value length, values per
     * field).
     */
    public record FieldActionSettings(
            boolean developmentEndpoints,
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

    /** The settings of earlier builds that held the providers, which the actions' own configurations replace. */
    static final List<String> FORMER_PROVIDER_SETTINGS = List.of("fieldActionProviders", "enableDevFieldActionProviders", "devFieldActionProviders");

    @Activate
    @Modified
    public void configure(FieldActionsConfig config, Map<String, Object> properties) {
        if (properties != null) {
            // Only a setting that held something: the file shipped with the lists held them empty, and false.
            List<String> former = FORMER_PROVIDER_SETTINGS.stream().filter(key -> meaningful(properties.get(key))).toList();
            if (!former.isEmpty()) {
                log.warn("[FieldActionsConfigService] {} no longer read: a field action calling a service reads it from the "
                        + "configuration of the module that ships the action", former);
            }
        }
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
        FieldActionSettings settings = new FieldActionSettings(
                config.enableDevFieldActionEndpoints(),
                connectTimeout,
                requestTimeout,
                ConfigurationValues.httpClient(connectTimeout),
                Duration.ofSeconds(Math.max(0L, config.fieldActionVerdictCacheTtlSeconds())),
                Math.max(0, config.fieldActionRateLimitPerMinute()),
                ConfigurationValues.positiveOrDefault(config.fieldActionMaxValueLength(), FieldActionsConfig.DEFAULT_FIELD_ACTION_MAX_VALUE_LENGTH),
                ConfigurationValues.positiveOrDefault(config.fieldActionMaxValuesPerField(), FieldActionsConfig.DEFAULT_FIELD_ACTION_MAX_VALUES_PER_FIELD)
        );
        log.info("FieldActionsConfigService configured: developmentEndpoints={}, connectTimeout={}s, requestTimeout={}s, verdictCacheTtl={}s, preCheckRateLimit={}/min, maxValueLength={}, maxValuesPerField={}",
                settings.developmentEndpoints(),
                connectTimeout.toSeconds(),
                requestTimeout.toSeconds(),
                settings.verdictCacheTtl().toSeconds(),
                settings.rateLimitPerMinute(),
                settings.maxValueLength(),
                settings.maxValuesPerField());
        return settings;
    }

    private static boolean meaningful(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return !text.isEmpty() && !"false".equalsIgnoreCase(text);
    }

    /** What the field actions read from the configuration — HTTP client, the pre-check endpoint's guards. */
    public FieldActionSettings getFieldActionSettings() {
        return lifecycle.current();
    }
}
