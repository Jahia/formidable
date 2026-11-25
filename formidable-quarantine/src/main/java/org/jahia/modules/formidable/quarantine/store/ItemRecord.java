package org.jahia.modules.formidable.quarantine.store;

/**
 * What the store says of one item, without its bytes.
 *
 * @param name          the name of the item node, {@code item0} for the first file
 * @param fieldName     the file field of the upload
 * @param fileName      the sanitized original name of the file
 * @param mimeType      the detected MIME type, or {@code null}
 * @param size          the number of bytes at intake
 * @param sha256        the SHA-256 computed at intake, lower-case hexadecimal
 * @param binaryPresent false once the bytes were deleted
 */
public record ItemRecord(String name, String fieldName, String fileName, String mimeType, long size, String sha256,
                         boolean binaryPresent) {
}
