package org.jahia.modules.formidable.engine.actions;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
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
 * Reads three distinct properties from the form node:
 *   - captcha    (weakreference → fmdb:captchaAction)    widget config + conditional verification
 *   - destination (weakreference → fmdbmix:formDestination)  where data goes (JCR or third party)
 *   - actions    (weakreference-multiple → fmdbmix:formSideEffect)  email etc., run after destination
 *
 * Pipeline order is enforced regardless of configuration:
 *   JCR mode    : captcha verify → save2jcr → side effects
 *   Transfer mode : sendData (captcha not verified) → side effects
 *
 * Invoked via: POST /cms/render/live/{locale}/{formPath}.formidableSubmit.do
 */
@Component(service = Action.class)
public class FormSubmitAction extends Action {

    private static final Logger log = LoggerFactory.getLogger(FormSubmitAction.class);

    static final String PROP_CAPTCHA     = "captcha";
    static final String PROP_DESTINATION = "destination";
    static final String PROP_ACTIONS     = "actions";

    private final List<FormAction> formActions = new CopyOnWriteArrayList<>();

    @Activate
    public void activate() {
        setName("formidableSubmit");
        setRequireAuthenticatedUser(false);
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

        JCRNodeWrapper captchaNode     = resolveWeakRef(formNode, PROP_CAPTCHA, session);
        JCRNodeWrapper destinationNode = resolveWeakRef(formNode, PROP_DESTINATION, session);
        List<JCRNodeWrapper> sideEffects = resolveWeakRefs(formNode, PROP_ACTIONS, session);

        if (captchaNode == null && destinationNode == null && sideEffects.isEmpty()) {
            return ok();
        }

        try {
            if (destinationNode != null && destinationNode.isNodeType("fmdb:sendDataAction")) {
                // Transfer mode: the client already POSTed to the destination before calling this pipeline.
                // Captcha was consumed by the target — do not re-verify here.
                // Only run side effects (e.g. email notification).
                for (JCRNodeWrapper node : sideEffects) {
                    executeAction(node, req, renderContext, session, parameters);
                }
            } else {
                // JCR mode: verify captcha first, then store, then side effects
                if (captchaNode != null) {
                    executeAction(captchaNode, req, renderContext, session, parameters);
                }
                if (destinationNode != null) {
                    executeAction(destinationNode, req, renderContext, session, parameters);
                }
                for (JCRNodeWrapper node : sideEffects) {
                    executeAction(node, req, renderContext, session, parameters);
                }
            }
        } catch (FormActionException e) {
            log.warn("Pipeline stopped: {}", e.getMessage());
            return error(e.getHttpStatus(), e.getMessage());
        }

        return ok();
    }

    private void executeAction(
            JCRNodeWrapper node,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {
        String nodeType = node.getPrimaryNodeTypeName();
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
