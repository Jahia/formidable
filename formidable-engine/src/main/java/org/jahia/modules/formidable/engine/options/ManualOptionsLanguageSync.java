package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LANGUAGE_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.MANUAL_OPTIONS_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.OPTIONS_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.TRANSLATION_NODES_PATTERN;

/**
 * Keeps the manual options of a choice field coherent across languages. An
 * option's VALUE is its identity — submissions store it, conditional logic
 * matches it, the forged-value validation checks it — so all languages must
 * share one single set of values, in one order. Only the label and the default
 * selection are editorial content that varies per language.
 *
 * The site's default language is the authority: whenever fmdb:options is saved,
 * every other language is re-aligned on the master's values, order and count.
 * A language keeps its own label and selected flag for a value it already
 * carries — entries sharing one value pair up positionally, so duplicated (or
 * still-empty) values never collapse onto one translation. Content that
 * diverged before this sync existed is re-aligned the next time its field is
 * saved.
 *
 * Works on the j:translation_* subnodes directly (the i18n storage, the same
 * access the options content migration uses), so one system session covers
 * every language. Idempotent: aligned translations rewrite nothing, which also
 * terminates the observation loop the sync's own writes re-enter.
 */
public final class ManualOptionsLanguageSync {

    private static final Logger log = LoggerFactory.getLogger(ManualOptionsLanguageSync.class);

    private ManualOptionsLanguageSync() {
    }

    /**
     * @return true when at least one language was re-aligned (the session then
     *         carries unsaved changes)
     */
    public static boolean sync(JCRNodeWrapper fieldNode) throws RepositoryException {
        if (!fieldNode.isNodeType(MANUAL_OPTIONS_MIXIN)) {
            return false;
        }

        String masterLanguage = fieldNode.getResolveSite() != null
                ? fieldNode.getResolveSite().getDefaultLanguage()
                : null;
        if (masterLanguage == null) {
            return false;
        }

        List<String> masterOptions = null;
        List<Node> otherTranslations = new ArrayList<>();
        NodeIterator translations = fieldNode.getNodes(TRANSLATION_NODES_PATTERN);
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            String language = translation.hasProperty(LANGUAGE_PROPERTY)
                    ? translation.getProperty(LANGUAGE_PROPERTY).getString()
                    : null;
            if (masterLanguage.equals(language)) {
                masterOptions = ManualOptionEntries.readOptions(translation);
            } else {
                otherTranslations.add(translation);
            }
        }

        // No master list means nothing to align to: a field authored only in a
        // non-default language keeps its own values as the identity, and an
        // untouched default language never overwrites what a translation carries.
        if (masterOptions == null || masterOptions.isEmpty()) {
            return false;
        }

        boolean updated = false;
        for (Node translation : otherTranslations) {
            updated |= alignTranslation(translation, masterOptions, fieldNode.getPath());
        }

        return updated;
    }

    /**
     * Rewrites one language's entries as the master's values in the master's
     * order, keeping that language's label and selected flag wherever the value
     * already exists there. Same-value entries are consumed positionally (a
     * queue per value), so two master rows sharing a value — including two rows
     * whose value is still empty — each keep their own translation.
     */
    private static boolean alignTranslation(Node translation, List<String> masterOptions, String fieldPath)
            throws RepositoryException {
        List<String> current = ManualOptionEntries.readOptions(translation);
        Map<String, Deque<String>> currentByValue = new HashMap<>();
        for (String raw : current) {
            String value = ManualOptionEntries.value(raw);
            if (value != null) {
                currentByValue.computeIfAbsent(value, unused -> new ArrayDeque<>()).addLast(raw);
            }
        }

        List<String> aligned = new ArrayList<>(masterOptions.size());
        for (String masterRaw : masterOptions) {
            String value = ManualOptionEntries.value(masterRaw);
            Deque<String> own = value != null ? currentByValue.get(value) : null;
            String kept = own != null ? own.pollFirst() : null;
            aligned.add(kept != null ? kept : masterRaw);
        }

        if (aligned.equals(current)) {
            return false;
        }

        translation.setProperty(OPTIONS_PROPERTY, aligned.toArray(new String[0]));
        log.info("[ManualOptionsLanguageSync] Re-aligned the options of '{}' ({})",
                fieldPath, translation.getName());
        return true;
    }
}
