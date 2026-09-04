package org.jahia.modules.formidable.engine.config;

import org.json.JSONObject;
import org.osgi.service.cm.Configuration;
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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Reads Formidable global configuration from org.jahia.modules.formidable.cfg
 * and provides CAPTCHA verification and upload constraint access.
 */
@Component(
        service = FormidableConfigService.class,
        configurationPid = "org.jahia.modules.formidable",
        immediate = true
)
@Designate(ocd = FormidableConfig.class)
public class FormidableConfigService {
    public static class CaptchaVerificationException extends Exception {
        public CaptchaVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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

    /**
     * An admin-declared options source for choice fields: a curated Jahia choicelist
     * initializer exposed to contributors under a stable id.
     *
     * @param id             stored in JCR ({@code fmdb:optionsSourceKey})
     * @param label          shown in the source picker
     * @param initializerKey key of the Jahia choicelist initializer to evaluate
     * @param param          optional initializer parameter (empty when absent)
     */
    public record OptionsSource(String id, String label, String initializerKey, String param) {}

    private record ConfigSnapshot(
            String captchaSiteKey,
            String captchaSecretKey,
            String captchaScriptUrl,
            String captchaWidgetVar,
            String captchaTokenField,
            URI captchaVerifyUri,
            Duration captchaHttpConnectTimeout,
            Duration captchaHttpRequestTimeout,
            HttpClient captchaHttpClient,
            long uploadMaxFileSizeBytes,
            long uploadMaxRequestSizeBytes,
            int uploadMaxFileCount,
            Set<String> uploadAllowedMimeTypes,
            Duration forwardHttpConnectTimeout,
            Duration forwardHttpRequestTimeout,
            HttpClient forwardHttpClient,
            Map<String, ForwardTarget> forwardTargets,
            Map<String, OptionsSource> optionsSources,
            Duration optionsSourcesCacheTtl,
            int optionsQueryMaxResults
    ) {}

    private static final Logger log = LoggerFactory.getLogger(FormidableConfigService.class);

    static final String PID = "org.jahia.modules.formidable";

    private final AtomicReference<ConfigSnapshot> config = new AtomicReference<>();

    /** The raw configuration properties last received, to spot the deployed file taking over. */
    private final AtomicReference<Map<String, Object>> lastProperties = new AtomicReference<>();

    private volatile ConfigurationAdmin configurationAdmin;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        if (this.configurationAdmin == configurationAdmin) {
            this.configurationAdmin = null;
        }
    }

    @Activate
    @Modified
    public void configure(FormidableConfig osgiConfig, Map<String, Object> properties) {
        Map<String, Object> previous = lastProperties.getAndSet(new HashMap<>(properties));
        activate(osgiConfig);
        carryOverLegacySettings(previous, properties);
    }

