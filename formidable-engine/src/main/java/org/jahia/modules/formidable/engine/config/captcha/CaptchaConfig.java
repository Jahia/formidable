package org.jahia.modules.formidable.engine.config.captcha;

import org.jahia.modules.formidable.engine.config.common.ConfigurationValues;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The CAPTCHA theme: the provider's keys and endpoints, and the two timeouts of the server-side verification.
 * Deployed as {@code karaf/etc/org.jahia.modules.formidable.captcha.cfg}.
 */
@ObjectClassDefinition(
        name = "Formidable — CAPTCHA",
        description = "The CAPTCHA provider a form may require: its keys, its script and verification endpoints, the timeouts."
)
public @interface CaptchaConfig {

    @AttributeDefinition(
            name = "CAPTCHA site key",
            description = "Public site key provided by the CAPTCHA service dashboard. Injected in the page to render the widget.",
            type = AttributeType.STRING
    )
    String captchaSiteKey() default "";

    @AttributeDefinition(
            name = "CAPTCHA secret key",
            description = "Private secret key used to verify the submitted token server-side. Never exposed to the client.",
            type = AttributeType.PASSWORD
    )
    String captchaSecretKey() default "";

    @AttributeDefinition(
            name = "CAPTCHA provider script URL",
            description = "URL of the CAPTCHA provider JavaScript API injected in the page " +
                    "(e.g. https://challenges.cloudflare.com/turnstile/v0/api.js).",
            type = AttributeType.STRING
    )
    String captchaScriptUrl() default "";

    @AttributeDefinition(
            name = "CAPTCHA widget variable",
            description = "Name of the global window object exposed by the CAPTCHA provider script " +
                    "(e.g. turnstile, hcaptcha, grecaptcha). Used client-side as window[widgetVar].render().",
            type = AttributeType.STRING
    )
    String captchaWidgetVar() default "";

    @AttributeDefinition(
            name = "CAPTCHA token field name",
            description = "Name of the hidden form field auto-injected by the CAPTCHA widget " +
                    "(e.g. cf-turnstile-response, h-captcha-response, g-recaptcha-response).",
            type = AttributeType.STRING
    )
    String captchaTokenField() default "";

    @AttributeDefinition(
            name = "CAPTCHA verification endpoint URL",
            description = "Server-side token verification endpoint of the CAPTCHA provider " +
                    "(e.g. https://challenges.cloudflare.com/turnstile/v0/siteverify).",
            type = AttributeType.STRING
    )
    String captchaVerifyUrl() default "";

    @AttributeDefinition(
            name = "CAPTCHA HTTP connect timeout (seconds)",
            description = "Maximum time allowed to establish the server-side connection to the CAPTCHA provider. " +
                    "Also used client-side as the maximum time to wait for the provider script to expose the " +
                    "widget API (window[widgetVar].render) before giving up rendering the widget. Default: 5 seconds.",
            type = AttributeType.LONG
    )
    long captchaHttpConnectTimeoutSeconds() default ConfigurationValues.DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS;

    @AttributeDefinition(
            name = "CAPTCHA HTTP request timeout (seconds)",
            description = "Maximum total time allowed for the server-side CAPTCHA verification request. Default: 10 seconds.",
            type = AttributeType.LONG
    )
    long captchaHttpRequestTimeoutSeconds() default ConfigurationValues.DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS;
}
