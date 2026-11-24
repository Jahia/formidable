package org.jahia.modules.formidable.engine.api;

/**
 * An action that runs after release asked the HTTP request or the response for something they do not have. After
 * release the request answers the three values of {@link SubmissionOrigin}, and the response answers nothing.
 */
public class DetachedRequestException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public DetachedRequestException(String message) {
        super(message);
    }
}
