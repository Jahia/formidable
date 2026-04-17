package org.jahia.modules.formidable.engine.captcha;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi Metatype configuration for the Formidable CAPTCHA service.
 *
 * Supports Cloudflare Turnstile, hCaptcha and Google reCAPTCHA v2.
 * Deploy as: {karaf}/etc/org.jahia.modules.formidable.captcha.cfg
 */
@ObjectClassDefinition(
        name = "Formidable - CAPTCHA Configuration",
        description = "Configures the CAPTCHA provider used by fmdbmix:captcha-enabled forms. " +
                "Supported providers: Cloudflare Turnstile, hCaptcha, Google reCAPTCHA v2."
)
public @interface CaptchaConfig {

    @AttributeDefinition(
            name = "Site key",
            description = "Public site key provided by the CAPTCHA service dashboard. Injected in the page to render the widget.",
            type = AttributeType.STRING
    )
    String siteKey() default "";

    @AttributeDefinition(
            name = "Secret key",
            description = "Private secret key used to verify the submitted token server-side. Never exposed to the client.",
            type = AttributeType.PASSWORD
    )
    String secretKey() default "";

    @AttributeDefinition(
            name = "Provider script URL",
            description = "URL of the CAPTCHA provider JavaScript API injected in the page " +
                    "(e.g. https://challenges.cloudflare.com/turnstile/v0/api.js).",
            type = AttributeType.STRING
    )
    String scriptUrl() default "";

    @AttributeDefinition(
            name = "Verification endpoint URL",
            description = "Server-side token verification endpoint of the CAPTCHA provider " +
                    "(e.g. https://challenges.cloudflare.com/turnstile/v0/siteverify).",
            type = AttributeType.STRING
    )
    String verifyUrl() default "";
}
