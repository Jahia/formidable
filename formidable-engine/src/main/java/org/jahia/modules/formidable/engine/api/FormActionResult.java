package org.jahia.modules.formidable.engine.api;

import java.util.Optional;

/**
 * What a {@link ContextualFormAction} tells the browser once it succeeded. Immutable.
 *
 * <p>The servlet adds it to the JSON body of the accepted submission: {@code redirect} (the island navigates
 * there) and {@code userMessage} (shown instead of the form's own submission message). When several actions answer,
 * the first redirect, of either kind, and the first message win, in action-list order. A message beside a redirect is
 * not guaranteed to be shown: do not rely on the visitor reading both.</p>
 *
 * <p>A redirect has two kinds, each held to its own rule by the pipeline; a target its rule refuses is dropped with a
 * warning, never sent to the browser:</p>
 * <ul>
 *   <li>{@link #redirect(String)}: a path of this server ({@link RedirectTargets#sameOriginPath}), whatever its source,
 *       a value computed from the request included;</li>
 *   <li>{@link #redirectToUrl(String)}: an absolute {@code http} or {@code https} URL ({@link RedirectTargets#httpUrl}),
 *       for a URL read from the configuration only (an action node, an OSGi configuration), never from the request:
 *       that would be an open redirect.</li>
 * </ul>
 *
 * @param redirectPath a path of this server. {@code null}: no redirect of this kind
 * @param redirectUrl  an absolute {@code http} or {@code https} URL read from the configuration. {@code null}: no
 *                     redirect of this kind
 * @param message      the text the visitor reads, plain, already translated in the submission's locale; the
 *                     servlet escapes it. {@code null}: the form's own submission message stands
 */
public record FormActionResult(String redirectPath, String redirectUrl, String message) {

    private static final FormActionResult NONE = new FormActionResult(null, null, null);

    /** No instruction for the browser: the submission is accepted as any other. */
    public static FormActionResult none() {
        return NONE;
    }

    /** Send the visitor to a path of this server. */
    public static FormActionResult redirect(String path) {
        return new FormActionResult(path, null, null);
    }

    /** Send the visitor to an absolute {@code http} or {@code https} URL read from the configuration, never from the request. */
    public static FormActionResult redirectToUrl(String url) {
        return new FormActionResult(null, url, null);
    }

    /** Show this text instead of the form's submission message. */
    public static FormActionResult message(String text) {
        return new FormActionResult(null, null, text);
    }

    /** The same result, its redirect replaced by a path of this server. */
    public FormActionResult withRedirect(String path) {
        return new FormActionResult(path, null, message);
    }

    /** The same result, its redirect replaced by a URL read from the configuration. */
    public FormActionResult withRedirectToUrl(String url) {
        return new FormActionResult(null, url, message);
    }

    /** The same result, plus a message. */
    public FormActionResult withMessage(String text) {
        return new FormActionResult(redirectPath, redirectUrl, text);
    }

    /** The redirect target, of either kind, when the action asked for one. */
    public Optional<String> redirect() {
        return Optional.ofNullable(redirectPath != null ? redirectPath : redirectUrl);
    }

    /** True when the action has nothing to tell the browser. */
    public boolean isEmpty() {
        return redirectPath == null && redirectUrl == null && message == null;
    }
}
