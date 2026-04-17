package org.jahia.modules.formidable.engine.captcha;

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

/**
 * Reads CAPTCHA configuration via {@link CaptchaConfig} (OSGi Metatype) from
 * org.jahia.modules.formidable.captcha.cfg and provides server-side token verification.
 *
 * Can also be configured from the Felix Web Console without editing the .cfg file manually.
 */
@Component(
        service = CaptchaConfigService.class,
        configurationPid = "org.jahia.modules.formidable.captcha",
        immediate = true
)
@Designate(ocd = CaptchaConfig.class)
public class CaptchaConfigService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaConfigService.class);

    private String siteKey;
    private String scriptUrl;
    private String verifyUrl;
    private String secretKey;

    private final HttpClient http = HttpClient.newHttpClient();

    @Activate
    @Modified
    public void activate(CaptchaConfig config) {
        siteKey   = config.siteKey();
        scriptUrl = config.scriptUrl();
        verifyUrl = config.verifyUrl();
        secretKey = config.secretKey();
        log.info("CaptchaConfigService configured: scriptUrl={}, verifyUrl={}, siteKey={}",
                scriptUrl, verifyUrl, !siteKey.isBlank() ? "[set]" : "[missing]");
    }

    public String getSiteKey() {
        return siteKey;
    }

    public String getScriptUrl() {
        return scriptUrl;
    }

    public boolean isConfigured() {
        return !siteKey.isBlank() && !secretKey.isBlank();
    }

    /**
     * Verifies the CAPTCHA token against the provider's server-side endpoint.
     *
     * @param token    the token submitted by the client widget
     * @param remoteIp the client's IP address (optional but recommended)
     * @return true if the provider confirms the token is valid
     */
    public boolean verify(String token, String remoteIp) {
        if (!isConfigured()) {
            log.warn("CAPTCHA verification skipped: service is not configured (siteKey or secretKey missing).");
            return false;
        }
        if (token == null || token.isBlank()) {
            return false;
        }

        String body = "secret=" + encode(secretKey)
                + "&response=" + encode(token)
                + (remoteIp != null ? "&remoteip=" + encode(remoteIp) : "");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(verifyUrl))
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
            log.error("CAPTCHA verification request failed (verifyUrl={})", verifyUrl, e);
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
