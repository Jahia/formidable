package org.jahia.modules.formidable.quarantine.store;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The SHA-256 of bytes, as lower-case hexadecimal: what an item records at intake, and the release compares. */
public final class Sha256 {

    private Sha256() {
    }

    /** The size of the buffer that hashes a stream: the stream is never read whole into memory. */
    private static final int BUFFER = 64 * 1024;

    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    /** The SHA-256 of the bytes left in the stream, read a buffer at a time. The caller closes the stream. */
    public static String hex(InputStream in) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[BUFFER];
        for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // every Java platform ships SHA-256
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
