package org.jahia.modules.formidable.engine.options;

import org.jahia.modules.formidable.engine.migration.ChoiceOptionsContentMigration;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.MANUAL_OPTIONS_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.OPTIONS_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.TRANSLATION_NODE_PREFIX;

/**
 * Re-aligns the manual options of every language on the site's default language
 * whenever a contributor saves them (see ManualOptionsLanguageSync for the
 * contract). fmdb:options is i18n, so its events fire on the j:translation_*
 * subnode; Jahia's observation manager merges the parent node's types into
 * translation events, so the fmdbmix:manualOptions type filter applies before
 * onEvent, and the path is stripped back to the field node here.
 *
 * One batch of events (a save, a bulk import) is deduplicated to its distinct
 * field paths and handled in one system session: the sync's own rewrite of an
 * N-language field re-enters observation as N-1 events for the same path, which
 * this reduces back to a single no-op pass. Each path also carries the languages
 * the save touched, read off the j:translation_* segment — the sync needs them to
 * tell a field awaiting its identity from a default language a contributor just
 * emptied.
 *
 * Counterpart of FormLogicSyncListener for the options identity.
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class ManualOptionsLanguageSyncListener extends DefaultEventListener {

    private static final Logger log = LoggerFactory.getLogger(ManualOptionsLanguageSyncListener.class);

    private static final String OPTIONS_PROPERTY_SUFFIX = "/" + OPTIONS_PROPERTY;
    private static final String TRANSLATION_SEGMENT = "/" + TRANSLATION_NODE_PREFIX;

    /**
     * Hard activation ordering, not a used reference: the startup migration moves
     * legacy per-language options onto the translation subnodes verbatim, and its
     * saves must NOT be re-aligned — the documented contract is "re-aligned the
     * next time the field is saved". Depending on the migration component makes
     * SCR register this listener only after the migration completed, instead of
     * relying on the header order of the two components.
     */
    @Reference
    @SuppressWarnings("unused")
    private ChoiceOptionsContentMigration startupMigrationCompleted;

    @Override
    public int getEventTypes() {
        return Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{MANUAL_OPTIONS_MIXIN};
    }

    @Override
    public void onEvent(EventIterator events) {
        Map<String, Set<String>> savedLanguagesByField = new LinkedHashMap<>();
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                String path = event.getPath();
                if (!path.endsWith(OPTIONS_PROPERTY_SUFFIX)) {
                    continue;
                }

                String nodePath = path.substring(0, path.lastIndexOf('/'));
                String language = null;
                int translation = nodePath.lastIndexOf(TRANSLATION_SEGMENT);
                if (translation >= 0) {
                    language = nodePath.substring(translation + TRANSLATION_SEGMENT.length());
                    nodePath = nodePath.substring(0, translation);
                }

                Set<String> savedLanguages =
                        savedLanguagesByField.computeIfAbsent(nodePath, unused -> new LinkedHashSet<>());
                if (language != null) {
                    savedLanguages.add(language);
                }
            } catch (RepositoryException e) {
                log.warn("[ManualOptionsLanguageSync] Unreadable options event: {}", e.getMessage());
            }
        }

        if (savedLanguagesByField.isEmpty()) {
            return;
        }

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, systemSession -> {
                for (Map.Entry<String, Set<String>> field : savedLanguagesByField.entrySet()) {
                    String fieldPath = field.getKey();
                    // Removal events of a deleted field (or form, or language) point
                    // at a gone path: nothing to re-align.
                    if (!systemSession.nodeExists(fieldPath)) {
                        continue;
                    }

                    // One save per field: a failure must never discard the fields
                    // already re-aligned before it in this batch, nor poison the
                    // later saves.
                    try {
                        JCRNodeWrapper fieldNode = systemSession.getNode(fieldPath);
                        if (ManualOptionsLanguageSync.sync(fieldNode, field.getValue())) {
                            systemSession.save();
                            log.debug("[ManualOptionsLanguageSync] Re-aligned '{}'", fieldPath);
                        }
                    } catch (RepositoryException e) {
                        log.warn("[ManualOptionsLanguageSync] Failed to re-align '{}': {}",
                                fieldPath, e.getMessage());
                        // Drop the half-applied changes, or every later save would
                        // re-throw them.
                        systemSession.refresh(false);
                    }
                }

                return null;
            });
        } catch (RepositoryException e) {
            log.warn("[ManualOptionsLanguageSync] Failed to re-align options: {}", e.getMessage());
        }
    }
}