    /**
     * Reads the configuration into the snapshot every getter serves. The lifecycle entry
     * point is {@link #configure}; this is public for the tests.
     */
    public void activate(FormidableConfig osgiConfig) {
        String captchaSiteKey = osgiConfig.captchaSiteKey();
        String captchaSecretKey = osgiConfig.captchaSecretKey();
        String captchaScriptUrl = osgiConfig.captchaScriptUrl();
        String captchaWidgetVar = osgiConfig.captchaWidgetVar();
        String captchaTokenField = osgiConfig.captchaTokenField();
        URI captchaVerifyUri = parseCaptchaVerifyUri(osgiConfig.captchaVerifyUrl());
        Duration captchaHttpConnectTimeout = readTimeoutSeconds(
                "captchaHttpConnectTimeoutSeconds",
                osgiConfig.captchaHttpConnectTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS
        );
        Duration captchaHttpRequestTimeout = readTimeoutSeconds(
                "captchaHttpRequestTimeoutSeconds",
                osgiConfig.captchaHttpRequestTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS
        );
        HttpClient captchaHttpClient = HttpClient.newBuilder()
                .connectTimeout(captchaHttpConnectTimeout)
                .build();

        // Same contract as the timeouts: a zero or negative limit is a configuration
        // mistake, not a way to disable the cap (-1 would also break the early
        // Content-Length guard, which compares against this value).
        long uploadMaxFileSizeBytes = readPositiveLong(
                "uploadMaxFileSizeBytes",
                osgiConfig.uploadMaxFileSizeBytes(),
                FormidableConfig.DEFAULT_UPLOAD_MAX_FILE_SIZE_BYTES
        );
        long uploadMaxRequestSizeBytes = readPositiveLong(
                "uploadMaxRequestSizeBytes",
                osgiConfig.uploadMaxRequestSizeBytes(),
                FormidableConfig.DEFAULT_UPLOAD_MAX_REQUEST_SIZE_BYTES
        );
        int uploadMaxFileCount = (int) readPositiveLong(
                "uploadMaxFileCount",
                osgiConfig.uploadMaxFileCount(),
                FormidableConfig.DEFAULT_UPLOAD_MAX_FILE_COUNT
        );
        Set<String> uploadAllowedMimeTypes = Set.copyOf(Arrays.stream(osgiConfig.uploadAllowedMimeTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet()));

        boolean enableDevForwardTargets = osgiConfig.enableDevForwardTargets();
        Duration forwardHttpConnectTimeout = readTimeoutSeconds(
                "forwardHttpConnectTimeoutSeconds",
                osgiConfig.forwardHttpConnectTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS
        );
        Duration forwardHttpRequestTimeout = readTimeoutSeconds(
                "forwardHttpRequestTimeoutSeconds",
                osgiConfig.forwardHttpRequestTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS
        );
        HttpClient forwardHttpClient = HttpClient.newBuilder()
                .connectTimeout(forwardHttpConnectTimeout)
                .build();

        Map<String, ForwardTarget> standardForwardTargets =
                parseForwardTargets(osgiConfig.forwardTargets(), "forwardTargets", false);
        Map<String, ForwardTarget> developmentForwardTargets = new LinkedHashMap<>();

        if (enableDevForwardTargets) {
            developmentForwardTargets =
                    parseForwardTargets(osgiConfig.devForwardTargets(), "devForwardTargets", true);
        } else if (osgiConfig.devForwardTargets() != null && !osgiConfig.devForwardTargets().isBlank()) {
            log.info("[FormidableConfigService] Ignoring devForwardTargets because enableDevForwardTargets=false.");
        }

        Map<String, ForwardTarget> forwardTargets = Collections.unmodifiableMap(new LinkedHashMap<>(
                mergeForwardTargets(standardForwardTargets, developmentForwardTargets)
        ));

        Map<String, OptionsSource> optionsSources =
                Collections.unmodifiableMap(parseOptionsSources(osgiConfig.optionsSources()));
        Duration optionsSourcesCacheTtl = readTimeoutSeconds(
                "optionsSourcesCacheTtlSeconds",
                osgiConfig.optionsSourcesCacheTtlSeconds(),
                FormidableConfig.DEFAULT_OPTIONS_SOURCES_CACHE_TTL_SECONDS
        );
        int optionsQueryMaxResults = osgiConfig.optionsQueryMaxResults() > 0
                ? osgiConfig.optionsQueryMaxResults()
                : FormidableConfig.DEFAULT_OPTIONS_QUERY_MAX_RESULTS;

        ConfigSnapshot snapshot = new ConfigSnapshot(
                captchaSiteKey,
                captchaSecretKey,
                captchaScriptUrl,
                captchaWidgetVar,
                captchaTokenField,
                captchaVerifyUri,
                captchaHttpConnectTimeout,
                captchaHttpRequestTimeout,
                captchaHttpClient,
                uploadMaxFileSizeBytes,
                uploadMaxRequestSizeBytes,
                uploadMaxFileCount,
                uploadAllowedMimeTypes,
                forwardHttpConnectTimeout,
                forwardHttpRequestTimeout,
                forwardHttpClient,
                forwardTargets,
                optionsSources,
                optionsSourcesCacheTtl,
                optionsQueryMaxResults
        );

        this.config.set(snapshot);

        log.info("FormidableConfigService configured: captchaVerification={}, captchaWidget={}, captchaConnectTimeout={}s, captchaRequestTimeout={}s, maxFileSize={}MB, maxRequest={}MB, allowedTypes={}, forwardTargets={}, devForwardTargetsEnabled={}, devForwardTargets={}, forwardConnectTimeout={}s, forwardRequestTimeout={}s",
                isCaptchaVerificationConfigured(snapshot) ? "[set]" : "[missing]",
                isCaptchaWidgetConfigured(snapshot) ? "[set]" : "[missing]",
                snapshot.captchaHttpConnectTimeout().toSeconds(),
                snapshot.captchaHttpRequestTimeout().toSeconds(),
                snapshot.uploadMaxFileSizeBytes() / 1_048_576,
                snapshot.uploadMaxRequestSizeBytes() / 1_048_576,
                snapshot.uploadAllowedMimeTypes().size(),
                snapshot.forwardTargets().size(),
                enableDevForwardTargets,
                developmentForwardTargets.size(),
                snapshot.forwardHttpConnectTimeout().toSeconds(),
                snapshot.forwardHttpRequestTimeout().toSeconds());
        log.info("FormidableConfigService options sources: {} declared, cacheTtl={}s",
                snapshot.optionsSources().size(),
                snapshot.optionsSourcesCacheTtl().toSeconds());
    }

