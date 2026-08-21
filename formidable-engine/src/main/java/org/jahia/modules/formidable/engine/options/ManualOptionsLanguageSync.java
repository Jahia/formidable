package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * carries, and inherits the master entry otherwise. Content that diverged
 * before this sync existed is re-aligned the next time its field is saved.
 *
 * Works on the j:translation_* subnodes directly (the i18n storage, the same
 * access the options content migration uses), so one system session covers
 * every language. Idempotent: aligned translations rewrite nothing, which also
 * terminates the observation loop the sync's own writes re-enter.
 */
public final class ManualOptionsLanguageSync {

    private static final Logger log = LoggerFactory.getLogger(ManualOptionsLanguageSync.class);

    private static final String MANUAL_OPTIONS_MIXIN = "fmdbmix:manualOptions";
    private static final String OPTIONS_PROPERTY = "fmdb:options";
    private static final String TRANSLATION_NODES_PATTERN = "j:translation_*";
    private static final String LANGUAGE_PROPERTY = "jcr:language";

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
                masterOptions = readOptions(translation);
            } else {
                otherTranslations.add(translation);
            }
        }

        // No master list means nothing to align to: an untouched default language
        // must never be overwritten by whatever a translation carries.
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
     * already exists there.
     */
    private static boolean alignTranslation(Node translation, List<String> masterOptions, String fieldPath)
            throws RepositoryException {
        List<String> current = readOptions(translation);
        Map<String, String> currentByValue = new HashMap<>();
        for (String raw : current) {
            String value = optionValue(raw);
            if (value != null) {
                currentByValue.putIfAbsent(value, raw);
            }
        }

        List<String> aligned = new ArrayList<>(masterOptions.size());
        for (String masterRaw : masterOptions) {
            String value = optionValue(masterRaw);
            String own = value != null ? currentByValue.get(value) : null;
            aligned.add(own != null ? own : masterRaw);
        }

        if (aligned.equals(current)) {
            return false;
        }

        translation.setProperty(OPTIONS_PROPERTY, aligned.toArray(new String[0]));
        log.info("[ManualOptionsLanguageSync] Re-aligned the options of '{}' ({})",
                fieldPath, translation.getName());
        return true;
    }

    private static List<String> readOptions(Node translation) throws RepositoryException {
        List<String> options = new ArrayList<>();
        if (!translation.hasProperty(OPTIONS_PROPERTY)) {
            return options;
        }

        for (Value value : translation.getProperty(OPTIONS_PROPERTY).getValues()) {
            options.add(value.getString());
        }

        return options;
    }

    /** The identity of one stored entry; null for unparseable JSON (kept as-is). */
    private static String optionValue(String rawOption) {
        try {
            return new JSONObject(rawOption).optString("value", null);
        } catch (JSONException e) {
            return null;
        }
    }
}
