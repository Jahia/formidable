package org.jahia.modules.formidable.engine.actions;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Verifies the CAPTCHA token submitted by the client against the provider's server-side API.
 *
 * Called only in JCR mode (destination = save2jcrAction or no destination).
 * In transfer mode (destination = sendDataAction), FormSubmitAction never calls this action;
 * the captcha token is forwarded to the target, which handles its own verification.
 */
@Component(service = FormAction.class)
public class CaptchaVerificationFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(CaptchaVerificationFormAction.class);



    private static final String TURNSTILE_URL  = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final String HCAPTCHA_URL   = "https://api.hcaptcha.com/siteverify";
    private static final String RECAPTCHA_URL  = "https://www.google.com/recaptcha/api/siteverify";

    private final HttpClient http = HttpClient.newHttpClient();

    @Override
    public String getNodeType() {
        return "fmdb:captchaAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {

        String secretKey = readProperty(actionNode, "secretKey");
        if (secretKey == null || secretKey.isBlank()) {
            throw FormActionException.serverError("fmdb:captchaAction is missing a secretKey.");
        }


        String scriptUrl = readProperty(actionNode, "scriptUrl");
        String provider = deriveProvider(scriptUrl);

        // Each widget injects its token under its own native field name
        String tokenField = switch (provider) {
            case "hcaptcha"     -> "h-captcha-response";
            case "recaptcha_v2" -> "g-recaptcha-response";
            default             -> "cf-turnstile-response"; // turnstile
        };

        String token = getFirstParam(parameters, tokenField);
        if (token == null || token.isBlank()) {
            throw FormActionException.badRequest("CAPTCHA token is missing.");
        }

        boolean valid = verify(secretKey, token, provider, req.getRemoteAddr());
        if (!valid) {
            throw FormActionException.badRequest("CAPTCHA verification failed.");
        }
    }

    private static String deriveProvider(String scriptUrl) {
        if (scriptUrl == null) return "turnstile";
        if (scriptUrl.contains("hcaptcha.com")) return "hcaptcha";
        if (scriptUrl.contains("google.com/recaptcha")) return "recaptcha_v2";
        return "turnstile";
    }

    private boolean verify(String secretKey, String token, String provider, String remoteIp) {
        String verifyUrl = switch (provider) {
            case "hcaptcha"    -> HCAPTCHA_URL;
            case "recaptcha_v2" -> RECAPTCHA_URL;
            default            -> TURNSTILE_URL;
        };

        String body = "secret=" + urlEncode(secretKey)
                + "&response=" + urlEncode(token)
                + "&remoteip=" + urlEncode(remoteIp);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(verifyUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject result = new JSONObject(response.body());
            return result.optBoolean("success", false);
        } catch (Exception e) {
            log.error("CAPTCHA verification request failed for provider '{}'", provider, e);
            return false;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String readProperty(JCRNodeWrapper node, String name) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getFirstParam(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}

