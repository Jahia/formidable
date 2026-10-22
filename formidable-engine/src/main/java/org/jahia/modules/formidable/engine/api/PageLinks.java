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
 * <p>Stateless. Used by the redirect action and by any module that must send the visitor to a page chosen in the
 * Content Editor. The answer is a path of this server when the page is on the server the request reached, on the
 * platform's context path, never the request's ({@code <context>/modules}); the absolute {@code http}/{@code https} URL
 * the platform printed when it is a page of a site with another server name; or nothing. The page is read from the
 * configuration, so the second answer may go to {@link FormActionResult#redirectToUrl}.</p>
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
     * @return the page's URL, or empty when the page has no usable URL (a warning is logged)
     */
    public static Optional<String> urlOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response) {
        return urlOf(page, request, response, PageLinks::platformRewriter, Jahia.getContextPath());
    }

    /** As {@link #urlOf(JCRNodeWrapper, HttpServletRequest, HttpServletResponse)} with the rewriting and the platform's context path chosen by the caller. */
    public static Optional<String> urlOf(JCRNodeWrapper page, HttpServletRequest request, HttpServletResponse response,
                                         Rewriter rewriter, String platformContextPath) {
        try {
            String rewritten = rewriter.rewrite(page.getUrl(), request, response);
            String url = RedirectTargets.swapContextPath(rewritten, request.getContextPath(), platformContextPath);
            Optional<String> target = RedirectTargets.sameOriginPath(url);
            if (target.isEmpty()) {
                // an absolute URL on this server (a vanity URL of a site with its own server name) is reduced to its path
                target = RedirectTargets.pathOnServer(url, request.getServerName());
            }
            if (target.isEmpty()) {
                // a page of a site that answers on another server name
                target = RedirectTargets.httpUrl(url);
            }
            if (target.isEmpty()) {
                log.warn("[PageLinks] The URL of '{}' is neither a path nor an http(s) URL: no redirect.", page.getPath());
            }
            return target;
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
