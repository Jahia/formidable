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

    private static final Logger log = LoggerFactory.getLogger(FormidableConfigService.class);

    private String captchaSiteKey;
    private String captchaSecretKey;
    private String captchaScriptUrl;
    private String captchaWidgetVar;
    private String captchaTokenField;
    private String captchaVerifyUrl;
    private Duration captchaHttpConnectTimeout;
    private Duration captchaHttpRequestTimeout;

    private long uploadMaxFileSizeBytes;
    private long uploadMaxRequestSizeBytes;
    private int  uploadMaxFileCount;
    private Set<String> uploadAllowedMimeTypes;
    private Duration forwardHttpConnectTimeout;
    private Duration forwardHttpRequestTimeout;

    /** Keyed by target id. Insertion-ordered so the choice list is stable. */
    private Map<String, ForwardTarget> forwardTargets = new LinkedHashMap<>();

    private final AtomicReference<HttpClient> captchaHttpClient = new AtomicReference<>();
    private final AtomicReference<HttpClient> forwardHttpClient = new AtomicReference<>();

    @Activate
    @Modified
    public void activate(FormidableConfig config) {
        captchaSiteKey    = config.captchaSiteKey();
        captchaSecretKey  = config.captchaSecretKey();
        captchaScriptUrl  = config.captchaScriptUrl();
        captchaWidgetVar  = config.captchaWidgetVar();
        captchaTokenField = config.captchaTokenField();
        captchaVerifyUrl  = config.captchaVerifyUrl();
        captchaHttpConnectTimeout = readTimeoutSeconds(
                "captchaHttpConnectTimeoutSeconds",
                config.captchaHttpConnectTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS
        );
        captchaHttpRequestTimeout = readTimeoutSeconds(
                "captchaHttpRequestTimeoutSeconds",
                config.captchaHttpRequestTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS
        );
        captchaHttpClient.set(HttpClient.newBuilder()
                .connectTimeout(captchaHttpConnectTimeout)
                .build());

        uploadMaxFileSizeBytes    = config.uploadMaxFileSizeBytes();
        uploadMaxRequestSizeBytes = config.uploadMaxRequestSizeBytes();
        uploadMaxFileCount        = config.uploadMaxFileCount();
        uploadAllowedMimeTypes    = Arrays.stream(config.uploadAllowedMimeTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        boolean enableDevForwardTargets = config.enableDevForwardTargets();
        forwardHttpConnectTimeout = readTimeoutSeconds(
                "forwardHttpConnectTimeoutSeconds",
                config.forwardHttpConnectTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS
        );
        forwardHttpRequestTimeout = readTimeoutSeconds(
                "forwardHttpRequestTimeoutSeconds",
                config.forwardHttpRequestTimeoutSeconds(),
                FormidableConfig.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS
        );
        forwardHttpClient.set(HttpClient.newBuilder()
                .connectTimeout(forwardHttpConnectTimeout)
                .build());

        Map<String, ForwardTarget> standardForwardTargets =
                parseForwardTargets(config.forwardTargets(), "forwardTargets", false);
        Map<String, ForwardTarget> developmentForwardTargets = new LinkedHashMap<>();

        if (enableDevForwardTargets) {
            developmentForwardTargets =
                    parseForwardTargets(config.devForwardTargets(), "devForwardTargets", true);
        } else if (config.devForwardTargets() != null && !config.devForwardTargets().isBlank()) {
            log.info("[FormidableConfigService] Ignoring devForwardTargets because enableDevForwardTargets=false.");
        }

        forwardTargets = mergeForwardTargets(standardForwardTargets, developmentForwardTargets);

        log.info("FormidableConfigService configured: captchaVerification={}, captchaWidget={}, captchaConnectTimeout={}s, captchaRequestTimeout={}s, maxFileSize={}MB, maxRequest={}MB, allowedTypes={}, forwardTargets={}, devForwardTargetsEnabled={}, devForwardTargets={}, forwardConnectTimeout={}s, forwardRequestTimeout={}s",
                isCaptchaVerificationConfigured() ? "[set]" : "[missing]",
                isCaptchaWidgetConfigured() ? "[set]" : "[missing]",
                captchaHttpConnectTimeout.toSeconds(),
                captchaHttpRequestTimeout.toSeconds(),
                uploadMaxFileSizeBytes / 1_048_576,
                uploadMaxRequestSizeBytes / 1_048_576,
                uploadAllowedMimeTypes.size(),
                forwardTargets.size(),
                enableDevForwardTargets,
                developmentForwardTargets.size(),
                forwardHttpConnectTimeout.toSeconds(),
                forwardHttpRequestTimeout.toSeconds());
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

    public String getCaptchaSiteKey()    { return captchaSiteKey; }
    public String getCaptchaScriptUrl()  { return captchaScriptUrl; }
    public String getCaptchaWidgetVar()  { return captchaWidgetVar; }
    public String getCaptchaTokenField() { return captchaTokenField; }

    public boolean isCaptchaVerificationConfigured() {
        return captchaSiteKey != null && !captchaSiteKey.isBlank()
                && captchaSecretKey != null && !captchaSecretKey.isBlank()
                && captchaVerifyUrl != null && !captchaVerifyUrl.isBlank();
    }

    public boolean isCaptchaWidgetConfigured() {
        return captchaSiteKey != null && !captchaSiteKey.isBlank()
                && captchaScriptUrl != null && !captchaScriptUrl.isBlank()
                && captchaWidgetVar != null && !captchaWidgetVar.isBlank()
                && captchaTokenField != null && !captchaTokenField.isBlank();
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
        if (!isCaptchaVerificationConfigured()) {
            log.warn("CAPTCHA verification skipped: service is not configured.");
            return false;
        }
        if (token == null || token.isBlank()) {
            return false;
        }

        String body = "secret=" + encode(captchaSecretKey)
                + "&response=" + encode(token)
                + (remoteIp != null ? "&remoteip=" + encode(remoteIp) : "");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(captchaVerifyUrl))
                    .timeout(captchaHttpRequestTimeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = getCaptchaHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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
                    "CAPTCHA verification request interrupted (verifyUrl=" + captchaVerifyUrl + ").",
                    e
            );
        } catch (Exception e) {
            throw new CaptchaVerificationException(
                    "CAPTCHA verification request failed (verifyUrl=" + captchaVerifyUrl + ").",
                    e
            );
        }
    }

    // --- UPLOAD ---

    public long getUploadMaxFileSizeBytes()    { return uploadMaxFileSizeBytes; }
    public long getUploadMaxRequestSizeBytes() { return uploadMaxRequestSizeBytes; }
    public int  getUploadMaxFileCount()        { return uploadMaxFileCount; }
    public Set<String> getUploadAllowedMimeTypes() { return uploadAllowedMimeTypes; }
    public Duration getCaptchaHttpConnectTimeout() { return captchaHttpConnectTimeout; }
    public Duration getCaptchaHttpRequestTimeout() { return captchaHttpRequestTimeout; }

    // --- FORWARD ACTION ---
    public Duration getForwardHttpConnectTimeout() { return forwardHttpConnectTimeout; }
    public Duration getForwardHttpRequestTimeout() { return forwardHttpRequestTimeout; }
    public HttpClient getForwardHttpClient() { return getRequiredHttpClient(forwardHttpClient, "forward"); }

    /**
     * Returns all configured forward targets, in declaration order.
     */
    public Collection<ForwardTarget> getForwardTargets() {
        return forwardTargets.values();
    }

    /**
     * Resolves a forward target by its stable id.
     *
     * @param id the value stored in the JCR {@code targetId} property
     * @return the configured forward target, or empty if the id is unknown
     */
    public Optional<ForwardTarget> resolveForwardTarget(String id) {
        ForwardTarget target = forwardTargets.get(id);
        return target != null ? Optional.of(target) : Optional.empty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private HttpClient getCaptchaHttpClient() {
        return getRequiredHttpClient(captchaHttpClient, "captcha");
    }

    private static HttpClient getRequiredHttpClient(AtomicReference<HttpClient> clientRef, String clientName) {
        HttpClient client = clientRef.get();
        if (client == null) {
            throw new IllegalStateException("Formidable " + clientName + " HTTP client is not initialized.");
        }
        return client;
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
