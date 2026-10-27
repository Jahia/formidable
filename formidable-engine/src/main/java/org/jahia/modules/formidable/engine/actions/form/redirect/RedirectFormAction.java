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
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Optional;

/**
 * Sends the visitor to a page or to a web address once the submission is accepted, instead of the submission
 * message: the action of {@code fmdb:redirectAction} (docs/architecture/redirect-action.md).
 *
 * <p>The target is either a page picked in the Content Editor ({@code page}, a weak reference: it follows a move or a
 * rename; its URL is built from the node in the visitor's language, through the platform's URL rewriting) or a URL
 * typed by the contributor ({@code url}, per language, falling back to the site's default language, then to the first
 * language that has one): an absolute {@code http}/{@code https} URL, returned with
 * {@link FormActionResult#redirectToUrl}, which a value read from the configuration may use. A page of the site is the
 * page target's: a typed path would miss a move, the visitor's language and the context path. The action fails
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
            if (TARGET_URL.equals(type)) {
                return url(action).map(FormActionResult::redirectToUrl).orElseGet(FormActionResult::none);
            }
            return page(context, action).map(RedirectFormAction::toResult).orElseGet(FormActionResult::none);
        } catch (RepositoryException | RuntimeException e) {
            // never fail a submission over where to send the visitor next
            log.warn("[RedirectFormAction] The redirect target could not be resolved: the submission is accepted without a redirect.", e);
            return FormActionResult.none();
        }
    }

    private static Optional<String> url(JCRNodeWrapper action) throws RepositoryException {
        // i18n: the action node came from a session in the submission's locale
        String value = action.hasProperty(FmdbProperty.REDIRECT_URL) ? nonBlank(action.getProperty(FmdbProperty.REDIRECT_URL).getString()) : null;
        if (value == null) {
            value = fallbackUrl(action);
        }
        if (value == null) {
            log.warn("[RedirectFormAction] The action {} has no URL in any language: no redirect.", action.getPath());
            return Optional.empty();
        }
        Optional<String> url = RedirectTargets.httpUrl(value);
        if (url.isEmpty()) {
            log.warn("[RedirectFormAction] The URL of the action {} is not an absolute http(s) URL: no redirect.", action.getPath());
        }
        return url;
    }

    /**
     * The URL when the submission's language has none: the one of the site's default language, else the first language
     * that has one, so that a migrated form keeps redirecting in every language (Jahia Forms took the first translation
     * it found).
     */
    private static String fallbackUrl(JCRNodeWrapper action) throws RepositoryException {
        JCRSiteNode site = action.getResolveSite();
        if (site != null && site.getDefaultLanguage() != null) {
            Locale defaultLocale = LanguageCodeConverters.languageCodeToLocale(site.getDefaultLanguage());
            if (action.hasI18N(defaultLocale, false)) {
                String value = urlOf(action.getI18N(defaultLocale, false));
                if (value != null) {
                    return value;
                }
            }
        }
        NodeIterator translations = action.getI18Ns();
        while (translations.hasNext()) {
            String value = urlOf(translations.nextNode());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String urlOf(Node translation) throws RepositoryException {
        return translation.hasProperty(FmdbProperty.REDIRECT_URL) ? nonBlank(translation.getProperty(FmdbProperty.REDIRECT_URL).getString()) : null;
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** The URL of a page: a path of this server, or the absolute URL of a page on another server name. */
    private static FormActionResult toResult(String target) {
        return target.startsWith("/") ? FormActionResult.redirectTo(target) : FormActionResult.redirectToUrl(target);
    }

    private Optional<String> page(FormActionContext context, JCRNodeWrapper action) throws RepositoryException {
        if (!action.hasProperty(FmdbProperty.REDIRECT_PAGE)) {
            log.warn("[RedirectFormAction] The action {} has no page: no redirect.", action.getPath());
            return Optional.empty();
        }
        // read in live, in the submission's locale (the action node came from that session): an unpublished or
        // deleted page does not resolve. The session is the system's, so a page the visitor may not read resolves:
        // its access rights apply when the browser loads it, as for any link
        JCRNodeWrapper page;
        try {
            page = (JCRNodeWrapper) action.getProperty(FmdbProperty.REDIRECT_PAGE).getNode();
        } catch (ItemNotFoundException e) {
            log.warn("[RedirectFormAction] The page of the action {} is not published or was deleted: no redirect.", action.getPath());
            return Optional.empty();
        }
        return pageUrls.urlOf(page, context.request(), context.response());
    }
}