    /**
     * Parses the {@code optionsSources} config value. Each non-blank line must have the
     * form {@code id|Label|initializerKey} or {@code id|Label|initializerKey|param}.
     * Invalid entries are logged and skipped; the first occurrence of a duplicate id wins.
     */
    private static Map<String, OptionsSource> parseOptionsSources(String raw) {
        Map<String, OptionsSource> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String entry : raw.split("[\n\r]+")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                parseOptionsSourceEntry(trimmed, result);
            }
        }
        return result;
    }

    /** One config line: validated, logged and skipped on any defect, kept otherwise. */
    private static void parseOptionsSourceEntry(String trimmed, Map<String, OptionsSource> result) {
        String[] parts = trimmed.split("\\|", 4);
        if (parts.length < 3) {
            log.warn("[FormidableConfigService] Skipping malformed optionsSources entry "
                    + "(expected id|Label|initializerKey[|param]): '{}'", trimmed);
            return;
        }
        String id = parts[0].trim();
        String label = parts[1].trim();
        String initializerKey = parts[2].trim();
        String param = parts.length == 4 ? parts[3].trim() : "";
        if (id.isEmpty() || label.isEmpty() || initializerKey.isEmpty()) {
            log.warn("[FormidableConfigService] Skipping optionsSources entry with a blank "
                    + "id, label or initializerKey: '{}'", trimmed);
            return;
        }
        if (result.containsKey(id)) {
            log.warn("[FormidableConfigService] Duplicate optionsSources id '{}', keeping first occurrence.", id);
            return;
        }
        result.put(id, new OptionsSource(id, label, initializerKey, param));
    }

    /**
     * Parses a forward target registry config value.
     * Each line has the form: {@code id|Label|url}
     * Invalid entries are logged and skipped.
     */
    private static Map<String, ForwardTarget> parseForwardTargets(
            String raw,
            String propertyName,
            boolean development
    ) {
        Map<String, ForwardTarget> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String[] entries = raw.split("[\n\r]+");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                Optional<ForwardTarget> parsedTarget = parseForwardTargetEntry(trimmed, propertyName, development);
                if (parsedTarget.isPresent()) {
                    ForwardTarget target = parsedTarget.get();
                    if (result.containsKey(target.id())) {
                        log.warn("[FormidableConfigService] Duplicate {} id '{}', keeping first occurrence.", propertyName, target.id());
                    } else {
                        result.put(target.id(), target);
                    }
                }
            }
        }
        return result;
    }

    private static Optional<ForwardTarget> parseForwardTargetEntry(
            String entry,
            String propertyName,
            boolean development
    ) {
        String[] parts = entry.split("\\|", 3);
        if (parts.length != 3) {
            log.warn("[FormidableConfigService] Skipping malformed {} entry (expected id|label|url): '{}'", propertyName, entry);
            return Optional.empty();
        }

        String id = parts[0].trim();
        String label = parts[1].trim();
        String url = parts[2].trim();
        if (id.isEmpty() || url.isEmpty()) {
            log.warn("[FormidableConfigService] Skipping {} entry with empty id or url: '{}'", propertyName, entry);
            return Optional.empty();
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            log.warn("[FormidableConfigService] Skipping {} entry '{}': malformed URI '{}'", propertyName, id, url);
            return Optional.empty();
        }

        String unsupportedReason = getUnsupportedForwardTargetUriReason(uri, development);
        if (unsupportedReason != null) {
            log.warn("[FormidableConfigService] Skipping {} entry '{}': {}",
                    propertyName,
                    id,
                    unsupportedReason);
            return Optional.empty();
        }

        return Optional.of(new ForwardTarget(id, label, uri, development));
    }

    private static URI parseCaptchaVerifyUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            log.warn("[FormidableConfigService] Invalid captchaVerifyUrl '{}': malformed URI.", rawUrl);
            return null;
        }

        if (uri.getUserInfo() != null) {
            log.warn("[FormidableConfigService] Invalid captchaVerifyUrl '{}': embedded credentials are not allowed.", rawUrl);
            return null;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            log.warn("[FormidableConfigService] Invalid captchaVerifyUrl '{}': HTTPS is required.", rawUrl);
            return null;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            log.warn("[FormidableConfigService] Invalid captchaVerifyUrl '{}': hostname is missing.", rawUrl);
            return null;
        }

        return uri;
    }

    private static Map<String, ForwardTarget> mergeForwardTargets(
            Map<String, ForwardTarget> standardForwardTargets,
            Map<String, ForwardTarget> developmentForwardTargets
    ) {
        Map<String, ForwardTarget> merged = new LinkedHashMap<>(standardForwardTargets);
        for (Map.Entry<String, ForwardTarget> entry : developmentForwardTargets.entrySet()) {
            if (merged.containsKey(entry.getKey())) {
                log.warn("[FormidableConfigService] Duplicate forward target id '{}' across forwardTargets and devForwardTargets, keeping the standard target.",
                        entry.getKey());
            } else {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private static String getUnsupportedForwardTargetUriReason(URI uri, boolean development) {
        if (uri.getUserInfo() != null) {
            return "URI must not include embedded user credentials.";
        }

        String scheme = uri.getScheme();
        if (!development) {
            return "https".equalsIgnoreCase(scheme) ? null : "URI must use HTTPS.";
        }

        if (!"http".equalsIgnoreCase(scheme)) {
            return "URI must use HTTP on localhost or host.docker.internal.";
        }

        return isAllowedDevelopmentEndpoint(uri)
                ? null
                : "URI must use HTTP on localhost or host.docker.internal.";
    }

    private static boolean isAllowedDevelopmentEndpoint(URI uri) {
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "host.docker.internal".equalsIgnoreCase(host);
    }

    // --- CAPTCHA ---

    public String getCaptchaSiteKey()    { return currentConfig().captchaSiteKey(); }
    public String getCaptchaScriptUrl()  { return currentConfig().captchaScriptUrl(); }
    public String getCaptchaWidgetVar()  { return currentConfig().captchaWidgetVar(); }
    public String getCaptchaTokenField() { return currentConfig().captchaTokenField(); }

    public boolean isCaptchaVerificationConfigured() {
        return isCaptchaVerificationConfigured(currentConfig());
    }

    public boolean isCaptchaWidgetConfigured() {
        return isCaptchaWidgetConfigured(currentConfig());
    }

    /**
     * Verifies the CAPTCHA token against the provider's server-side endpoint.
     *
     * @param token    the token submitted by the client widget
     * @param remoteIp the client's IP address (optional but recommended)
     * @return true if the provider confirms the token is valid
     * @throws CaptchaVerificationException when verification cannot complete because of an
     *                                      infrastructure or provider-side technical failure
     */
    public boolean verifyCaptcha(String token, String remoteIp) throws CaptchaVerificationException {
        ConfigSnapshot snapshot = currentConfig();
        if (!isCaptchaVerificationConfigured(snapshot)) {
            log.warn("CAPTCHA verification skipped: service is not configured.");
            return false;
        }
        if (token == null || token.isBlank()) {
            return false;
        }

        String body = "secret=" + encode(snapshot.captchaSecretKey())
                + "&response=" + encode(token)
                + (remoteIp != null ? "&remoteip=" + encode(remoteIp) : "");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(snapshot.captchaVerifyUri())
                    .timeout(snapshot.captchaHttpRequestTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = snapshot.captchaHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            JSONObject result = new JSONObject(responseBody);
            boolean success = result.optBoolean("success", false);
            if (!success && log.isDebugEnabled()) {
                log.debug("CAPTCHA verification failed. Provider response: {}", responseBody);
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CaptchaVerificationException(
                    "CAPTCHA verification request interrupted (verifyUrl=" + snapshot.captchaVerifyUri() + ").",
                    e
            );
        } catch (Exception e) {
            throw new CaptchaVerificationException(
                    "CAPTCHA verification request failed (verifyUrl=" + snapshot.captchaVerifyUri() + ").",
                    e
            );
        }
    }

    // --- UPLOAD ---

    public long getUploadMaxFileSizeBytes()    { return currentConfig().uploadMaxFileSizeBytes(); }
    public long getUploadMaxRequestSizeBytes() { return currentConfig().uploadMaxRequestSizeBytes(); }
    public int  getUploadMaxFileCount()        { return currentConfig().uploadMaxFileCount(); }
    public Set<String> getUploadAllowedMimeTypes() { return currentConfig().uploadAllowedMimeTypes(); }
    public Duration getCaptchaHttpConnectTimeout() { return currentConfig().captchaHttpConnectTimeout(); }
    public Duration getCaptchaHttpRequestTimeout() { return currentConfig().captchaHttpRequestTimeout(); }

    // --- FORWARD ACTION ---
    public Duration getForwardHttpConnectTimeout() { return currentConfig().forwardHttpConnectTimeout(); }
    public Duration getForwardHttpRequestTimeout() { return currentConfig().forwardHttpRequestTimeout(); }
    public HttpClient getForwardHttpClient() { return currentConfig().forwardHttpClient(); }

    /**
     * Returns all configured forward targets, in declaration order.
     */
    public Collection<ForwardTarget> getForwardTargets() {
        return currentConfig().forwardTargets().values();
    }

    public Collection<OptionsSource> getOptionsSources() {
        return currentConfig().optionsSources().values();
    }

    /**
     * Resolves an options source by its stable id.
     *
     * @param id the value stored in the JCR {@code fmdb:optionsSourceKey} property
     * @return the configured options source, or empty if the id is unknown
     */
    public Optional<OptionsSource> resolveOptionsSource(String id) {
        OptionsSource source = currentConfig().optionsSources().get(id);
        return source != null ? Optional.of(source) : Optional.empty();
    }

    /**
     * Maximum number of options a content-mode choice field may resolve; above it the
     * field fails explicitly like a failing source. Administrator-configurable.
     */
    public int getOptionsQueryMaxResults() {
        return currentConfig().optionsQueryMaxResults();
    }

    public Duration getOptionsSourcesCacheTtl() {
        return currentConfig().optionsSourcesCacheTtl();
    }

    /**
     * Resolves a forward target by its stable id.
     *
     * @param id the value stored in the JCR {@code targetId} property
     * @return the configured forward target, or empty if the id is unknown
     */
    public Optional<ForwardTarget> resolveForwardTarget(String id) {
        ForwardTarget target = currentConfig().forwardTargets().get(id);
        return target != null ? Optional.of(target) : Optional.empty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean isCaptchaVerificationConfigured(ConfigSnapshot snapshot) {
        return snapshot.captchaSiteKey() != null && !snapshot.captchaSiteKey().isBlank()
                && snapshot.captchaSecretKey() != null && !snapshot.captchaSecretKey().isBlank()
                && snapshot.captchaVerifyUri() != null;
    }

    private static boolean isCaptchaWidgetConfigured(ConfigSnapshot snapshot) {
        return snapshot.captchaSiteKey() != null && !snapshot.captchaSiteKey().isBlank()
                && snapshot.captchaScriptUrl() != null && !snapshot.captchaScriptUrl().isBlank()
                && snapshot.captchaWidgetVar() != null && !snapshot.captchaWidgetVar().isBlank()
                && snapshot.captchaTokenField() != null && !snapshot.captchaTokenField().isBlank();
    }

    /**
     * Writes back the settings the deployed configuration file replaced on an installation
     * configured without it (see {@link LegacyConfigurationCarryOver}). fileinstall then
     * persists the update into the file, and the next {@link #configure} sees a configuration
     * that already comes from the file: nothing to carry over any more. Limit: fileinstall
     * loads the file one to two seconds after Jahia copies it at bundle resolution, so the
     * previous settings are only seen when this component activated before that — which is
     * the case when the module is installed or upgraded on a running server.
     */
    private void carryOverLegacySettings(Map<String, Object> previous, Map<String, Object> next) {
        Map<String, Object> carried = LegacyConfigurationCarryOver.settingsToCarryOver(previous, next);
        if (carried.isEmpty()) {
            return;
        }
        ConfigurationAdmin admin = configurationAdmin;
        if (admin == null) {
            log.warn("[FormidableConfigService] The deployed configuration file replaced settings made without it, "
                    + "and ConfigurationAdmin is not available to write them back: {}", carried);
            return;
        }
        try {
            Configuration configuration = admin.getConfiguration(PID, "?");
            Dictionary<String, Object> updated = new Hashtable<>();
            Dictionary<String, Object> current = configuration.getProperties();
            if (current != null) {
                for (Enumeration<String> keys = current.keys(); keys.hasMoreElements(); ) {
                    String key = keys.nextElement();
                    updated.put(key, current.get(key));
                }
            }
            carried.forEach(updated::put);
            configuration.update(updated);
            log.warn("[FormidableConfigService] The deployed configuration file replaced settings made without it; "
                    + "carried over into the file: {}", carried.keySet());
        } catch (IOException e) {
            log.error("[FormidableConfigService] Could not carry the previous settings over into the configuration file: {}", carried, e);
        }
    }

    private ConfigSnapshot currentConfig() {
        ConfigSnapshot snapshot = config.get();
        if (snapshot == null) {
            throw new IllegalStateException("Formidable configuration is not initialized.");
        }
        return snapshot;
    }

    private static long readPositiveLong(String propertyName, long value, long defaultValue) {
        if (value <= 0) {
            log.warn("[FormidableConfigService] Invalid {}={}, falling back to {}.",
                    propertyName, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private static Duration readTimeoutSeconds(String propertyName, long seconds, long defaultSeconds) {
        if (seconds <= 0) {
            log.warn("[FormidableConfigService] Invalid {}={}s, falling back to {}s.",
                    propertyName, seconds, defaultSeconds);
            return Duration.ofSeconds(defaultSeconds);
        }
        return Duration.ofSeconds(seconds);
    }
}
