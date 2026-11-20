package org.jahia.test.modules.formidable.quarantine.store;

/** The quarantine store could not read or write. The message names forms, paths and fields, never a value. */
public class QuarantineStoreException extends Exception {

    private static final long serialVersionUID = 1L;

    public QuarantineStoreException(String message) {
        super(message);
    }

    public QuarantineStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
