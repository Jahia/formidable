package org.jahia.modules.formidable.engine.api;

import java.util.Arrays;
import java.util.Objects;

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
    public SubmittedFile {
        data = data == null ? new byte[0] : data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubmittedFile that)) {
            return false;
        }
        return Objects.equals(fieldName, that.fieldName)
                && Objects.equals(originalName, that.originalName)
                && Objects.equals(mimeType, that.mimeType)
                && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(fieldName, originalName, mimeType);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "SubmittedFile[fieldName=" + fieldName
                + ", originalName=" + originalName
                + ", mimeType=" + mimeType
                + ", data=" + Arrays.toString(data) + "]";
    }
}
