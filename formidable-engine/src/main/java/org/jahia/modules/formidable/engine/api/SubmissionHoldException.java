package org.jahia.modules.formidable.engine.api;

/**
 * A {@link SubmissionHold} could not store a submission. The message is logged, never sent to the visitor, and it
 * names no value of the submission.
 */
public class SubmissionHoldException extends Exception {

    private static final long serialVersionUID = 1L;

    public SubmissionHoldException(String message) {
        super(message);
    }

    public SubmissionHoldException(String message, Throwable cause) {
        super(message, cause);
    }
}
