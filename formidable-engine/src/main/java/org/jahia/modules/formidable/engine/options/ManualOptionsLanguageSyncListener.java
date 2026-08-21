package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

/**
 * Re-aligns the manual options of every language on the site's default language
 * whenever a contributor saves them (see ManualOptionsLanguageSync for the
 * contract). fmdb:options is i18n, so its events fire on the j:translation_*
 * subnode: the path is matched by property name and stripped back to the field
 * node — no node-type filter would see the translation node's own type.
 *
 * Counterpart of FormLogicSyncListener for the options identity.
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class ManualOptionsLanguageSyncListener extends DefaultEventListener {

    private static final Logger log = LoggerFactory.getLogger(ManualOptionsLanguageSyncListener.class);

    private static final String OPTIONS_PROPERTY_SUFFIX = "/fmdb:options";
    private static final String TRANSLATION_SEGMENT = "/j:translation_";

    @Override
    public int getEventTypes() {
        return Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED;
    }

    @Override
    public void onEvent(EventIterator events) {
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                String path = event.getPath();
                if (!path.endsWith(OPTIONS_PROPERTY_SUFFIX)) {
                    continue;
                }

                String nodePath = path.substring(0, path.lastIndexOf('/'));
                int translation = nodePath.lastIndexOf(TRANSLATION_SEGMENT);
                if (translation >= 0) {
                    nodePath = nodePath.substring(0, translation);
                }

                String fieldPath = nodePath;
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, systemSession -> {
                    JCRNodeWrapper fieldNode = systemSession.getNode(fieldPath);
                    if (ManualOptionsLanguageSync.sync(fieldNode)) {
                        systemSession.save();
                        log.debug("[ManualOptionsLanguageSync] Re-aligned '{}'", fieldPath);
                    }

                    return null;
                });
            } catch (RepositoryException e) {
                log.warn("[ManualOptionsLanguageSync] Failed to re-align options: {}", e.getMessage());
            }
        }
    }
}
