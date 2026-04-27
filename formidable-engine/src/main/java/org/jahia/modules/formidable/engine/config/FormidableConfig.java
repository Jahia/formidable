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
    String captcha_siteKey() default "";

    @AttributeDefinition(
            name = "CAPTCHA secret key",
            description = "Private secret key used to verify the submitted token server-side. Never exposed to the client.",
            type = AttributeType.PASSWORD
    )
    String captcha_secretKey() default "";

    @AttributeDefinition(
            name = "CAPTCHA provider script URL",
            description = "URL of the CAPTCHA provider JavaScript API injected in the page " +
                    "(e.g. https://challenges.cloudflare.com/turnstile/v0/api.js).",
            type = AttributeType.STRING
    )
    String captcha_scriptUrl() default "";

    @AttributeDefinition(
            name = "CAPTCHA verification endpoint URL",
            description = "Server-side token verification endpoint of the CAPTCHA provider " +
                    "(e.g. https://challenges.cloudflare.com/turnstile/v0/siteverify).",
            type = AttributeType.STRING
    )
    String captcha_verifyUrl() default "";

    // --- FILE UPLOAD ---

    @AttributeDefinition(
            name = "Max file size (bytes)",
            description = "Maximum allowed size per uploaded file in bytes. Default: 10 MB.",
            type = AttributeType.LONG
    )
    long upload_maxFileSizeBytes() default 10_485_760L;

    @AttributeDefinition(
            name = "Max request size (bytes)",
            description = "Maximum allowed total multipart request body size in bytes. Default: 50 MB.",
            type = AttributeType.LONG
    )
    long upload_maxRequestSizeBytes() default 52_428_800L;

    @AttributeDefinition(
            name = "Max file count per request",
            description = "Maximum number of file parts allowed in a single multipart request. " +
                    "Limits resource exhaustion (CVE-2023-24998). Default: 10.",
            type = AttributeType.INTEGER
    )
    int upload_maxFileCount() default 10;

    @AttributeDefinition(
            name = "Allowed MIME types (fallback)",
            description = "Global MIME type allowlist applied as fallback when no 'accept' property is defined " +
                    "on the fmdb:inputFile field. Comma-separated list of MIME types.",
            type = AttributeType.STRING
    )
    String upload_allowedMimeTypes() default "image/jpeg,image/png,image/gif,image/webp,application/pdf," +
            "application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document," +
            "application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet," +
            "text/plain,text/csv";
}

