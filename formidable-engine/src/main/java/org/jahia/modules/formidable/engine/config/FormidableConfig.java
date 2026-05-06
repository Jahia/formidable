package org.jahia.modules.formidable.engine.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi Metatype configuration for the Formidable module.
 * Deploy as: {karaf}/etc/org.jahia.modules.formidable.cfg
 */
@ObjectClassDefinition(
        name = "Formidable Configuration",
        description = "Global configuration for the Formidable form builder module."
)
public @interface FormidableConfig {

    // --- CAPTCHA ---

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
            name = "CAPTCHA verification endpoint URL",
            description = "Server-side token verification endpoint of the CAPTCHA provider " +
                    "(e.g. https://challenges.cloudflare.com/turnstile/v0/siteverify).",
            type = AttributeType.STRING
    )
    String captchaVerifyUrl() default "";

    // --- FILE UPLOAD ---

    @AttributeDefinition(
            name = "Max file size (bytes)",
            description = "Maximum allowed size per uploaded file in bytes. Default: 10 MB.",
            type = AttributeType.LONG
    )
    long uploadMaxFileSizeBytes() default 10_485_760L;

    @AttributeDefinition(
            name = "Max request size (bytes)",
            description = "Maximum allowed total multipart request body size in bytes. Default: 50 MB.",
            type = AttributeType.LONG
    )
    long uploadMaxRequestSizeBytes() default 52_428_800L;

    @AttributeDefinition(
            name = "Max file count per request",
            description = "Maximum number of file parts allowed in a single multipart request. " +
                    "Limits resource exhaustion (CVE-2023-24998). Default: 10.",
            type = AttributeType.INTEGER
    )
    int uploadMaxFileCount() default 10;

    // --- FORWARD ACTION ---

    @AttributeDefinition(
            name = "Forward action targets",
            description = "Newline-separated list of allowed forward targets for fmdb:forwardAction. " +
                    "Each entry has the form: id|Label|https://target-url. " +
                    "Commas inside labels or URLs are preserved. " +
                    "The id is stored in JCR; the URL is resolved server-side and never exposed to contributors. " +
                    "Leave empty to disable all forward actions (fail-safe default).",
            type = AttributeType.STRING
    )
    String forwardTargets() default "";

    @AttributeDefinition(
            name = "Enable development forward targets",
            description = "Allows use of devForwardTargets. Disabled by default. " +
                    "When enabled, only plain HTTP targets on localhost or host.docker.internal are accepted.",
            type = AttributeType.BOOLEAN
    )
    boolean enableDevForwardTargets() default false;

    @AttributeDefinition(
            name = "Development forward action targets",
            description = "Newline-separated list of development-only forward targets for fmdb:forwardAction. " +
                    "Each entry has the form: id|Label|http://localhost/... or id|Label|http://host.docker.internal/... " +
                    "Ignored unless 'Enable development forward targets' is true.",
            type = AttributeType.STRING
    )
    String devForwardTargets() default "";

    // ---

    @AttributeDefinition(
            name = "Allowed MIME types (fallback)",
            description = "Global MIME type allowlist applied as fallback when no 'accept' property is defined " +
                    "on the fmdb:inputFile field. Comma-separated list of MIME types.",
            type = AttributeType.STRING
    )
    String uploadAllowedMimeTypes() default "image/jpeg,image/png,image/gif,image/webp,application/pdf," +
            "application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document," +
            "application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet," +
            "text/plain,text/csv," +
            "video/mp4,video/webm,video/ogg,video/x-matroska";
}
