package org.jahia.modules.formidable.engine.config;

import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private record ConfigSnapshot(
            String captchaSiteKey,
            String captchaSecretKey,
            String captchaScriptUrl,
            String captchaWidgetVar,
            String captchaTokenField,
            String captchaVerifyUrl,
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
            Map<String, ForwardTarget> forwardTargets
    ) {}

    private static final Logger log = LoggerFactory.getLogger(FormidableConfigService.class);

    private final AtomicReference<ConfigSnapshot> config = new AtomicReference<>();

    @Activate
    @Modified
    public void activate(FormidableConfig osgiConfig) {
        String captchaSiteKey = osgiConfig.captchaSiteKey();
        String captchaSecretKey = osgiConfig.captchaSecretKey();
        String captchaScriptUrl = osgiConfig.captchaScriptUrl();
        String captchaWidgetVar = osgiConfig.captchaWidgetVar();
        String captchaTokenField = osgiConfig.captchaTokenField();
        String captchaVerifyUrl = osgiConfig.captchaVerifyUrl();
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

        long uploadMaxFileSizeBytes = osgiConfig.uploadMaxFileSizeBytes();
        long uploadMaxRequestSizeBytes = osgiConfig.uploadMaxRequestSizeBytes();
        int uploadMaxFileCount = osgiConfig.uploadMaxFileCount();
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

        ConfigSnapshot snapshot = new ConfigSnapshot(
                captchaSiteKey,
                captchaSecretKey,
                captchaScriptUrl,
                captchaWidgetVar,
                captchaTokenField,
                captchaVerifyUrl,
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
                forwardTargets
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
                    .uri(URI.create(snapshot.captchaVerifyUrl()))
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
                    "CAPTCHA verification request interrupted (verifyUrl=" + snapshot.captchaVerifyUrl() + ").",
                    e
            );
        } catch (Exception e) {
            throw new CaptchaVerificationException(
                    "CAPTCHA verification request failed (verifyUrl=" + snapshot.captchaVerifyUrl() + ").",
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
                && snapshot.captchaVerifyUrl() != null && !snapshot.captchaVerifyUrl().isBlank();
    }

    private static boolean isCaptchaWidgetConfigured(ConfigSnapshot snapshot) {
        return snapshot.captchaSiteKey() != null && !snapshot.captchaSiteKey().isBlank()
                && snapshot.captchaScriptUrl() != null && !snapshot.captchaScriptUrl().isBlank()
                && snapshot.captchaWidgetVar() != null && !snapshot.captchaWidgetVar().isBlank()
                && snapshot.captchaTokenField() != null && !snapshot.captchaTokenField().isBlank();
    }

    private ConfigSnapshot currentConfig() {
        ConfigSnapshot snapshot = config.get();
        if (snapshot == null) {
            throw new IllegalStateException("Formidable configuration is not initialized.");
        }
        return snapshot;
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
