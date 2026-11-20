package org.jahia.test.modules.formidable.quarantine.check;

import java.io.IOException;
import java.io.InputStream;

/**
 * One held upload, as a check sees it. A check reads the bytes through {@link #open()}. The contract promises a
 * stream, not streaming: the store of the module reads the whole item into memory on each call of {@link #open()},
 * so a check that opens the file twice reads it twice. A check that must not hold a large file in memory needs
 * another store.
 */
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
     * @return a new stream over the bytes, read into memory for this call; the caller closes it
     * @throws IOException when the bytes cannot be read
     */
    InputStream open() throws IOException;
}
