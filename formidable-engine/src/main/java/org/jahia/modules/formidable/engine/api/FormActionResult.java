package org.jahia.modules.formidable.engine.api;

import java.util.Optional;

/**
 * What a {@link ContextualFormAction} tells the browser once it succeeded. Immutable.
 *
 * <p>The servlet adds it to the JSON body of the accepted submission: {@code redirect} (the island navigates
 * there once it showed the success message) and {@code userMessage} (shown instead of the form's own submission
 * message). When several actions answer, the first redirect and the first message win, in action-list order.</p>
 *
 * @param redirectPath a same-origin relative path ({@link RedirectTargets#sameOriginPath}); anything else is
 *                     dropped with a warning by the pipeline, never sent to the browser. {@code null}: no redirect
 * @param message      the text the visitor reads, plain, already translated in the submission's locale; the
 *                     servlet escapes it. {@code null}: the form's own submission message stands
 */
public record FormActionResult(String redirectPath, String message) {

    private static final FormActionResult NONE = new FormActionResult(null, null);

    /** No instruction for the browser: the submission is accepted as any other. */
    public static FormActionResult none() {
        return NONE;
    }

    /** Send the visitor to a same-origin path once the success message was shown. */
    public static FormActionResult redirect(String path) {
        return new FormActionResult(path, null);
    }

    /** Show this text instead of the form's submission message. */
    public static FormActionResult message(String text) {
        return new FormActionResult(null, text);
    }

    /** The same result, plus a redirect. */
    public FormActionResult withRedirect(String path) {
        return new FormActionResult(path, message);
    }

    /** The same result, plus a message. */
    public FormActionResult withMessage(String text) {
        return new FormActionResult(redirectPath, text);
    }

    /** The redirect, when the action asked for one. */
    public Optional<String> redirect() {
        return Optional.ofNullable(redirectPath);
    }

    /** True when the action has nothing to tell the browser. */
    public boolean isEmpty() {
        return redirectPath == null && message == null;
    }
}
