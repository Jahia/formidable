package org.jahia.modules.formidable.quarantine.api;

/** A run that could not start: the quarantine tree of the site could not be read. Its message names no value. */
public class QuarantineRunException extends Exception {

    private static final long serialVersionUID = 1L;

    public QuarantineRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
