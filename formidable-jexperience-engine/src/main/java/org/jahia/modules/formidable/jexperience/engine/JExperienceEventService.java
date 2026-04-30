package org.jahia.modules.formidable.jexperience.engine;

import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.modules.jexperience.admin.ContextServerService;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component(service = JExperienceEventService.class)
public class JExperienceEventService {

    static final String EVENT_TYPE = "formidableSubmissionAccepted";

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private volatile ContextServerService contextServerService;

    public void sendSubmissionAcceptedEvent(
            JCRNodeWrapper formNode,
            HttpServletRequest request,
            Map<String, Object> fields,
            Map<String, String> profileMappings
    ) throws FormActionException {
        if (contextServerService == null) {
            throw FormActionException.serverError("jExperience is not available.");
        }

        String siteKey;
        try {
            siteKey = formNode.getResolveSite().getName();
        } catch (RepositoryException e) {
            throw new FormActionException("Cannot resolve form site for jExperience event.", 500, e);
        }

        if (!contextServerService.isAvailable(siteKey)) {
            throw FormActionException.serverError("jExperience is not available for site '" + siteKey + "'.");
        }

        Item source = buildSource(formNode, siteKey, request);
        Item target = buildTarget(formNode, siteKey);
        Map<String, Object> properties = Map.of(
                "submissionChannel", "formidable",
                "submissionStatus", "accepted",
                "fields", fields,
                "profileMappings", profileMappings
        );

        Event event = new Event(EVENT_TYPE, null, null, siteKey, source, target, properties, new Date(), true);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSource(source);
        contextRequest.setSessionId(contextServerService.getWemSessionId(request));
        contextRequest.setProfileId(contextServerService.getProfileId(request, siteKey));
        contextRequest.setEvents(List.of(event));

        try {
            contextServerService.executeContextRequest(contextRequest, request, siteKey);
        } catch (IOException e) {
            throw new FormActionException("Could not submit event to jExperience.", 500, e);
        }
    }

    private Item buildSource(JCRNodeWrapper formNode, String siteKey, HttpServletRequest request)
            throws FormActionException {
        CustomItem source = new CustomItem(safeIdentifier(formNode), "page");
        source.setScope(siteKey);
        source.setProperties(Map.of(
                "path", safeNodePath(formNode),
                "language", formNode.getLanguage(),
                "url", request.getHeader("Referer") == null ? "" : request.getHeader("Referer")
        ));
        return source;
    }

    private Item buildTarget(JCRNodeWrapper formNode, String siteKey) throws FormActionException {
        CustomItem target = new CustomItem(safeIdentifier(formNode), "form");
        target.setScope(siteKey);
        target.setProperties(Map.of(
                "formId", safeIdentifier(formNode),
                "formPath", safeNodePath(formNode),
                "formType", safeNodeType(formNode),
                "language", formNode.getLanguage()
        ));
        return target;
    }

    private String safeNodePath(JCRNodeWrapper node) throws FormActionException {
        return node.getPath();
    }

    private String safeIdentifier(JCRNodeWrapper node) throws FormActionException {
        try {
            return node.getIdentifier();
        } catch (RepositoryException e) {
            throw new FormActionException("Cannot resolve node identifier for jExperience event.", 500, e);
        }
    }

    private String safeNodeType(JCRNodeWrapper node) throws FormActionException {
        try {
            return node.getPrimaryNodeTypeName();
        } catch (RepositoryException e) {
            throw new FormActionException("Cannot resolve node type for jExperience event.", 500, e);
        }
    }
}
