package org.jahia.modules.formidable.engine.servlet;

/**
 * Thrown by any step in {@link FormSubmissionPipeline} when the submission must be rejected.
 * Carries an {@link ErrorCode} (returned to the client) and an internal message (logged only).
 */
final class SubmissionException extends Exception {

    final ErrorCode errorCode;

    SubmissionException(ErrorCode errorCode, String internalMessage) {
        super(internalMessage);
        this.errorCode = errorCode;
    }

    int httpStatus() {
        return errorCode.httpStatus;
    }
}

