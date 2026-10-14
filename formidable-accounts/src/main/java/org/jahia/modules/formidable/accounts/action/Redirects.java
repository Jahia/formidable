package org.jahia.modules.formidable.accounts.action;

import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.PageLinks;
import org.jahia.modules.formidable.engine.api.RedirectTargets;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * Where an action sends the visitor next: the page the contributor picked ({@code redirectPage}), or, ahead of it, the
 * place the visitor was going before the form ({@code redirectParameter}: the name of a query parameter of the form's
 * own page, as in {@code /login?redirect=/account}). Either way the answer is a same-origin path or nothing
 * ({@link RedirectTargets}): the parameter is the visitor's to set, so it is never trusted beyond that rule.
 *
 * <p>Stateless apart from its page-URL seam; thread-safe.</p>
 */
final class Redirects {

    /** How a page becomes a path: the platform's URL rewriting in production. */
    @FunctionalInterface
    interface PageUrls {
        Optional<String> pathOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response);
    }

    private static final Logger log = LoggerFactory.getLogger(Redirects.class);

    private final PageUrls pageUrls;

    Redirects() {
        this(PageLinks::pathOf);
    }

    Redirects(PageUrls pageUrls) {
        this.pageUrls = pageUrls;
    }

    /**
     * @param context     the submission
     * @param useReturn   whether the visitor is signed in, so that "where they were going" is a place they can reach
     * @return the path to send the visitor to, empty for none
     */
    Optional<String> resolve(FormActionContext context, boolean useReturn) {
        JCRNodeWrapper action = context.actionNode();
        if (useReturn) {
            String parameter = ActionProps.string(action, "redirectParameter");
            Optional<String> back = RedirectTargets.returnPath(context.request().getHeader("Referer"), context.request().getServerName(), parameter);
            if (back.isPresent()) {
                return back;
            }
        }
        return page(context, action, "redirectPage");
    }

    /**
     * @param context  the submission
     * @param property the weak-reference property of the action naming a page
     * @return the page's path, empty when the property is unset or the page does not resolve
     */
    Optional<String> page(FormActionContext context, JCRNodeWrapper action, String property) {
        try {
            if (!action.hasProperty(property)) {
                return Optional.empty();
            }
            JCRNodeWrapper page = (JCRNodeWrapper) action.getProperty(property).getNode();
            return pageUrls.pathOf(page, context.request(), context.response());
        } catch (RepositoryException | RuntimeException e) {
            // an unpublished or deleted page: the submission is accepted without it
            log.warn("[formidable-accounts] The page of '{}' could not be resolved: no redirect.", property);
            return Optional.empty();
        }
    }
}
