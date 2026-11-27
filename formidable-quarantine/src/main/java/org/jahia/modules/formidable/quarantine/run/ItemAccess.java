package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;

import java.io.IOException;
import java.io.InputStream;

/**
 * The bytes of the items of one held submission, open while the run holds the session of {@link CheckStore#withItems}
 * around the calls of the checks, and closed otherwise: a check that opens a file at another time, such as in
 * {@code appliesTo}, gets an {@link IOException}.
 */
final class ItemAccess {

    private CheckStore.ItemStreams streams;

    /** Opens the bytes to the checks, until {@link #end()}. */
    void begin(CheckStore.ItemStreams items) {
        this.streams = items;
    }

    void end() {
        this.streams = null;
    }

    InputStream open(String heldPath, String itemName) throws IOException {
        if (streams == null) {
            throw new IOException("The bytes of " + heldPath + "/" + itemName
                    + " are read only while the run hands the file to a check");
        }
        try {
            return streams.open(itemName);
        } catch (QuarantineStoreException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
