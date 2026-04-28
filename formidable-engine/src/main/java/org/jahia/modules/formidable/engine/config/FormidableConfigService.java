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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * A resolved forward target entry from the operator configuration.
     *
     * @param id    stable identifier stored in JCR (e.g. {@code salesforce-prod})
     * @param label human-readable label shown in the CMS editor
     * @param uri   resolved target URI; guaranteed to use HTTPS
     */
    public record ForwardTarget(String id, String label, URI uri) {}

    private static final Logger log = LoggerFactory.getLogger(FormidableConfigService.class);

    private String captchaSiteKey;
    private String captchaSecretKey;
    private String captchaScriptUrl;
    private String captchaVerifyUrl;

    private long uploadMaxFileSizeBytes;
    private long uploadMaxRequestSizeBytes;
    private int  uploadMaxFileCount;
    private Set<String> uploadAllowedMimeTypes;

    /** Keyed by target id. Insertion-ordered so the choice list is stable. */
    private Map<String, ForwardTarget> forwardTargets = new LinkedHashMap<>();

    private final HttpClient http = HttpClient.newHttpClient();

    @Activate
    @Modified
    public void activate(FormidableConfig config) {
        captchaSiteKey    = config.captchaSiteKey();
        captchaSecretKey  = config.captchaSecretKey();
        captchaScriptUrl  = config.captchaScriptUrl();
        captchaVerifyUrl  = config.captchaVerifyUrl();

        uploadMaxFileSizeBytes    = config.uploadMaxFileSizeBytes();
        uploadMaxRequestSizeBytes = config.uploadMaxRequestSizeBytes();
        uploadMaxFileCount        = config.uploadMaxFileCount();
        uploadAllowedMimeTypes    = Arrays.stream(config.uploadAllowedMimeTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        forwardTargets = parseForwardTargets(config.forwardTargets());

        log.info("FormidableConfigService configured: captcha={}, maxFileSize={}MB, maxRequest={}MB, allowedTypes={}, forwardTargets={}",
                isCaptchaConfigured() ? "[set]" : "[missing]",
                uploadMaxFileSizeBytes / 1_048_576,
                uploadMaxRequestSizeBytes / 1_048_576,
                uploadAllowedMimeTypes.size(),
                forwardTargets.size());
    }

    /**
     * Parses the {@code forwardTargets} config value.
     * Each line has the form: {@code id|Label|https://...}
     * Invalid entries are logged and skipped. For local Docker development,
     * plain HTTP is also accepted for localhost and host.docker.internal.
     */
    private static Map<String, ForwardTarget> parseForwardTargets(String raw) {
        Map<String, ForwardTarget> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String[] entries = raw.split("[\n\r]+");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\|", 3);
            if (parts.length != 3) {
                log.warn("[FormidableConfigService] Skipping malformed forwardTargets entry (expected id|label|url): '{}'", trimmed);
                continue;
            }
            String id    = parts[0].trim();
            String label = parts[1].trim();
            String url   = parts[2].trim();

            if (id.isEmpty() || url.isEmpty()) {
                log.warn("[FormidableConfigService] Skipping forwardTargets entry with empty id or url: '{}'", trimmed);
                continue;
            }
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException e) {
                log.warn("[FormidableConfigService] Skipping forwardTargets entry '{}': malformed URI '{}'", id, url);
                continue;
            }
            if (!isSupportedForwardTargetUri(uri)) {
                log.warn("[FormidableConfigService] Skipping forwardTargets entry '{}': URI must use HTTPS, except for localhost and host.docker.internal over HTTP.", id);
                continue;
            }
            if (result.containsKey(id)) {
                log.warn("[FormidableConfigService] Duplicate forwardTargets id '{}', keeping first occurrence.", id);
                continue;
            }
            result.put(id, new ForwardTarget(id, label, uri));
        }
        return result;
    }

    private static boolean isSupportedForwardTargetUri(URI uri) {
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return true;
        }

        if (!"http".equalsIgnoreCase(scheme)) {
            return false;
        }

        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "host.docker.internal".equalsIgnoreCase(host);
    }

    // --- CAPTCHA ---

    public String getCaptchaSiteKey()   { return captchaSiteKey; }
    public String getCaptchaScriptUrl() { return captchaScriptUrl; }

    public boolean isCaptchaConfigured() {
        return captchaSiteKey != null && !captchaSiteKey.isBlank()
                && captchaSecretKey != null && !captchaSecretKey.isBlank();
    }

    /**
     * Verifies the CAPTCHA token against the provider's server-side endpoint.
     *
     * @param token    the token submitted by the client widget
     * @param remoteIp the client's IP address (optional but recommended)
     * @return true if the provider confirms the token is valid
     */
    public boolean verifyCaptcha(String token, String remoteIp) {
        if (!isCaptchaConfigured()) {
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
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject result = new JSONObject(response.body());
            boolean success = result.optBoolean("success", false);
            if (!success) {
                log.debug("CAPTCHA verification failed. Provider response: {}", response.body());
            }
            return success;
        } catch (Exception e) {
            log.error("CAPTCHA verification request failed (verifyUrl={})", captchaVerifyUrl, e);
            return false;
        }
    }

    // --- UPLOAD ---

    public long getUploadMaxFileSizeBytes()    { return uploadMaxFileSizeBytes; }
    public long getUploadMaxRequestSizeBytes() { return uploadMaxRequestSizeBytes; }
    public int  getUploadMaxFileCount()        { return uploadMaxFileCount; }
    public Set<String> getUploadAllowedMimeTypes() { return uploadAllowedMimeTypes; }

    // --- FORWARD ACTION ---

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
     * @return the target URI, or empty if the id is unknown
     */
    public Optional<URI> resolveForwardTarget(String id) {
        ForwardTarget target = forwardTargets.get(id);
        return target != null ? Optional.of(target.uri()) : Optional.empty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
