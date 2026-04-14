package org.jahia.modules.formidable.engine.actions;

import javax.servlet.http.HttpServletResponse;

/**
 * Thrown by a {@link FormAction} to signal a processing failure.
 * The HTTP status code is forwarded to the client as the response status.
 */
public class FormActionException extends Exception {

    private final int httpStatus;

    public FormActionException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public FormActionException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP status to return to the client (e.g. 400, 500). */
    public int getHttpStatus() {
        return httpStatus;
    }

    public static FormActionException serverError(String message) {
        return new FormActionException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public static FormActionException badRequest(String message) {
        return new FormActionException(message, HttpServletResponse.SC_BAD_REQUEST);
    }
}

