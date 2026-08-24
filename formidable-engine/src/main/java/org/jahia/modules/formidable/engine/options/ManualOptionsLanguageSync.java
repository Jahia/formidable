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
 * share one single set of values, in one order, with one default selection
 * (form behavior travels with the value). Only the label is editorial content
 * that varies per language.
 *
 * The site's default language is the authority: whenever fmdb:options is saved,
 * every EXISTING translation is re-aligned on the master's values, order and
 * count. A language nobody translated yet is deliberately left alone — in
 * Jahia, starting a translation is the contributor's gesture (authoring, or
 * Content Editor's "Copy a language"), never a server-side side effect. A
 * language keeps its own label for a value it already carries — entries
 * sharing one value pair up positionally, so duplicated (or still-empty)
 * values never collapse onto one translation. Content that diverged before
 * this sync existed is re-aligned the next time its field is saved.
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
            List<String> current = ManualOptionEntries.readOptions(translation);
            if (current.isEmpty()) {
                // The language exists (a translated title, an editor visit) but its
                // options were never authored: stays untranslated, nothing to align.
                continue;
            }

            if (!carriesRealEntry(current)) {
                // Only valueless rows: the accidental leftovers of an "add" clicked
                // outside the default language, where no value can ever be typed.
                // That is noise, not a translation — clean it so the language stays
                // untranslated instead of silently adopting the master's entries.
                translation.setProperty(OPTIONS_PROPERTY, (String[]) null);
                log.info("[ManualOptionsLanguageSync] Cleaned the valueless options of '{}' ({})",
                        fieldNode.getPath(), translation.getName());
                updated = true;
                continue;
            }

            updated |= alignTranslation(translation, current, masterOptions, fieldNode.getPath());
        }

        return updated;
    }

    /** True when at least one entry carries a real (non-blank) value. */
    private static boolean carriesRealEntry(List<String> options) {
        for (String raw : options) {
            String value = ManualOptionEntries.value(raw);
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }

        return false;
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
        String sourceLanguage = null;
        List<String> sourceOptions = null;
        for (Node translation : otherTranslations) {
            List<String> options = ManualOptionEntries.readOptions(translation);
            // Valueless rows are noise, never an identity to seed from.
            if (!carriesRealEntry(options)) {
                continue;
            }

            String language = translation.hasProperty(LANGUAGE_PROPERTY)
                    ? translation.getProperty(LANGUAGE_PROPERTY).getString()
                    : "";
            if (sourceLanguage == null || language.compareTo(sourceLanguage) < 0) {
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
    private static boolean alignTranslation(Node translation, List<String> current, List<String> masterOptions,
            String fieldPath) throws RepositoryException {
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
