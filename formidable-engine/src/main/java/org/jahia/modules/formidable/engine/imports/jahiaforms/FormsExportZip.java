package org.jahia.modules.formidable.engine.imports.jahiaforms;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The zip a Jahia "Export Zip with live content" gives for a node: a {@code repository.xml}, a
 * {@code live-repository.xml} when live content was asked for, and the binaries of the files under the
 * node. Jahia writes a binary at {@code live-content/<path of the file node>/<file name>} (or
 * {@code content/…} for the edit workspace), the path being relative to the parent of the exported node,
 * {@code formFactory/results/…/cv/cv.pdf/cv.pdf} ({@code DocumentViewExporter.buildBinaryPathInZip}).
 * <p>
 * The zip is opened as a {@link ZipFile}, which seeks to an entry through the central directory instead
 * of inflating its way there, so the structure, the submissions and each file cost one read each. The
 * file comes from an upload: no entry is read past the bound, a zip bomb stops there.
 */
final class FormsExportZip implements Closeable {

    static final String LIVE_XML = "live-repository.xml";
    static final String XML = "repository.xml";
    private static final String LIVE_CONTENT = "live-content/";
    private static final String CONTENT = "content/";
    /** 512 MB: above the largest export the dialog accepts, with room for the binaries it unpacks. */
    static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;

    private final ZipFile zip;
    private final long maxEntryBytes;

    /** @param file the uploaded export, copied to a local file by the caller, who deletes it after {@link #close()} */
    FormsExportZip(Path file) throws IOException {
        this(file, MAX_ENTRY_BYTES);
    }

    FormsExportZip(Path file, long maxEntryBytes) throws IOException {
        this.zip = new ZipFile(file.toFile());
        this.maxEntryBytes = maxEntryBytes;
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

    boolean hasLiveXml() {
        return zip.getEntry(LIVE_XML) != null;
    }

    /**
     * The binary of a file node, at the entry Jahia writes for it, under {@code live-content/} first and
     * {@code content/} otherwise. Null when the zip holds neither.
     *
     * @param nodePath the path of the {@code jnt:file} node in the export, {@code formFactory/results/…/cv.pdf}
     */
    InputStream openBinary(String nodePath, String fileName) throws IOException {
        String tail = nodePath + "/" + fileName;
        InputStream live = open(LIVE_CONTENT + tail);
        return live != null ? live : open(CONTENT + tail);
    }

    /** Opens the entry with that exact name, or returns null. The caller closes the stream. */
    InputStream open(String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        return new BoundedStream(zip.getInputStream(entry), maxEntryBytes);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /** An entry stream that refuses to read past the bound. */
    private static final class BoundedStream extends InputStream {
        private final InputStream in;
        private final long limit;
        private long read;

        BoundedStream(InputStream in, long limit) {
            this.in = in;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = in.read(buffer, off, len);
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
            in.close();
        }
    }
}
