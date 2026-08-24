package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.utils.LanguageCodeConverters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LANGUAGE_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.MANUAL_OPTIONS_MIXIN;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.OPTIONS_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.TRANSLATION_NODES_PATTERN;

/**
 * Keeps the manual options of a choice field coherent across languages. An
 * option's VALUE is its identity — submissions store it, conditional logic
 * matches it, the forged-value validation checks it — so all languages must
 * share one single set of values, in one order, with one default selection
 * (form behavior travels with the value). Only the label is editorial content
 * that varies per language.
 *
 * The site's default language is the authority: whenever fmdb:options is saved,
 * every other site language is re-aligned on the master's values, order and
 * count — a language whose translation subnode does not exist yet gets it
 * created and fed, so every language always has entries whose labels can be
 * translated. A language keeps its own label for a value it already carries —
 * entries sharing one value pair up positionally, so duplicated (or
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

        JCRSiteNode site = fieldNode.getResolveSite();
        String masterLanguage = site != null ? site.getDefaultLanguage() : null;
        if (masterLanguage == null) {
            return false;
        }

        List<String> masterOptions = null;
        List<Node> otherTranslations = new ArrayList<>();
        Set<String> coveredLanguages = new HashSet<>();
        coveredLanguages.add(masterLanguage);
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
                if (language != null) {
                    coveredLanguages.add(language);
                }
            }
        }

        // No master list yet: the first authored language SEEDS it. Its values
        // become the identity right away (labels ride along as the starting point
        // for translation), so the default language is never opened later on an
        // empty mandatory list whose improvised values would re-align — and erase —
        // what the first language authored.
        boolean seeded = false;
        if (masterOptions == null || masterOptions.isEmpty()) {
            masterOptions = seedMaster(fieldNode, masterLanguage, otherTranslations);
            if (masterOptions == null) {
                return false;
            }

            seeded = true;
        }

        boolean updated = seeded;
        for (Node translation : otherTranslations) {
            updated |= alignTranslation(translation, masterOptions, fieldNode.getPath());
        }

        // A site language never authored on this field has no j:translation_*
        // subnode at all: create it and feed it the master entries, otherwise that
        // language opens the editor on an empty mandatory list with nothing to
        // translate — and no way to save.
        Set<String> siteLanguages = site.getLanguages();
        if (siteLanguages != null) {
            for (String language : siteLanguages) {
                if (coveredLanguages.contains(language)) {
                    continue;
                }

                Node created = fieldNode.getOrCreateI18N(LanguageCodeConverters.languageCodeToLocale(language));
                updated |= alignTranslation(created, masterOptions, fieldNode.getPath());
            }
        }

        return updated;
    }

    /**
     * Writes the first authored language's entries onto the default language's
     * translation (created through the platform API when needed). When several
     * languages carry options and none is the default, the first by language code
     * wins — deterministic, and the following alignment makes the others coherent.
     *
     * @return the seeded master entries, or null when no language carries options
     */
    private static List<String> seedMaster(JCRNodeWrapper fieldNode, String masterLanguage,
            List<Node> otherTranslations) throws RepositoryException {
        Node source = null;
        String sourceLanguage = null;
        List<String> sourceOptions = null;
        for (Node translation : otherTranslations) {
            List<String> options = ManualOptionEntries.readOptions(translation);
            if (options.isEmpty()) {
                continue;
            }

            String language = translation.hasProperty(LANGUAGE_PROPERTY)
                    ? translation.getProperty(LANGUAGE_PROPERTY).getString()
                    : "";
            if (source == null || language.compareTo(sourceLanguage) < 0) {
                source = translation;
                sourceLanguage = language;
                sourceOptions = options;
            }
        }

        if (sourceOptions == null) {
            return null;
        }

        Node master = fieldNode.getOrCreateI18N(LanguageCodeConverters.languageCodeToLocale(masterLanguage));
        master.setProperty(OPTIONS_PROPERTY, sourceOptions.toArray(new String[0]));
        log.info("[ManualOptionsLanguageSync] Seeded the default language ({}) options of '{}' from '{}'",
                masterLanguage, fieldNode.getPath(), sourceLanguage);
        return sourceOptions;
    }

    /**
     * Rewrites one language's entries as the master's values, order and default
     * selections (form behavior travels with the value), keeping only that
     * language's label wherever the value already exists there. Same-value entries
     * are consumed positionally (a queue per value), so two master rows sharing a
     * value — including two rows whose value is still empty — each keep their own
     * translation.
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
            aligned.add(ManualOptionEntries.withMasterIdentity(masterRaw, own != null ? own.pollFirst() : null));
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
