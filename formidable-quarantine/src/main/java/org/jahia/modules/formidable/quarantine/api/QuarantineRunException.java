package org.jahia.modules.formidable.quarantine.api;

/**
 * A pass of the run that stopped because the quarantine tree of the site could not be read. The pass may stop after it
 * processed, and maybe released, the submissions of earlier forms: those keep their new state. Its message names no
 * value. Its subclass {@link QuarantineRunInProgressException} is a pass that did not start.
 */
public class QuarantineRunException extends Exception {

    private static final long serialVersionUID = 1L;

    public QuarantineRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
