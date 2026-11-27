package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Scope;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * The file the run hands to a {@link Scope#METADATA} check: the metadata of the held file, and no byte.
 * {@link #open()} throws.
 */
final class MetadataOnlyFile implements QuarantinedFile {

    private final QuarantinedFile file;

    MetadataOnlyFile(QuarantinedFile file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public String sha256() {
        return file.sha256();
    }

    @Override
    public long size() {
        return file.size();
    }

    @Override
    public String mimeType() {
        return file.mimeType();
    }

    @Override
    public String fileName() {
        return file.fileName();
    }

    @Override
    public InputStream open() throws IOException {
        throw new IOException("A metadata check reads no byte of " + file);
    }

    /** What a log line may say: the file it wraps says it. */
    @Override
    public String toString() {
        return "MetadataOnly[" + file + "]";
    }
}
