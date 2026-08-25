package org.jahia.modules.formidable.engine.logic;

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
import java.util.ArrayList;
import java.util.List;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FORM_LOGIC_ELEMENT_MIXIN;

/**
 * Assigns a fieldKey to every newly created form element (field, fieldset, step).
 *
 * fieldKey is the element's stable business identity: unlike the JCR UUID it survives
 * import/copy, and unlike the node name it survives renames. Conditional logic rules
 * persist it as sourceFieldKey, which makes them unambiguous when several fields share
 * the same name or label.
 *
 * Copied nodes arrive with the original's fieldKey and are left untouched here;
 * same-form collisions are remapped by FormDuplicationCleanupListener.
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class FieldKeyAssignmentListener extends DefaultEventListener {
    private static final Logger log = LoggerFactory.getLogger(FieldKeyAssignmentListener.class);

    @Override
    public int getEventTypes() {
        return Event.NODE_ADDED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{FORM_LOGIC_ELEMENT_MIXIN};
    }

    @Override
    public void onEvent(EventIterator events) {
        // Imports and subtree copies deliver one NODE_ADDED per node: process the whole
        // batch in a single system session with a single save instead of one per node.
        List<String> nodePaths = new ArrayList<>();
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                nodePaths.add(event.getPath());
            } catch (RepositoryException e) {
                log.warn("[FieldKey] Failed to read event path: {}", e.getMessage());
            }
        }

        if (nodePaths.isEmpty()) {
            return;
        }

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, systemSession -> {
                boolean assigned = false;
                for (String nodePath : nodePaths) {
                    JCRNodeWrapper node;
                    try {
                        node = systemSession.getNode(nodePath);
                    } catch (PathNotFoundException e) {
                        continue;
                    }

                    // Jahia's observation manager matches the type filter against the
                    // parent for j:translation_* subnodes, so their NODE_ADDED events
                    // land here too: a key written on a translation node has no
                    // definition there and only pollutes the repository.
                    if (!node.isNodeType(FORM_LOGIC_ELEMENT_MIXIN)) {
                        continue;
                    }

                    if (FieldKeys.assignIfMissing(node)) {
                        assigned = true;
                        log.debug("[FieldKey] Assigned fieldKey to '{}'", nodePath);
                    }
                }

                if (assigned) {
                    systemSession.save();
                }

                return null;
            });
        } catch (RepositoryException e) {
            log.warn("[FieldKey] Failed to assign fieldKeys: {}", e.getMessage());
        }
    }
}
