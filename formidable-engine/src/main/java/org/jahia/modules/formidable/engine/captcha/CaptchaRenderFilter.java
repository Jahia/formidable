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
 * when the current fmdb:form node has fmdbmix:captchaProtectedForm applied.
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
    private static final String CAPTCHA_PROTECTED_FORM_MIXIN = "fmdbmix:captchaProtectedForm";

    static final String ATTR_SITE_KEY     = "formidable.captcha.siteKey";
    static final String ATTR_SCRIPT_URL   = "formidable.captcha.scriptUrl";
    static final String ATTR_WIDGET_VAR   = "formidable.captcha.widgetVar";
    static final String ATTR_TOKEN_FIELD  = "formidable.captcha.tokenField";

    private FormidableConfigService config;

    @Reference
    public void setConfig(FormidableConfigService config) {
        this.config = config;
    }

    @Activate
    public void activate() {
        setPriority(10);
        // fmdbmix:captcha in formidable-elements extends fmdbmix:captchaProtectedForm, so
        // applyOnNodeTypes can target the engine-owned contract directly.
        setApplyOnNodeTypes(CAPTCHA_PROTECTED_FORM_MIXIN);
        setApplyOnTemplateTypes("html");
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        try {

            if (!config.isCaptchaWidgetConfigured()) {
                log.warn("[Formidable] {} is applied on form '{}' but CAPTCHA is not configured " +
                        "(captchaSiteKey, captchaScriptUrl, captchaWidgetVar or captchaTokenField missing in org.jahia.modules.formidable.cfg). The widget will not be rendered.",
                        CAPTCHA_PROTECTED_FORM_MIXIN, resource.getNodePath());
                return null;
            }

            renderContext.getRequest().setAttribute(ATTR_SITE_KEY,     config.getCaptchaSiteKey());
            renderContext.getRequest().setAttribute(ATTR_SCRIPT_URL,   config.getCaptchaScriptUrl());
            renderContext.getRequest().setAttribute(ATTR_WIDGET_VAR,   config.getCaptchaWidgetVar());
            renderContext.getRequest().setAttribute(ATTR_TOKEN_FIELD,  config.getCaptchaTokenField());
        } catch (Exception e) {
            log.warn("[Formidable] Could not prepare CAPTCHA attributes for node '{}'", resource.getNodePath(), e);
        }

        return super.prepare(renderContext, resource, chain);
    }
}
