package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The zip a Jahia "Export Zip with live content" gives for a node: a {@code repository.xml}, a
 * {@code live-repository.xml} when live content was asked for, and the binaries of the files under the
 * node, each at an entry named after the node's path. The zip comes from an upload, so it is read as a
 * stream, entry by entry, and no entry is read past {@link #MAX_ENTRY_BYTES}: a zip bomb stops there.
 */
final class FormsExportZip {

    static final String LIVE_XML = "live-repository.xml";
    static final String XML = "repository.xml";
    /** 512 MB: above the largest export the dialog accepts, with room for the binaries it unpacks. */
    static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;

    private final Function<String, InputStream> opener;

    /**
     * @param opener opens the zip anew each time, from the upload stored in the repository: the stream
     *               is read once per entry looked up, and the structure and the submissions are two reads
     */
    FormsExportZip(Function<String, InputStream> opener) {
        this.opener = opener;
    }

    /** Opens the XML that holds the results: {@code live-repository.xml} when present, else {@code repository.xml}. */
    InputStream openRepositoryXml() throws IOException, FormsExportException {
        InputStream live = open(LIVE_XML);
        if (live != null) {
            return live;
        }
        InputStream edit = open(XML);
        if (edit == null) {
            throw new FormsExportException("The file holds no repository.xml: it is not a Jahia export. "
                    + FormsExportException.PROCEDURE);
        }
        return edit;
    }

    boolean hasLiveXml() throws IOException {
        try (InputStream live = open(LIVE_XML)) {
            return live != null;
        }
    }

    /**
     * The binary of a file node, found at the entry whose name ends with the node's path in the export
     * ({@code …/formFactory/results/…/<file>}): the export prefixes it with the workspace folder and the
     * site path, which the XML does not name. Null when the zip holds no such entry.
     */
    InputStream openBinary(String nodePath) throws IOException {
        return openMatching(name -> name.endsWith("/" + nodePath) || name.equals(nodePath));
    }

    /** Opens the entry with that exact name, or returns null. The caller closes the stream. */
    InputStream open(String entryName) throws IOException {
        return openMatching(name -> name.equals(entryName));
    }

    private InputStream openMatching(Predicate<String> entryName) throws IOException {
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(opener.apply("zip")));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (!entry.isDirectory() && entryName.test(entry.getName())) {
                return new BoundedEntryStream(zip, MAX_ENTRY_BYTES);
            }
        }
        zip.close();
        return null;
    }

    /** The current entry of a zip stream, bounded in size, and closing the zip when it is closed. */
    private static final class BoundedEntryStream extends InputStream {
        private final ZipInputStream zip;
        private final long limit;
        private long read;

        BoundedEntryStream(ZipInputStream zip, long limit) {
            this.zip = zip;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = zip.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = zip.read(buffer, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read > limit) {
                throw new IOException("An entry of the export is larger than " + limit + " bytes");
            }
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }
}
