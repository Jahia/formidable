package org.jahia.modules.formidable.engine.fieldactions;

import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderException;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Resource;
import org.json.JSONObject;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The runtime {@link ViewRenderer}: the platform's own way from Java into a JavaScript module. A Jahia JavaScript
 * module cannot expose an HTTP endpoint and the JavaScript modules engine exports none of its packages, so the one
 * sanctioned path is {@link RenderService#render}: the field-action node is rendered with the view named
 * {@value #VIEW}, the request travels in the {@value #REQUEST_ATTRIBUTE} attribute — request attributes are already
 * how the engine talks to the JavaScript views — and the view answers a small JSON body. Both the view's own
 * {@code cache.expiration=0} and the {@code expiration} attribute set here keep a verdict about one value out of
 * the fragment cache, where it would answer every visitor with the first one's result.
 */
final class RenderServiceViewRenderer implements ViewRenderer {

    /** The view a field-action type registers to be written in JavaScript. */
    static final String VIEW = "hidden.execute";
    /** The request attribute carrying the {@link FieldActionRequest} as JSON while the view renders. */
    static final String REQUEST_ATTRIBUTE = "formidable.fieldAction";
    private static final String EXPIRATION_ATTRIBUTE = "expiration";

    @Override
    public String render(JCRNodeWrapper actionNode, FieldActionRequest request, HttpServletRequest req, HttpServletResponse resp)
            throws RenderException, RepositoryException {
        if (req == null || resp == null) {
            return null;
        }
        Object previousPayload = req.getAttribute(REQUEST_ATTRIBUTE);
        Object previousExpiration = req.getAttribute(EXPIRATION_ATTRIBUTE);
        req.setAttribute(REQUEST_ATTRIBUTE, payload(request).toString());
        req.setAttribute(EXPIRATION_ATTRIBUTE, "0");
        try {
            Resource resource = new Resource(actionNode, "html", VIEW, Resource.CONFIGURATION_MODULE);
            RenderContext context = new RenderContext(req, resp, JCRSessionFactory.getInstance().getCurrentUser());
            context.setSite(actionNode.getResolveSite());
            context.setWorkspace(actionNode.getSession().getWorkspace().getName());
            context.setMainResource(resource);
            context.setServletPath("/cms/render");
            return RenderService.getInstance().render(resource, context);
        } finally {
            restore(req, REQUEST_ATTRIBUTE, previousPayload);
            restore(req, EXPIRATION_ATTRIBUTE, previousExpiration);
        }
    }

    static JSONObject payload(FieldActionRequest request) {
        JSONObject payload = new JSONObject();
        payload.put("formId", request.formId());
        payload.put("fieldName", request.fieldName());
        payload.put("value", request.value() == null ? "" : request.value());
        payload.put("locale", request.locale() == null ? "" : request.locale().toLanguageTag());
        return payload;
    }

    private static void restore(HttpServletRequest req, String name, Object previous) {
        if (previous == null) {
            req.removeAttribute(name);
        } else {
            req.setAttribute(name, previous);
        }
    }
}
