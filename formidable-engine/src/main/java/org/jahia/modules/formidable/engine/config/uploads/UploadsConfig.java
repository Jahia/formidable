package org.jahia.modules.formidable.engine.config.uploads;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The uploads theme: the size and count limits of a multipart submission, and the MIME types a file field
 * accepts when it declares none. Deployed as {@code karaf/etc/org.jahia.modules.formidable.uploads.cfg}.
 */
@ObjectClassDefinition(
        name = "Formidable — Uploads",
        description = "The limits of a submission carrying files, and the MIME types accepted by default."
)
public @interface UploadsConfig {

    long DEFAULT_UPLOAD_MAX_FILE_SIZE_BYTES = 10_485_760L;
    long DEFAULT_UPLOAD_MAX_REQUEST_SIZE_BYTES = 52_428_800L;
    int DEFAULT_UPLOAD_MAX_FILE_COUNT = 10;

    @AttributeDefinition(
            name = "Max file size (bytes)",
            description = "Maximum allowed size per uploaded file in bytes. Default: 10 MB.",
            type = AttributeType.LONG
    )
    long uploadMaxFileSizeBytes() default DEFAULT_UPLOAD_MAX_FILE_SIZE_BYTES;

    @AttributeDefinition(
            name = "Max request size (bytes)",
            description = "Maximum allowed total multipart request body size in bytes. Default: 50 MB.",
            type = AttributeType.LONG
    )
    long uploadMaxRequestSizeBytes() default DEFAULT_UPLOAD_MAX_REQUEST_SIZE_BYTES;

    @AttributeDefinition(
            name = "Max file count per request",
            description = "Maximum number of file parts allowed in a single multipart request. " +
                    "Limits resource exhaustion (CVE-2023-24998). Default: 10.",
            type = AttributeType.INTEGER
    )
    int uploadMaxFileCount() default DEFAULT_UPLOAD_MAX_FILE_COUNT;

    @AttributeDefinition(
            name = "Allowed MIME types (fallback)",
            description = "Global MIME type allowlist applied as fallback when no 'accept' property is defined " +
                    "on the fmdb:inputFile field. Comma-separated list of MIME types or wildcards (for example image/*). " +
                    "Supported allowlist values: image/jpeg, image/png, image/gif, image/webp, application/pdf, " +
                    "application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document, " +
                    "application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, " +
                    "application/vnd.oasis.opendocument.text, application/vnd.oasis.opendocument.spreadsheet, " +
                    "text/plain, text/csv, video/mp4, video/webm, video/ogg, video/x-matroska. " +
                    "Validation uses Tika filename-aware detection (detect(byte[], String)) so ambiguous formats " +
                    "such as CSV, Matroska, WebM, and similar container or text-based files are resolved from " +
                    "both content and original filename extension.",
            type = AttributeType.STRING
    )
    String uploadAllowedMimeTypes() default "image/jpeg,image/png,image/gif,image/webp,application/pdf," +
            "application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document," +
            "application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet," +
            "application/vnd.oasis.opendocument.text,application/vnd.oasis.opendocument.spreadsheet," +
            "text/plain,text/csv," +
            "video/mp4,video/webm,video/ogg,video/x-matroska";
}
