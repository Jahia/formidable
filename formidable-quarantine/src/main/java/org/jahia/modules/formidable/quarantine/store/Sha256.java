package org.jahia.modules.formidable.quarantine.store;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The SHA-256 of bytes, as lower-case hexadecimal: what an item records at intake, and the release compares. */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // every Java platform ships SHA-256
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
