package org.jahia.modules.formidable.quarantine.store;

import org.jahia.modules.formidable.engine.api.SubmittedFile;

/**
 * One upload as the store writes it: its bytes read once from {@link SubmittedFile#data()}, which clones the array on
 * every call, and their SHA-256 computed once from those bytes.
 */
final class IntakeFile {

    private final SubmittedFile file;
    private final byte[] bytes;
    private final String sha256;

    private IntakeFile(SubmittedFile file, byte[] bytes) {
        this.file = file;
        this.bytes = bytes;
        this.sha256 = Sha256.hex(bytes);
    }

    static IntakeFile of(SubmittedFile file) {
        return new IntakeFile(file, file.data());
    }

    String fieldName() {
        return file.fieldName();
    }

    String fileName() {
        return file.originalName();
    }

    String mimeType() {
        return file.mimeType();
    }

    /** The bytes read at intake; the store hands them to the repository and to nothing else. */
    byte[] bytes() {
        return bytes;
    }

    String sha256() {
        return sha256;
    }

    /** What a log line may say: the field and the size, never the file name the visitor chose, never the bytes. */
    @Override
    public String toString() {
        return "IntakeFile[fieldName=" + fieldName() + ", size=" + bytes.length + "]";
    }
}
