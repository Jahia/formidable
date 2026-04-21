package org.jahia.modules.formidable.engine.actions;

import java.util.Set;

public final class FormidableConstants {

    private FormidableConstants() {}

    public static final Set<String> CAPTCHA_TOKEN_FIELDS = Set.of(
            "cf-turnstile-response",  // Cloudflare Turnstile
            "h-captcha-response",     // hCaptcha
            "g-recaptcha-response"    // Google reCAPTCHA v2
    );
}

