package org.jahia.modules.formidable.engine.config.captcha;

import org.jahia.modules.formidable.engine.config.ThemeLifecycle;
import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.json.JSONObject;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * The CAPTCHA theme read from {@code org.jahia.modules.formidable.captcha.cfg}: what the render filter hands
 * the page, whether verification is configured at all, and the verification itself against the provider's
 * endpoint (the submission pipeline's step 7).
 */
@Component(service = CaptchaConfigService.class, configurationPid = CaptchaConfigService.PID, immediate = true)
@Designate(ocd = CaptchaConfig.class)
public class CaptchaConfigService {

    public static final String PID = "org.jahia.modules.formidable.captcha";

    /** Verification could not complete because of an infrastructure or provider-side technical failure. */
    public static class CaptchaVerificationException extends Exception {
        public CaptchaVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record Snapshot(
            String siteKey,
            String secretKey,
            String scriptUrl,
            String widgetVar,
            String tokenField,
            URI verifyUri,
            Duration connectTimeout,
            Duration requestTimeout,
            HttpClient httpClient
    ) {
        boolean verificationConfigured() {
            return isSet(siteKey) && isSet(secretKey) && verifyUri != null;
        }

        boolean widgetConfigured() {
            return isSet(siteKey) && isSet(scriptUrl) && isSet(widgetVar) && isSet(tokenField);
        }

        private static boolean isSet(String value) {
            return value != null && !value.isBlank();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(CaptchaConfigService.class);

    private final ThemeLifecycle<Snapshot> lifecycle = new ThemeLifecycle<>(PID, CaptchaConfig.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetConfigurationAdmin")
    public void setConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.setConfigurationAdmin(admin);
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin admin) {
        lifecycle.unsetConfigurationAdmin(admin);
    }

    @Activate
    @Modified
    public void configure(CaptchaConfig config, Map<String, Object> properties) {
        lifecycle.configure(properties, read(config));
    }

    /** Reads the configuration into the snapshot the getters serve, no file behind it; public for the tests. */
    public void activate(CaptchaConfig config) {
        lifecycle.configure(null, read(config));
    }

    private static Snapshot read(CaptchaConfig config) {
        Duration connectTimeout = ConfigurationValues.timeoutSeconds("captchaHttpConnectTimeoutSeconds",
                config.captchaHttpConnectTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS);
        Duration requestTimeout = ConfigurationValues.timeoutSeconds("captchaHttpRequestTimeoutSeconds",
                config.captchaHttpRequestTimeoutSeconds(), ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS);
        Snapshot snapshot = new Snapshot(
                config.captchaSiteKey(),
                config.captchaSecretKey(),
                config.captchaScriptUrl(),
                config.captchaWidgetVar(),
                config.captchaTokenField(),
                parseVerifyUri(config.captchaVerifyUrl()),
                connectTimeout,
                requestTimeout,
                ConfigurationValues.httpClient(connectTimeout)
        );
        log.info("CaptchaConfigService configured: verification={}, widget={}, connectTimeout={}s, requestTimeout={}s",
                snapshot.verificationConfigured() ? "[set]" : "[missing]",
                snapshot.widgetConfigured() ? "[set]" : "[missing]",
                connectTimeout.toSeconds(),
                requestTimeout.toSeconds());
        return snapshot;
    }

    private static URI parseVerifyUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            log.warn("[CaptchaConfigService] Invalid captchaVerifyUrl '{}': malformed URI.", rawUrl);
            return null;
        }
        if (uri.getUserInfo() != null) {
            log.warn("[CaptchaConfigService] Invalid captchaVerifyUrl '{}': embedded credentials are not allowed.", rawUrl);
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            log.warn("[CaptchaConfigService] Invalid captchaVerifyUrl '{}': HTTPS is required.", rawUrl);
            return null;
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            log.warn("[CaptchaConfigService] Invalid captchaVerifyUrl '{}': hostname is missing.", rawUrl);
            return null;
        }
        return uri;
    }

    public String getCaptchaSiteKey()    { return lifecycle.current().siteKey(); }
    public String getCaptchaScriptUrl()  { return lifecycle.current().scriptUrl(); }
    public String getCaptchaWidgetVar()  { return lifecycle.current().widgetVar(); }
    public String getCaptchaTokenField() { return lifecycle.current().tokenField(); }
    public Duration getCaptchaHttpConnectTimeout() { return lifecycle.current().connectTimeout(); }
    public Duration getCaptchaHttpRequestTimeout() { return lifecycle.current().requestTimeout(); }

    public boolean isCaptchaVerificationConfigured() {
        return lifecycle.current().verificationConfigured();
    }

    public boolean isCaptchaWidgetConfigured() {
        return lifecycle.current().widgetConfigured();
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
        Snapshot snapshot = lifecycle.current();
        if (!snapshot.verificationConfigured()) {
            log.warn("CAPTCHA verification skipped: service is not configured.");
            return false;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        String body = "secret=" + encode(snapshot.secretKey())
                + "&response=" + encode(token)
                + (remoteIp != null ? "&remoteip=" + encode(remoteIp) : "");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(snapshot.verifyUri())
                    .timeout(snapshot.requestTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = snapshot.httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            boolean success = new JSONObject(responseBody).optBoolean("success", false);
            if (!success && log.isDebugEnabled()) {
                log.debug("CAPTCHA verification failed. Provider response: {}", responseBody);
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CaptchaVerificationException("CAPTCHA verification request interrupted (verifyUrl=" + snapshot.verifyUri() + ").", e);
        } catch (Exception e) {
            throw new CaptchaVerificationException("CAPTCHA verification request failed (verifyUrl=" + snapshot.verifyUri() + ").", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
