package org.jahia.modules.formidable.engine.api;

import org.jahia.bin.Jahia;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.seo.urlrewrite.UrlRewriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * The public URL of a page, as the platform prints it in a rendered page: {@code node.getUrl()} then the outbound
 * URL rewriting (vanity URLs, the {@code /cms/render/live} prefix and the site key dropped per the server-name rules).
 * Never a hand-built {@code /<lang>/<path>.html}, which is a 404 on every site but the default one.
 *
 * <p>Stateless. For any action that must send the visitor to a page chosen in the Content Editor (the redirect
 * and sign-in actions of formidable-extended-actions). The answer is always a same-origin path ({@link RedirectTargets#sameOriginPath}) or nothing.</p>
 */
public final class PageLinks {

    private static final Logger log = LoggerFactory.getLogger(PageLinks.class);

    /** The outbound rewriting, a seam: the production one asks the platform's {@code UrlRewriteService}. */
    @FunctionalInterface
    public interface Rewriter {
        /** @return the rewritten URL, possibly relative to the servlet context path */
        String rewrite(String url, HttpServletRequest request, HttpServletResponse response) throws Exception;
    }

    private PageLinks() {
    }

    /**
     * @param page     a page read in the workspace the visitor sees ({@code live}), in the submission's locale
     * @param request  the request being served
     * @param response the response being served
     * @return the page's same-origin path, or empty when the page has no usable URL (a warning is logged)
     */
    public static Optional<String> pathOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response) {
        return pathOf(page, request, response, PageLinks::platformRewriter, Jahia.getContextPath());
    }

    /** As {@link #pathOf(JCRNodeWrapper, HttpServletRequest, HttpServletResponse)} with the rewriting and the platform's context path chosen by the caller. */
    public static Optional<String> pathOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response,
                                          Rewriter rewriter, String platformContextPath) {
        try {
            String rewritten = rewriter.rewrite(page.getUrl(), request, response);
            String url = RedirectTargets.swapContextPath(rewritten, request.getContextPath(), platformContextPath);
            Optional<String> path = RedirectTargets.sameOriginPath(url);
            if (path.isEmpty()) {
                // an absolute URL on this server (a vanity URL of a site with its own server name) is reduced to its path
                path = RedirectTargets.pathOfReferer(url, request.getServerName());
            }
            if (path.isEmpty()) {
                log.warn("[PageLinks] The URL of '{}' is not a path of this server: no redirect.", page.getPath());
            }
            return path;
        } catch (RepositoryException e) {
            log.warn("[PageLinks] The URL of a page could not be read: no redirect.", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[PageLinks] The URL of a page could not be rewritten: no redirect.", e);
            return Optional.empty();
        }
    }

    private static String platformRewriter(String url, HttpServletRequest request, HttpServletResponse response) throws Exception {
        UrlRewriteService service = (UrlRewriteService) SpringContextSingleton.getBean("UrlRewriteService");
        return service == null ? url : service.rewriteOutbound(url, request, response);
    }
}
