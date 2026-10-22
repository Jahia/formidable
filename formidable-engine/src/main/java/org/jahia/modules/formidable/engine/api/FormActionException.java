package org.jahia.modules.formidable.engine.api;

import javax.servlet.http.HttpServletResponse;

/**
 * Thrown by a {@link FormAction} to signal a processing failure.
 * The HTTP status code is forwarded to the client as the response status, with two
 * reservations: only an error status is honoured (a value outside 400-599 falls back
 * to the pipeline's generic action-failure status), and a failure caused by the
 * platform's read-only mode always answers 503 with the maintenance code, whatever
 * status the action chose. The response body always carries the opaque error code,
 * never this exception's message.
 *
 * <p>An action that wants the visitor to read something throws {@link #refusal}: the error code becomes
 * {@code FMDB-018} and the body carries the visitor message, escaped, in {@code userMessage}. That text is the
 * action's own, already translated ({@link VisitorMessages}), and it must be generic: it is shown to anyone who can
 * submit the form, so it never tells which of two causes made the action fail.</p>
 */
public class FormActionException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    /** What the visitor reads; {@code null} when the failure is opaque (FMDB-008). */
    private final String visitorMessage;

    public FormActionException(String message, int httpStatus) {
        this(message, httpStatus, null, null);
    }

    public FormActionException(String message, int httpStatus, Throwable cause) {
        this(message, httpStatus, cause, null);
    }

    private FormActionException(String message, int httpStatus, Throwable cause, String visitorMessage) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.visitorMessage = visitorMessage;
    }

    /**
     * A refusal the visitor reads on the form ({@code FMDB-018}).
     *
     * @param httpStatus     the response status, an error status (400-599); 422 is the usual choice, 429 for a rate limit
     * @param internalMessage what the server log says, never sent to the browser; never put a secret in it
     * @param visitorMessage the plain text the visitor reads, already translated; the pipeline escapes it
     */
    public static FormActionException refusal(int httpStatus, String internalMessage, String visitorMessage) {
        return new FormActionException(internalMessage, httpStatus, null, visitorMessage);
    }

    /** As {@link #refusal(int, String, String)}, keeping the cause for the log. */
    public static FormActionException refusal(int httpStatus, String internalMessage, String visitorMessage, Throwable cause) {
        return new FormActionException(internalMessage, httpStatus, cause, visitorMessage);
    }

    /** The text the visitor reads, or {@code null} when the failure is opaque. */
    public String getVisitorMessage() {
        return visitorMessage;
    }

    /** True when the visitor is to be told something ({@link #refusal}). */
    public boolean hasVisitorMessage() {
        return visitorMessage != null && !visitorMessage.isBlank();
    }

    /** HTTP status to return to the client (e.g. 400, 500). */
    public int getHttpStatus() {
        return httpStatus;
    }

    public static FormActionException serverError(String message) {
        return new FormActionException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public static FormActionException serverError(String message, Throwable cause) {
        return new FormActionException(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, cause);
    }

    public static FormActionException badRequest(String message) {
        return new FormActionException(message, HttpServletResponse.SC_BAD_REQUEST);
    }
}
