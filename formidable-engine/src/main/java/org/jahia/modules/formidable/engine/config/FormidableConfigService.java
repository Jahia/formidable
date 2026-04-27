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

    private static final Logger log = LoggerFactory.getLogger(FormidableConfigService.class);

    private String captchaSiteKey;
    private String captchaSecretKey;
    private String captchaScriptUrl;
    private String captchaVerifyUrl;

    private long uploadMaxFileSizeBytes;
    private long uploadMaxRequestSizeBytes;
    private int  uploadMaxFileCount;
    private Set<String> uploadAllowedMimeTypes;

    private final HttpClient http = HttpClient.newHttpClient();

    @Activate
    @Modified
    public void activate(FormidableConfig config) {
        captchaSiteKey    = config.captcha_siteKey();
        captchaSecretKey  = config.captcha_secretKey();
        captchaScriptUrl  = config.captcha_scriptUrl();
        captchaVerifyUrl  = config.captcha_verifyUrl();

        uploadMaxFileSizeBytes    = config.upload_maxFileSizeBytes();
        uploadMaxRequestSizeBytes = config.upload_maxRequestSizeBytes();
        uploadMaxFileCount        = config.upload_maxFileCount();
        uploadAllowedMimeTypes    = Arrays.stream(config.upload_allowedMimeTypes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        log.info("FormidableConfigService configured: captcha={}, maxFileSize={}MB, maxRequest={}MB, allowedTypes={}",
                isCaptchaConfigured() ? "[set]" : "[missing]",
                uploadMaxFileSizeBytes / 1_048_576,
                uploadMaxRequestSizeBytes / 1_048_576,
                uploadAllowedMimeTypes.size());
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

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}

