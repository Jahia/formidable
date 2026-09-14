package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.DefaultEventListener;
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
 * The removals half of the mapping-rule sync: every {@code NODE_REMOVED} under {@code /sites} in
 * live, with no node-type filter — Jahia cannot resolve the types of a node removed by a published
 * deletion, so a typed listener never sees a deleted form leave live. Only the head of a removed
 * subtree is considered, the one node whose parent still exists: a removed field under a still
 * published form resynchronises that form; a removed node with no form above it may have been a
 * form, and is synchronised by the identifier the event carries — one rule lookup, answered 204
 * when it was not a form. Children of a removed subtree, whose parent is gone too, cost nothing.
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class MappingRuleRemovalListener extends DefaultEventListener {

    private static final Logger log = LoggerFactory.getLogger(MappingRuleRemovalListener.class);

    @Reference
    private MappingRuleSynchronizer synchronizer;

    public MappingRuleRemovalListener() {
        setWorkspace(MappingRuleSynchronizer.WORKSPACE_LIVE);
        setAvailableDuringPublish(true);
    }

    MappingRuleRemovalListener(MappingRuleSynchronizer synchronizer) {
        this();
        this.synchronizer = synchronizer;
    }

    @Override
    public int getEventTypes() {
        return Event.NODE_REMOVED;
    }

    @Override
    public String getPath() {
        return MappingRuleSyncListener.SCOPE;
    }

    @Override
    public void onEvent(EventIterator events) {
        List<Event> batch = new ArrayList<>();
        while (events.hasNext()) {
            batch.add(events.nextEvent());
        }
        Map<String, String> forms;
        try {
            forms = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, MappingRuleSynchronizer.WORKSPACE_LIVE, null,
                    session -> formsOf(batch, session));
        } catch (RepositoryException e) {
            log.warn("[MappingRuleRemovalListener] Could not resolve the forms of a removal: {}", e.getMessage());
            return;
        }
        forms.forEach((uuid, siteKey) -> synchronizer.syncLater(siteKey, uuid));
    }

    /** The forms a batch of removals touches, each once: form uuid → site key. */
    Map<String, String> formsOf(List<Event> events, JCRSessionWrapper session) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (Event event : events) {
            try {
                formOf(event, session).ifPresent(form -> forms.put(form.uuid(), form.siteKey()));
            } catch (RepositoryException e) {
                log.warn("[MappingRuleRemovalListener] Could not resolve the form of a removal: {}", e.getMessage());
            }
        }
        return forms;
    }

    /**
     * The form a removal concerns: the one above a removed field, or the removed node itself when
     * nothing above it is a form; empty when the node went with a larger removal (its parent is
     * gone too) or lies outside a site.
     */
    Optional<MappingRuleSyncListener.Form> formOf(Event event, JCRSessionWrapper session) throws RepositoryException {
        String path = event.getPath();
        String siteKey = MappingRuleSyncListener.siteKeyOf(path);
        String parent = MappingRuleSyncListener.parentPath(path);
        if (siteKey == null || parent == null || !parentExists(session, parent)) {
            return Optional.empty();
        }
        return MappingRuleSyncListener.nearestForm(session, parent)
                .or(() -> Optional.ofNullable(identifierOf(event)))
                .map(uuid -> new MappingRuleSyncListener.Form(siteKey, uuid));
    }

    private static boolean parentExists(JCRSessionWrapper session, String parent) throws RepositoryException {
        try {
            session.getNode(parent);
            return true;
        } catch (PathNotFoundException e) {
            return false;
        }
    }

    private static String identifierOf(Event event) {
        try {
            return event.getIdentifier();
        } catch (RepositoryException e) {
            return null;
        }
    }
}
