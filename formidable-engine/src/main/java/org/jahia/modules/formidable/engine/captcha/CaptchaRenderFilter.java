package org.jahia.modules.formidable.engine.captcha;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
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

    private FormidableConfigService config;

    @Reference
    public void setConfig(FormidableConfigService config) {
        this.config = config;
    }

    @Activate
    public void activate() {
        setPriority(10);
        // fmdbmix:captcha is a mixin — Jahia evaluates isNodeType() so this correctly
        // restricts the filter to form nodes that have the mixin applied.
        setApplyOnNodeTypes("fmdbmix:captcha");
        setApplyOnTemplateTypes("html");
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        try {

            if (!config.isCaptchaConfigured()) {
                log.warn("[Formidable] fmdbmix:captcha is applied on form '{}' but CAPTCHA is not configured " +
                        "(siteKey or scriptUrl missing in org.jahia.modules.formidable.cfg). The widget will not be rendered.",
                        resource.getNodePath());
                return null;
            }

            renderContext.getRequest().setAttribute(ATTR_SITE_KEY,   config.getCaptchaSiteKey());
            renderContext.getRequest().setAttribute(ATTR_SCRIPT_URL, config.getCaptchaScriptUrl());
        } catch (Exception e) {
            log.warn("[Formidable] Could not prepare CAPTCHA attributes for node '{}'", resource.getNodePath(), e);
        }

        return super.prepare(renderContext, resource, chain);
    }
}
