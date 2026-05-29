package org.jahia.modules.formidable.engine.actions;

/**
 * SPI-level representation of one validated uploaded file passed to form actions.
 *
 * @param fieldName    submitted form field name
 * @param originalName sanitized original filename
 * @param mimeType     detected MIME type
 * @param data         uploaded file bytes
 */
public record SubmittedFile(
        String fieldName,
        String originalName,
        String mimeType,
        byte[] data
) {
    public static SubmittedFile fromParsedFile(FormDataParser.FormFile file) {
        return new SubmittedFile(file.fieldName(), file.originalName(), file.mimeType(), file.data());
    }
}
