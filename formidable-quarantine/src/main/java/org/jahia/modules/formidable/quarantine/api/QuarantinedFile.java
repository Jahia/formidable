package org.jahia.modules.formidable.quarantine.api;

import org.osgi.annotation.versioning.ProviderType;

import java.io.IOException;
import java.io.InputStream;

/**
 * One held upload, as a check sees it. A check reads the bytes through {@link #open()}: a stream over the binary
 * that the repository stores, read as the check reads it, with no copy of the file in memory. A check that opens the
 * file twice reads it twice.
 * <p>
 * The stream reads only while the run holds its session open: during {@link Qualifier#submit}. A file or a stream that
 * a check keeps cannot be read once {@code submit} returned, and {@link #open()} throws in
 * {@link Qualifier#appliesTo}. A {@link Scope#METADATA} check is handed a file whose {@link #open()} always throws.
 */
@ProviderType
public interface QuarantinedFile {

    /** @return the SHA-256 of the bytes computed at intake, in lower-case hexadecimal */
    String sha256();

    /** @return the size of the bytes */
    long size();

    /** @return the MIME type recorded at intake */
    String mimeType();

    /** @return the name of the file as the visitor sent it */
    String fileName();

    /**
     * @return a new stream over the bytes, which the caller closes before {@link Qualifier#submit} returns
     * @throws IOException when the bytes cannot be read, when the run does not hold its session open, or for a
     *                     {@link Scope#METADATA} check
     */
    InputStream open() throws IOException;
}
