package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shows every form its jCustomer identifier: when a form is created, or first edited after
 * this module arrived, the {@code fmdbmix:jExperienceForm} mixin and its read-only
 * {@code jExperienceIdentifier} are written on the node in the default workspace, so the
 * author can copy the identifier from the form's jExperience section into a goal. A form that
 * already carries it is left alone — the write itself fires an event this listener ignores.
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class FormIdentifierListener extends DefaultEventListener {

    static final String FORM_NODE_TYPE = "fmdb:form";
    static final String WORKSPACE_DEFAULT = "default";
    static final String TRANSLATION_NODE_PREFIX = "j:translation_";

    private static final Logger log = LoggerFactory.getLogger(FormIdentifierListener.class);

    public FormIdentifierListener() {
        setWorkspace(WORKSPACE_DEFAULT);
    }

    @Override
    public int getEventTypes() {
        return Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{FORM_NODE_TYPE};
    }

    @Override
    public void onEvent(EventIterator events) {
        Set<String> candidates = new LinkedHashSet<>();
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                candidates.add(nodePathOf(event));
            } catch (RepositoryException e) {
                log.debug("[FormIdentifierListener] Could not read an event path: {}", e.getMessage());
            }
        }
        for (String path : candidates) {
            stampIfMissing(path);
        }
    }

    /** The node the event is about: a property event carries the property's path, one level below. */
    private static String nodePathOf(Event event) throws RepositoryException {
        String path = event.getPath();
        if (event.getType() == Event.NODE_ADDED) {
            return path;
        }
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : path;
    }

    private void stampIfMissing(String path) {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_DEFAULT, null, session -> {
                JCRNodeWrapper node;
                try {
                    node = session.getNode(path);
                } catch (PathNotFoundException e) {
                    return null;
                }
                // the type filter of the listener also lets the form's translation subnodes through: an
                // i18n title edit is an event on j:translation_<lang>, one level below the form
                if (node.getName().startsWith(TRANSLATION_NODE_PREFIX)) {
                    node = node.getParent();
                }
                if (!node.isNodeType(FORM_NODE_TYPE) || node.hasProperty(FormIdentifier.PROPERTY)) {
                    return null;
                }
                if (!node.isNodeType(FormIdentifier.FORM_MIXIN)) {
                    node.addMixin(FormIdentifier.FORM_MIXIN);
                }
                node.setProperty(FormIdentifier.PROPERTY, FormIdentifier.of(node.getIdentifier()));
                session.save();
                log.info("[FormIdentifierListener] Stamped the jExperience identifier on {}", node.getPath());
                return null;
            });
        } catch (RepositoryException e) {
            log.warn("[FormIdentifierListener] Could not stamp the jExperience identifier on {}: {}", path, e.getMessage());
        }
    }
}
