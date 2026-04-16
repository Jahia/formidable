package org.jahia.modules.formidable.engine.actions;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.formidable.engine.captcha.CaptchaConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Value;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main Jahia action that processes form submissions for fmdb:form nodes.
 *
 * Pipeline order (enforced regardless of configuration):
 *   1. CAPTCHA verification — if fmdb:form has fmdbmix:captcha mixin (always server-side)
 *   2. Destination          — save2jcr or other fmdbmix:formDestination
 *   3. Follow-up actions    — fmdbmix:formSideEffect nodes (e.g. email)
 *
 * Invoked via: POST /cms/render/live/{locale}/{formPath}.formidableSubmit.do
 */
@Component(service = Action.class)
public class FormSubmitAction extends Action {

    private static final Logger log = LoggerFactory.getLogger(FormSubmitAction.class);

    static final String PROP_DESTINATION = "destination";
    static final String PROP_ACTIONS     = "actions";

    // CAPTCHA token field names per provider (injected automatically by the widget)
    private static final String[] CAPTCHA_TOKEN_FIELDS = {
            "cf-turnstile-response",   // Cloudflare Turnstile
            "h-captcha-response",      // hCaptcha
            "g-recaptcha-response"     // Google reCAPTCHA v2
    };

    private final List<FormAction> formActions = new CopyOnWriteArrayList<>();
    private CaptchaConfigService captchaConfigService;

    @Activate
    public void activate() {
        setName("formidableSubmit");
        setRequireAuthenticatedUser(false);
    }

    @Reference
    public void setCaptchaConfigService(CaptchaConfigService captchaConfigService) {
        this.captchaConfigService = captchaConfigService;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unbindFormAction")
    protected void bindFormAction(FormAction action) {
        formActions.add(action);
        log.debug("Registered FormAction: {}", action.getNodeType());
    }

    protected void unbindFormAction(FormAction action) {
        formActions.remove(action);
    }

    @Override
    public ActionResult doExecute(
            HttpServletRequest req,
            RenderContext renderContext,
            Resource resource,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters,
            URLResolver urlResolver
    ) throws Exception {

        JCRNodeWrapper formNode = resource.getNode();

        try {
            // 1. CAPTCHA verification (always first, if mixin is present)
            boolean hasCaptcha = false;
            try {
                hasCaptcha = formNode.isNodeType("fmdbmix:captcha");
            } catch (javax.jcr.RepositoryException e) {
                log.warn("Could not check fmdbmix:captcha on form node '{}'", formNode.getPath(), e);
            }

            if (hasCaptcha) {
                String token = resolveToken(parameters);
                boolean valid = captchaConfigService.verify(token, req.getRemoteAddr());
                if (!valid) {
                    return error(HttpServletResponse.SC_BAD_REQUEST, "CAPTCHA verification failed.");
                }
            }

            // 2. Destination
            JCRNodeWrapper destinationNode = resolveWeakRef(formNode, PROP_DESTINATION, session);
            if (destinationNode != null) {
                executeAction(destinationNode, req, renderContext, session, parameters);
            }

            // 3. Follow-up actions
            for (JCRNodeWrapper node : resolveWeakRefs(formNode, PROP_ACTIONS, session)) {
                executeAction(node, req, renderContext, session, parameters);
            }

        } catch (FormActionException e) {
            log.warn("Pipeline stopped: {}", e.getMessage());
            return error(e.getHttpStatus(), e.getMessage());
        }

        return ok();
    }

    /** Tries each known captcha token field name and returns the first non-blank value found. */
    private static String resolveToken(Map<String, List<String>> parameters) {
        for (String field : CAPTCHA_TOKEN_FIELDS) {
            List<String> values = parameters.get(field);
            if (values != null && !values.isEmpty() && !values.get(0).isBlank()) {
                return values.get(0);
            }
        }
        return null;
    }

    private void executeAction(
            JCRNodeWrapper node,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {
        String nodeType;
        try {
            nodeType = node.getPrimaryNodeTypeName();
        } catch (javax.jcr.RepositoryException e) {
            log.warn("Could not get primary node type for node '{}', skipping.", node.getPath());
            return;
        }
        FormAction handler = formActions.stream()
                .filter(a -> nodeType.equals(a.getNodeType()))
                .findFirst()
                .orElse(null);
        if (handler == null) {
            log.warn("No FormAction handler found for node type '{}', skipping.", nodeType);
            return;
        }
        handler.execute(node, req, renderContext, session, parameters);
    }

    private static JCRNodeWrapper resolveWeakRef(JCRNodeWrapper node, String prop, JCRSessionWrapper session) {
        try {
            if (!node.hasProperty(prop)) return null;
            return session.getNodeByIdentifier(node.getProperty(prop).getString());
        } catch (ItemNotFoundException e) {
            log.warn("Referenced node not found for property '{}' on '{}'", prop, node.getPath());
            return null;
        } catch (Exception e) {
            log.warn("Could not resolve weakref '{}' on '{}'", prop, node.getPath(), e);
            return null;
        }
    }

    private static List<JCRNodeWrapper> resolveWeakRefs(JCRNodeWrapper node, String prop, JCRSessionWrapper session) {
        List<JCRNodeWrapper> result = new ArrayList<>();
        try {
            if (!node.hasProperty(prop)) return result;
            for (Value v : node.getProperty(prop).getValues()) {
                try {
                    result.add(session.getNodeByIdentifier(v.getString()));
                } catch (ItemNotFoundException e) {
                    log.warn("Referenced node not found (uuid={}) in '{}', skipping.", v.getString(), prop);
                }
            }
        } catch (Exception e) {
            log.warn("Could not read '{}' property on '{}'", prop, node.getPath(), e);
        }
        return result;
    }

    private static ActionResult ok() {
        JSONObject body = new JSONObject();
        body.put("success", true);
        return new ActionResult(HttpServletResponse.SC_OK, null, body);
    }

    private static ActionResult error(int status, String message) {
        JSONObject body = new JSONObject();
        body.put("success", false);
        body.put("message", message);
        return new ActionResult(status, null, body);
    }
}
