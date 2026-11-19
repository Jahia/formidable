package org.jahia.test.modules.formidable.quarantine.check;

import java.io.IOException;
import java.io.InputStream;

/**
 * One held upload, as a check sees it. The bytes are read through {@link #open()}, never held in an array.
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
     * @return a new stream over the bytes; the caller closes it
     * @throws IOException when the bytes cannot be read
     */
    InputStream open() throws IOException;
}
