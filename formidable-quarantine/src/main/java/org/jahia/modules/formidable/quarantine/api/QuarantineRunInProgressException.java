package org.jahia.modules.formidable.quarantine.api;

/**
 * A pass of the run that did not start, because another run holds the lock of the run of the site, on this server or
 * on another one. Nothing was read and nothing moved: the caller retries later.
 */
public class QuarantineRunInProgressException extends QuarantineRunException {

    private static final long serialVersionUID = 1L;

    public QuarantineRunInProgressException(String message, Throwable cause) {
        super(message, cause);
    }
}
