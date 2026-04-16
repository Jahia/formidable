package org.jahia.modules.formidable.engine.captcha;

import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
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
@Component(service = AbstractFilter.class, immediate = true)
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
        setPriority(10f);
        setApplyOnNodeTypes("fmdb:form");
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) {
        try {
            if (resource.getNode().isNodeType("fmdbmix:captcha")) {
                renderContext.getRequest().setAttribute(ATTR_SITE_KEY,   captchaConfigService.getSiteKey());
                renderContext.getRequest().setAttribute(ATTR_SCRIPT_URL, captchaConfigService.getScriptUrl());
            }
        } catch (Exception e) {
            log.warn("Could not check fmdbmix:captcha mixin on node '{}'", resource.getNodePath(), e);
        }
        return null; // continue render chain
    }
}

