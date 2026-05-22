package org.jahia.modules.formidable.engine.servlet;

/**
 * Thrown by any step in {@link FormSubmissionPipeline} when the submission must be rejected.
 * Carries an {@link ErrorCode} (returned to the client) and an internal message (logged only).
 */
final class SubmissionException extends Exception {

    final ErrorCode errorCode;
    final int actionsCompleted;
    final int actionsTotal;

    SubmissionException(ErrorCode errorCode, String internalMessage) {
        this(errorCode, internalMessage, -1, -1);
    }

    SubmissionException(ErrorCode errorCode, String internalMessage, int actionsCompleted, int actionsTotal) {
        super(internalMessage);
        this.errorCode = errorCode;
        this.actionsCompleted = actionsCompleted;
        this.actionsTotal = actionsTotal;
    }

    int httpStatus() {
        return errorCode.httpStatus;
    }

    boolean hasActionProgress() {
        return actionsCompleted >= 0 && actionsTotal > 0;
    }
}

