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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main Jahia action that processes form submissions for fmdb:form nodes.
 *
 * When the fmdbmix:actionPipeline mixin is applied to a form, the contributor
 * selects action nodes (instances of types carrying fmdbmix:formAction) via the
 * "actions" weakreference-multiple property. This action resolves those references
 * and delegates to each registered {@link FormAction} service in order.
 *
 * Invoked via: POST /cms/render/live/{locale}/{formPath}.formidableSubmit.do
 */
@Component(service = Action.class)
public class FormSubmitAction extends Action {

    private static final Logger log = LoggerFactory.getLogger(FormSubmitAction.class);

    static final String ACTIONS_PROPERTY = "actions";
    static final String PROP_ENABLED = "enabled";

    private final List<FormAction> formActions = new CopyOnWriteArrayList<>();

    @Activate
    public void activate() {
        setName("formidableSubmit");
        setRequireAuthenticatedUser(false);
        setRequireValidSession(false);
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

        if (!formNode.hasProperty(ACTIONS_PROPERTY)) {
            return ok();
        }

        Value[] refs;
        try {
            refs = formNode.getProperty(ACTIONS_PROPERTY).getValues();
        } catch (Exception e) {
            log.warn("Could not read '{}' property on form node '{}'", ACTIONS_PROPERTY, formNode.getPath(), e);
            return ok();
        }

        for (Value ref : refs) {
            JCRNodeWrapper actionNode;
            try {
                actionNode = session.getNodeByIdentifier(ref.getString());
            } catch (ItemNotFoundException e) {
                log.warn("Referenced action node not found (uuid={}), skipping.", ref.getString());
                continue;
            }

            String nodeType = actionNode.getPrimaryNodeTypeName();
            FormAction handler = formActions.stream()
                    .filter(a -> nodeType.equals(a.getNodeType()))
                    .findFirst()
                    .orElse(null);

            if (handler == null) {
                log.warn("No FormAction handler found for node type '{}', skipping.", nodeType);
                continue;
            }

            try {
                handler.execute(actionNode, req, renderContext, session, parameters);
            } catch (FormActionException e) {
                log.warn("FormAction '{}' failed: {}", nodeType, e.getMessage());
                return error(e.getHttpStatus(), e.getMessage());
            }
        }

        return ok();
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
