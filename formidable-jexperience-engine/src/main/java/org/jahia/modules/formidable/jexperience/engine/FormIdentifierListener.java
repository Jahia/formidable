package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.query.Query;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shows every form its jCustomer identifier: the {@code fmdbmix:jExperienceForm} mixin and its
 * read-only {@code jExperienceIdentifier} are written on the node in the default workspace, so
 * the author can copy the identifier from the form's jExperience section into a goal. Every
 * form under {@code /sites} the module finds when it starts is stamped at once, one save per
 * form; afterwards a form is stamped when it is created — a copy, an import — or, should the
 * start-up pass have missed it, when it is next edited.
 *
 * <p>The identifier is recomputed from the node's own UUID and compared with the stored value,
 * never assumed from its presence: a JCR copy or an import carries the source form's mixin and
 * value onto a node with a new UUID (the core copies every mixin and property it does not
 * forbid), and two forms must never share one jCustomer identity. A form carrying the right
 * value is left alone — which is also how the write's own event ends here.
 *
 * <p>Default workspace only, on purpose. The identifier is a pure function of the UUID, which
 * publication preserves, and every runtime use — the mapping rule, the event, the render
 * filter's config block — derives it from the UUID again instead of reading the stored
 * property, so live never needs the stored value and the pass stays clear of the live-write
 * traps the engine's MigrationSessions documents. The visible effect on an upgraded instance is
 * that every form shows as <em>modified</em> in jContent once the module starts. Lifecycle:
 * docs/administration/upgrade-notes.md, "Installing the jExperience integration flags every
 * form as modified".
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class FormIdentifierListener extends DefaultEventListener {

    // formidable-elements declares the form type, so the engine publishes no constant for it:
    // reading it by name is the gap a form marker would close (docs/architecture/cnd-module-ownership.md).
    static final String FORM_NODE_TYPE = "fmdb:form";
    static final String WORKSPACE_DEFAULT = "default";
    static final String TRANSLATION_NODE_PREFIX = "j:translation_";
    /** Editorial content only, as for every engine pass: module-bundled nodes under /modules belong to their module. */
    static final String SCOPE = "/sites";
    static final String FORMS_QUERY = "SELECT * FROM [" + FORM_NODE_TYPE + "] WHERE ISDESCENDANTNODE('" + SCOPE + "')";

    private static final Logger log = LoggerFactory.getLogger(FormIdentifierListener.class);

    public FormIdentifierListener() {
        setWorkspace(WORKSPACE_DEFAULT);
    }

    /** Every existing form gets its identifier when the module starts, so no author meets an empty read-only field. */
    @Activate
    public void stampExistingForms() {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_DEFAULT, null,
                    session -> stampAll(session, formsUnderSites(session)));
        } catch (RepositoryException e) {
            log.warn("[FormIdentifierListener] Could not stamp the existing forms at start, they will be stamped when edited: {}", e.getMessage());
        }
    }

    private static NodeIterator formsUnderSites(JCRSessionWrapper session) throws RepositoryException {
        return session.getWorkspace().getQueryManager().createQuery(FORMS_QUERY, Query.JCR_SQL2).execute().getNodes();
    }

    /**
     * The start-up pass: one save per form, so a form that cannot be saved — locked, read-only,
     * a stale definition — is logged and skipped while the others keep their identifier.
     *
     * @return how many forms were stamped
     */
    int stampAll(JCRSessionWrapper session, NodeIterator forms) {
        int stamped = 0;
        int failed = 0;
        while (forms.hasNext()) {
            JCRNodeWrapper form = (JCRNodeWrapper) forms.nextNode();
            try {
                if (stamp(form)) {
                    session.save();
                    stamped++;
                }
            } catch (RepositoryException e) {
                failed++;
                log.warn("[FormIdentifierListener] Could not stamp the jExperience identifier on {}: {}", form.getPath(), e.getMessage());
                refreshQuietly(session);
            }
        }
        log.info("[FormIdentifierListener] Stamped the jExperience identifier on {} existing form(s) at start", stamped);
        if (failed > 0) {
            log.warn("[FormIdentifierListener] {} form(s) could not be stamped at start; each is stamped again when next edited", failed);
        }
        return stamped;
    }

    /** Drops the half-applied changes of the failed form, or every later save would re-throw them. */
    private static void refreshQuietly(JCRSessionWrapper session) {
        try {
            session.refresh(false);
        } catch (RepositoryException e) {
            log.warn("[FormIdentifierListener] Could not discard the pending changes: {}", e.getMessage());
        }
    }

    @Override
    public int getEventTypes() {
        return Event.NODE_ADDED | Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{FORM_NODE_TYPE};
    }

    /** Observation is scoped like the start-up pass. */
    @Override
    public String getPath() {
        return SCOPE;
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
            stampIfNeeded(path);
        }
    }

    /** The node the event is about: a property event carries the property's path, one level below. */
    static String nodePathOf(Event event) throws RepositoryException {
        String path = event.getPath();
        if (event.getType() == Event.NODE_ADDED) {
            return path;
        }
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : path;
    }

    /**
     * The form an event's node belongs to: the type filter of the listener also lets the form's
     * translation subnodes through — an i18n title edit is an event on {@code j:translation_<lang>},
     * one level below the form.
     */
    static JCRNodeWrapper formOf(JCRNodeWrapper node) throws RepositoryException {
        return node.getName().startsWith(TRANSLATION_NODE_PREFIX) ? node.getParent() : node;
    }

    /**
     * Writes the mixin and the identifier the form's own UUID gives; false when the form already
     * carries exactly that. Presence is not enough: a JCR copy or an import brings the source
     * form's value onto a node with a new UUID.
     */
    static boolean stamp(JCRNodeWrapper node) throws RepositoryException {
        if (!node.isNodeType(FORM_NODE_TYPE)) {
            return false;
        }
        String identifier = FormIdentifier.of(node.getIdentifier());
        if (identifier.equals(node.getPropertyAsString(FormIdentifier.PROPERTY))) {
            return false;
        }
        if (!node.isNodeType(FormIdentifier.FORM_MIXIN)) {
            node.addMixin(FormIdentifier.FORM_MIXIN);
        }
        node.setProperty(FormIdentifier.PROPERTY, identifier);
        return true;
    }

    private void stampIfNeeded(String path) {
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE_DEFAULT, null, session -> {
                JCRNodeWrapper node;
                try {
                    node = session.getNode(path);
                } catch (PathNotFoundException e) {
                    return null;
                }
                JCRNodeWrapper form = formOf(node);
                if (stamp(form)) {
                    session.save();
                    log.info("[FormIdentifierListener] Stamped the jExperience identifier on {}", form.getPath());
                }
                return null;
            });
        } catch (RepositoryException e) {
            log.warn("[FormIdentifierListener] Could not stamp the jExperience identifier on {}: {}", path, e.getMessage());
        }
    }
}
