package org.jahia.modules.formidable.engine.config.uploads;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The uploads theme: the size and count limits of a multipart submission, and the file types a file field
 * may accept. Deployed as {@code karaf/etc/org.jahia.modules.formidable.uploads.cfg}.
 */
@ObjectClassDefinition(
        name = "Formidable — Uploads",
        description = "The limits of a submission carrying files, and the file types a file field may accept."
)
public @interface UploadsConfig {

    long DEFAULT_UPLOAD_MAX_FILE_SIZE_BYTES = 10_485_760L;
    long DEFAULT_UPLOAD_MAX_REQUEST_SIZE_BYTES = 52_428_800L;
    int DEFAULT_UPLOAD_MAX_FILE_COUNT = 10;
    /** Tika's registry of the version the engine embeds: which MIME type an extension stands for. */
    String TIKA_REGISTRY = "https://github.com/apache/tika/blob/3.3.2/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml";

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
            name = "Allowed file types",
            description = "The file types a file field may accept, comma-separated: an extension (pdf, docx), a MIME " +
                    "type (application/pdf), a wildcard (image/*) or */* for any file. An extension stands for the MIME type Apache " +
                    "Tika gives it; give the MIME type when an extension is shared (ogg is read as audio/vorbis). A field " +
                    "without accepted types accepts all of them, a field with some keeps those still listed here. " +
                    "Empty: no file is accepted. Every uploaded file's real type is detected and checked. Which type " +
                    "an extension stands for: " + UploadsConfig.TIKA_REGISTRY,
            type = AttributeType.STRING
    )
    String uploadAllowedTypes() default "jpg,png,gif,webp,pdf,doc,docx,xls,xlsx,odt,ods,txt,csv,mp4,webm,ogv,mkv";
}
