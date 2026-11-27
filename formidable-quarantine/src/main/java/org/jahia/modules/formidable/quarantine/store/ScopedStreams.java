package org.jahia.modules.formidable.quarantine.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The streams of the items of one held submission while the session that reads them is open. {@link #close()} ends
 * every stream it opened: a later read of one throws, and so does a later {@link #open}. A check can thus read the
 * bytes only while the run holds the session, whatever it keeps.
 */
public final class ScopedStreams implements CheckStore.ItemStreams, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ScopedStreams.class);

    private final CheckStore.ItemStreams source;
    private final List<Scoped> opened = new ArrayList<>();
    private boolean closed;

    /** @param source opens the stream of an item in the session; this class ends it */
    public ScopedStreams(CheckStore.ItemStreams source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public InputStream open(String itemName) throws QuarantineStoreException {
        if (closed) {
            throw new QuarantineStoreException("The session that read the items is closed: item " + itemName);
        }
        Scoped stream = new Scoped(source.open(itemName));
        opened.add(stream);
        return stream;
    }

    /** Ends every stream this scope opened. */
    @Override
    public void close() {
        closed = true;
        for (Scoped stream : opened) {
            stream.end();
        }
        opened.clear();
    }

    /** A stream that throws once its scope ended. */
    private static final class Scoped extends FilterInputStream {

        private boolean ended;

        Scoped(InputStream in) {
            super(in);
        }

        void end() {
            ended = true;
            try {
                in.close();
            } catch (IOException e) {
                // the session closes next and frees the binary all the same
                log.debug("Could not close the stream of an item: {}", e.getMessage());
            }
        }

        private void ensureOpen() throws IOException {
            if (ended) {
                throw new IOException("The session that read this item is closed");
            }
        }

        @Override
        public int read() throws IOException {
            ensureOpen();
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ensureOpen();
            return super.read(b, off, len);
        }

        // the reads of a whole stream go to the stream of the store, as each read does, and not through a buffer of
        // InputStream: a test store that refuses them then sees them
        @Override
        public byte[] readAllBytes() throws IOException {
            ensureOpen();
            return in.readAllBytes();
        }

        @Override
        public byte[] readNBytes(int len) throws IOException {
            ensureOpen();
            return in.readNBytes(len);
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            ensureOpen();
            return in.transferTo(out);
        }

        @Override
        public long skip(long n) throws IOException {
            ensureOpen();
            return super.skip(n);
        }

        @Override
        public int available() throws IOException {
            ensureOpen();
            return super.available();
        }

        @Override
        public void close() throws IOException {
            if (!ended) {
                super.close();
            }
        }
    }
}
