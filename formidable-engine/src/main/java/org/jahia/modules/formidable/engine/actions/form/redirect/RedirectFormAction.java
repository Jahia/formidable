package org.jahia.modules.formidable.engine.actions.form.redirect;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.PageLinks;
import org.jahia.modules.formidable.engine.api.RedirectTargets;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * Sends the visitor to a page or to a web address once the submission is accepted, instead of the submission
 * message: the action of {@code fmdb:redirectAction} (docs/architecture/redirect-action.md).
 *
 * <p>The target is either a page picked in the Content Editor ({@code page}, a weak reference: it follows a move or a
 * rename; its URL is built from the node in the visitor's language, through the platform's URL rewriting) or a URL
 * typed by the contributor ({@code url}, per language): a path of this server, returned with
 * {@link FormActionResult#redirect}, or an absolute {@code http}/{@code https} URL, returned with
 * {@link FormActionResult#redirectToUrl}, which a value read from the configuration may use. The action fails
 * nothing: a missing, unpublished or unusable target is logged and the submission is accepted without a
 * redirect.</p>
 *
 * <p>Threading: a singleton with no state; everything comes from the {@link FormActionContext}. Writes nothing, so
 * the type carries {@code fmdbmix:readOnlyCompatibleAction}.</p>
 */
@Component(service = FormAction.class)
public class RedirectFormAction implements ContextualFormAction {

    private static final Logger log = LoggerFactory.getLogger(RedirectFormAction.class);

    static final String TARGET_PAGE = "page";
    static final String TARGET_URL = "url";

    /** How a page becomes a path: the platform's URL rewriting in production, a stub in the tests. */
    @FunctionalInterface
    interface PageUrls {
        Optional<String> urlOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response);
    }

    private final PageUrls pageUrls;

    public RedirectFormAction() {
        this(PageLinks::urlOf);
    }

    RedirectFormAction(PageUrls pageUrls) {
        this.pageUrls = pageUrls;
    }

    @Override
    public String getNodeType() {
        return FmdbNodeType.REDIRECT_ACTION;
    }

    @Override
    public FormActionResult execute(FormActionContext context) {
        JCRNodeWrapper action = context.actionNode();
        try {
            String type = action.hasProperty(FmdbProperty.REDIRECT_TARGET_TYPE)
                    ? action.getProperty(FmdbProperty.REDIRECT_TARGET_TYPE).getString() : TARGET_PAGE;
            Optional<String> target = TARGET_URL.equals(type) ? url(action) : page(context, action);
            return target.map(RedirectFormAction::toResult).orElseGet(FormActionResult::none);
        } catch (RepositoryException | RuntimeException e) {
            // never fail a submission over where to send the visitor next
            log.warn("[RedirectFormAction] The redirect target could not be resolved: the submission is accepted without a redirect.", e);
            return FormActionResult.none();
        }
    }

    private static Optional<String> url(JCRNodeWrapper action) throws RepositoryException {
        // i18n: the action node came from a session in the submission's locale
        if (!action.hasProperty(FmdbProperty.REDIRECT_URL)) {
            log.warn("[RedirectFormAction] The action has no URL in this language: no redirect.");
            return Optional.empty();
        }
        String value = action.getProperty(FmdbProperty.REDIRECT_URL).getString();
        Optional<String> url = RedirectTargets.sameOriginPath(value).or(() -> RedirectTargets.httpUrl(value));
        if (url.isEmpty()) {
            log.warn("[RedirectFormAction] The configured URL is neither a path of this server nor an http(s) URL: no redirect.");
        }
        return url;
    }

    /** A path is a redirect of the first kind; anything else here is an http(s) URL read from the configuration. */
    private static FormActionResult toResult(String target) {
        return target.startsWith("/") ? FormActionResult.redirect(target) : FormActionResult.redirectToUrl(target);
    }

    private Optional<String> page(FormActionContext context, JCRNodeWrapper action) throws RepositoryException {
        if (!action.hasProperty(FmdbProperty.REDIRECT_PAGE)) {
            log.warn("[RedirectFormAction] The action has no page: no redirect.");
            return Optional.empty();
        }
        // read in live, in the submission's locale (the action node came from that session): an unpublished or
        // deleted page does not resolve, and the visitor is not sent to a page they cannot see
        JCRNodeWrapper page = (JCRNodeWrapper) action.getProperty(FmdbProperty.REDIRECT_PAGE).getNode();
        return pageUrls.urlOf(page, context.request(), context.response());
    }
}
