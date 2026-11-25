package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.store.ItemRecord;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * One item of a held submission, as a check sees it: the metadata the store recorded at intake, and a stream over the
 * binary of the repository, open only while the run hands the file to a check ({@link ItemAccess}).
 * {@link #sha256()} is the SHA-256 of intake, so an outcome is recorded for the bytes the visitor sent.
 */
final class ItemFile implements QuarantinedFile {

    private final String heldPath;
    private final ItemRecord item;
    private final ItemAccess access;

    ItemFile(String heldPath, ItemRecord item, ItemAccess access) {
        this.heldPath = Objects.requireNonNull(heldPath, "heldPath");
        this.item = Objects.requireNonNull(item, "item");
        this.access = Objects.requireNonNull(access, "access");
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
        return access.open(heldPath, item.name());
    }

    /** What a log line may say: never the bytes. */
    @Override
    public String toString() {
        return "ItemFile[" + heldPath + "/" + item.name() + "]";
    }
}
