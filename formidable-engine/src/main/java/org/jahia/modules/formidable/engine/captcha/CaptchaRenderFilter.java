package org.jahia.modules.formidable.engine.captcha;

import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render filter that injects CAPTCHA front-end configuration as request attributes
 * when the current fmdb:form node has the fmdbmix:captcha mixin applied.
 *
 * Attributes set on HttpServletRequest (readable from the GraalVM JS server view):
 *   formidable.captcha.siteKey   - public site key for the widget
 *   formidable.captcha.scriptUrl - provider JavaScript API URL
 *
 * Runs before the view is rendered (priority 10, lower number = earlier execution).
 */
@Component(service = RenderFilter.class, immediate = true)
public class CaptchaRenderFilter extends AbstractFilter {

    private static final Logger log = LoggerFactory.getLogger(CaptchaRenderFilter.class);

    static final String ATTR_SITE_KEY   = "formidable.captcha.siteKey";
    static final String ATTR_SCRIPT_URL = "formidable.captcha.scriptUrl";

    private CaptchaConfigService captchaConfigService;

    @Reference
    public void setCaptchaConfigService(CaptchaConfigService captchaConfigService) {
        this.captchaConfigService = captchaConfigService;
    }

    @Activate
    public void activate() {
        setPriority(10);
        setApplyOnNodeTypes("fmdb:form");
        setApplyOnTemplateTypes("html");
        setSkipOnAjaxRequest(true);
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        try {
            if (resource == null || resource.getNode() == null) {
                return super.prepare(renderContext, resource, chain);
            }

            if (!resource.getNode().isNodeType("fmdbmix:captcha")) {
                return super.prepare(renderContext, resource, chain);
            }

            if (!captchaConfigService.isConfigured()) {
                log.warn("[Formidable] fmdbmix:captcha is applied on form '{}' but CAPTCHA is not configured (siteKey or secretKey missing).", resource.getNodePath());
                return super.prepare(renderContext, resource, chain);
            }

            renderContext.getRequest().setAttribute(ATTR_SITE_KEY,   captchaConfigService.getSiteKey());
            renderContext.getRequest().setAttribute(ATTR_SCRIPT_URL, captchaConfigService.getScriptUrl());

        } catch (Exception e) {
            String nodePath = resource != null ? resource.getNodePath() : "<no-resource>";
            log.warn("Could not prepare CAPTCHA attributes for node '{}'", nodePath, e);
        }

        return super.prepare(renderContext, resource, chain);
    }
}
