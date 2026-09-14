package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Publication listener of the mapping rules: every change a publication brings to a form or to
 * one of its mapped fields in the live workspace — the form arriving, a field's mapping added or
 * changed, the form or a field leaving — resynchronises that form's rule. Live only, and marked
 * available during publication, without which Jahia never delivers publication events to a
 * listener (the engine's ACL sync listener learnt it the hard way). The form is resolved from
 * the event's node by climbing to the nearest {@code fmdb:form}.
 *
 * <p>Removals are only half covered here: Jahia resolves a removed node's types from the event's
 * info, which an unpublication fills and a published deletion does not — so a deleted form's
 * {@code NODE_REMOVED} never passes a node-type filter. {@link MappingRuleRemovalListener}, unfiltered,
 * takes the removals; this listener still handles the ones it receives, the synchronizer coalescing
 * the two.
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class MappingRuleSyncListener extends DefaultEventListener {

    static final String FORM_NODE_TYPE = FormIdentifierListener.FORM_NODE_TYPE;
    static final String SCOPE = "/sites";

    private static final Logger log = LoggerFactory.getLogger(MappingRuleSyncListener.class);

    @Reference
    private MappingRuleSynchronizer synchronizer;

    public MappingRuleSyncListener() {
        setWorkspace(MappingRuleSynchronizer.WORKSPACE_LIVE);
        setAvailableDuringPublish(true);
    }

    MappingRuleSyncListener(MappingRuleSynchronizer synchronizer) {
        this();
        this.synchronizer = synchronizer;
    }

    @Override
    public int getEventTypes() {
        return Event.NODE_ADDED | Event.NODE_REMOVED | Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{FORM_NODE_TYPE, FormMappingReader.MAPPING_MIXIN};
    }

    @Override
    public String getPath() {
        return SCOPE;
    }

    @Override
    public void onEvent(EventIterator events) {
        List<Event> batch = new ArrayList<>();
        while (events.hasNext()) {
            batch.add(events.nextEvent());
        }
        if (log.isDebugEnabled()) {
            for (Event event : batch) {
                log.debug("[MappingRuleSyncListener] event type={} path={} id={}", event.getType(), pathOf(event), identifierOf(event));
            }
        }
        Map<String, String> forms;
        try {
            forms = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, MappingRuleSynchronizer.WORKSPACE_LIVE, null,
                    session -> formsOf(batch, session));
        } catch (RepositoryException e) {
            log.warn("[MappingRuleSyncListener] Could not resolve the forms of a publication: {}", e.getMessage());
            return;
        }
        forms.forEach((uuid, siteKey) -> synchronizer.syncLater(siteKey, uuid));
    }

    /** The forms a batch of events touches, each once: form uuid → site key. */
    Map<String, String> formsOf(List<Event> events, JCRSessionWrapper session) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (Event event : events) {
            try {
                formOf(event, session).ifPresent(form -> forms.put(form.uuid(), form.siteKey()));
            } catch (RepositoryException e) {
                log.warn("[MappingRuleSyncListener] Could not resolve the form of an event: {}", e.getMessage());
            }
        }
        return forms;
    }

    record Form(String siteKey, String uuid) {
    }

    /** The published form an event is about, or the form a removal took away — by its identifier. */
    Optional<Form> formOf(Event event, JCRSessionWrapper session) throws RepositoryException {
        String path = nodePathOf(event);
        String siteKey = siteKeyOf(path);
        if (siteKey == null) {
            return Optional.empty();
        }
        if (event.getType() == Event.NODE_REMOVED) {
            // a removed field: its form is still there, above; a removed form: the event names it
            return nearestForm(session, parentPath(path))
                    .or(() -> Optional.ofNullable(identifierOf(event)))
                    .map(uuid -> new Form(siteKey, uuid));
        }
        return nearestForm(session, path).map(uuid -> new Form(siteKey, uuid));
    }

    private static String pathOf(Event event) {
        try {
            return event.getPath();
        } catch (RepositoryException e) {
            return "?";
        }
    }

    private static String identifierOf(Event event) {
        try {
            return event.getIdentifier();
        } catch (RepositoryException e) {
            return null;
        }
    }

    static Optional<String> nearestForm(JCRSessionWrapper session, String path) throws RepositoryException {
        String current = path;
        while (current != null && current.startsWith(SCOPE + "/")) {
            try {
                JCRNodeWrapper node = session.getNode(current);
                if (node.isNodeType(FORM_NODE_TYPE)) {
                    return Optional.of(node.getIdentifier());
                }
            } catch (PathNotFoundException e) {
                // gone with the publication: keep climbing
            }
            current = parentPath(current);
        }
        return Optional.empty();
    }

    static String nodePathOf(Event event) throws RepositoryException {
        String path = event.getPath();
        if (event.getType() == Event.PROPERTY_ADDED || event.getType() == Event.PROPERTY_CHANGED) {
            return parentPath(path);
        }
        return path;
    }

    static String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : null;
    }

    /** The site of a path under /sites, the scope of the rule and of jExperience's client. */
    static String siteKeyOf(String path) {
        if (path == null || !path.startsWith(SCOPE + "/")) {
            return null;
        }
        String rest = path.substring(SCOPE.length() + 1);
        int slash = rest.indexOf('/');
        String key = slash < 0 ? rest : rest.substring(0, slash);
        return key.isEmpty() ? null : key;
    }
}
