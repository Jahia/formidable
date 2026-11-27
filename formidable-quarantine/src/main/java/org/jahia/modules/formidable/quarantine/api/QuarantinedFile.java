package org.jahia.modules.formidable.quarantine.api;

import org.osgi.annotation.versioning.ProviderType;

import java.io.IOException;
import java.io.InputStream;

/**
 * One held upload, as a check sees it. A check reads the bytes through {@link #open()}: a stream over the binary
 * that the repository stores, read as the check reads it, with no copy of the file in memory. A check that opens the
 * file twice reads it twice.
 * <p>
 * The run calls the checks in batches, at most one call for each file in a batch. When a batch holds a
 * {@link Qualifier#submit} of a check that reads bytes, the run opens a session for the whole batch and closes it once
 * the last call of the batch returns. {@link #open()} works only while such a session is open:
 * <ul>
 * <li>A stream reads until its batch ends. A file that a check keeps opens a new stream during any later batch of the
 * same run that opens a session, and throws in any other batch.</li>
 * <li>{@link Qualifier#appliesTo} runs outside every batch, so {@link #open()} throws there.</li>
 * <li>{@link Qualifier#poll} receives no file. A file kept from {@code submit} opens in a poll only when the poll
 * shares its batch with a submit that reads bytes.</li>
 * <li>A {@link Scope#METADATA} check is handed a file whose {@link #open()} always throws, whatever the batch.</li>
 * </ul>
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
