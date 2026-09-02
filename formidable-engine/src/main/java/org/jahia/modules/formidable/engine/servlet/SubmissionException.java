package org.jahia.modules.formidable.engine.servlet;

/**
 * Thrown by any step in {@link FormSubmissionPipeline} when the submission must be rejected.
 * Carries an {@link ErrorCode} (returned to the client) and an internal message (logged only).
 */
final class SubmissionException extends Exception {

    final ErrorCode errorCode;
    final int actionsCompleted;
    final int actionsTotal;
    /** 0 = none: the error code's own status applies. */
    private final int httpStatusOverride;

    SubmissionException(ErrorCode errorCode, String internalMessage) {
        this(errorCode, internalMessage, -1, -1, null);
    }

    SubmissionException(ErrorCode errorCode, String internalMessage, Throwable cause) {
        this(errorCode, internalMessage, -1, -1, cause);
    }

    SubmissionException(ErrorCode errorCode, String internalMessage, int actionsCompleted, int actionsTotal) {
        this(errorCode, internalMessage, actionsCompleted, actionsTotal, null);
    }

    SubmissionException(
            ErrorCode errorCode,
            String internalMessage,
            int actionsCompleted,
            int actionsTotal,
            Throwable cause
    ) {
        this(errorCode, internalMessage, actionsCompleted, actionsTotal, cause, 0);
    }

    /**
     * @param httpStatusOverride response status to use instead of the error code's own —
     *                           the status a {@link org.jahia.modules.formidable.engine.api.FormActionException}
     *                           chose, which the SPI promises is forwarded to the client
     */
    SubmissionException(
            ErrorCode errorCode,
            String internalMessage,
            int actionsCompleted,
            int actionsTotal,
            Throwable cause,
            int httpStatusOverride
    ) {
        super(internalMessage, cause);
        this.errorCode = errorCode;
        this.actionsCompleted = actionsCompleted;
        this.actionsTotal = actionsTotal;
        this.httpStatusOverride = httpStatusOverride;
    }

    int httpStatus() {
        return httpStatusOverride > 0 ? httpStatusOverride : errorCode.httpStatus;
    }

    boolean hasActionProgress() {
        return actionsCompleted >= 0 && actionsTotal > 0;
    }
}
