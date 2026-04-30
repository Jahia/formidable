package org.jahia.modules.formidable.jexperience.engine.actions;

import org.jahia.modules.formidable.jexperience.engine.JExperienceEventService;
import org.jahia.modules.formidable.jexperience.engine.JExperienceFieldShape;
import org.jahia.modules.formidable.jexperience.engine.JExperienceFormFieldSupport;
import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component(service = FormAction.class)
public class SendToJExperienceFormAction implements FormAction {

    private static final Logger logger = LoggerFactory.getLogger(SendToJExperienceFormAction.class);

    private JExperienceEventService eventService;

    @Reference
    public void setEventService(JExperienceEventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public String getNodeType() {
        return "fmdb:jExperienceAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {
        JCRNodeWrapper formNode = resolveFormNode(actionNode);
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, String> profileMappings = new LinkedHashMap<>();

        try {
            if (formNode.hasNode("fields")) {
                for (JCRNodeWrapper fieldNode : JExperienceFormFieldSupport.collectMappableFieldNodes(formNode.getNode("fields"))) {
                    collectFieldPayload(fieldNode, parameters, fields, profileMappings);
                }
            }
        } catch (RepositoryException e) {
            throw new FormActionException("Could not inspect form fields for jExperience mapping.", 500, e);
        }

        logger.debug("[SendToJExperienceFormAction] Sending jExperience event for form '{}' with fields {} and mappings {}",
                safeIdentifier(formNode), fields.keySet(), profileMappings);
        eventService.sendSubmissionAcceptedEvent(formNode, req, fields, profileMappings);
    }

    private JCRNodeWrapper resolveFormNode(JCRNodeWrapper actionNode) throws FormActionException {
        try {
            JCRNodeWrapper actionListNode = (JCRNodeWrapper) actionNode.getParent();
            if (actionListNode == null) {
                throw FormActionException.serverError("The jExperience action is not attached to an action list.");
            }

            JCRNodeWrapper formNode = (JCRNodeWrapper) actionListNode.getParent();
            if (formNode == null || !formNode.isNodeType("fmdb:form")) {
                throw FormActionException.serverError("The jExperience action parent form could not be resolved.");
            }

            return formNode;
        } catch (RepositoryException e) {
            throw new FormActionException("Could not resolve form node for jExperience action.", 500, e);
        }
    }

    private void collectFieldPayload(
            JCRNodeWrapper fieldNode,
            Map<String, List<String>> parameters,
            Map<String, Object> fields,
            Map<String, String> profileMappings
    ) throws RepositoryException {
        Optional<JExperienceFieldShape> shape = JExperienceFormFieldSupport.resolveFieldShape(fieldNode);
        if (shape.isEmpty()) {
            return;
        }

        String fieldName = fieldNode.getName();
        List<String> rawValues = parameters.get(fieldName);
        if (rawValues == null || rawValues.isEmpty()) {
            return;
        }

        Optional<String> profileProperty = JExperienceFormFieldSupport.getProfileProperty(fieldNode);
        Object value = shape.get().multiple() ? List.copyOf(rawValues) : rawValues.get(0);

        fields.put(fieldName, value);

        // Future switch if we restore field-level event filtering:
        // boolean includeInEvent = JExperienceFormFieldSupport.isIncludedInEvent(fieldNode) || profileProperty.isPresent();
        // if (includeInEvent) {
        //     fields.put(fieldName, value);
        // }

        profileProperty.ifPresent(property -> profileMappings.put(fieldName, property));
    }

    private String safeIdentifier(JCRNodeWrapper node) {
        try {
            return node.getIdentifier();
        } catch (RepositoryException e) {
            return "unknown";
        }
    }
}
