package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.test.modules.formidable.quarantine.check.QuarantinedFile;
import org.jahia.test.modules.formidable.quarantine.store.CheckStore;
import org.jahia.test.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * One item of a held submission, as a check sees it: the metadata the store recorded at intake, and its bytes read
 * on demand through {@link CheckStore#openItem}. {@link #sha256()} is the SHA-256 of intake, so an outcome is recorded
 * for the bytes the visitor sent.
 */
final class ItemFile implements QuarantinedFile {

    private final String heldPath;
    private final ItemRecord item;
    private final CheckStore checkStore;

    ItemFile(String heldPath, ItemRecord item, CheckStore checkStore) {
        this.heldPath = Objects.requireNonNull(heldPath, "heldPath");
        this.item = Objects.requireNonNull(item, "item");
        this.checkStore = Objects.requireNonNull(checkStore, "checkStore");
    }

    /** @return the name of the item, under which its outcomes are recorded */
    String itemName() {
        return item.name();
    }

    @Override
    public String sha256() {
        return item.sha256();
    }

    @Override
    public long size() {
        return item.size();
    }

    @Override
    public String mimeType() {
        return item.mimeType();
    }

    @Override
    public String fileName() {
        return item.fileName();
    }

    @Override
    public InputStream open() throws IOException {
        try {
            return checkStore.openItem(heldPath, item.name());
        } catch (QuarantineStoreException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** What a log line may say: never the bytes. */
    @Override
    public String toString() {
        return "ItemFile[" + heldPath + "/" + item.name() + "]";
    }
}
