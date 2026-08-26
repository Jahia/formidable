package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.LANGUAGE_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.OPTIONS_PROPERTY;
import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.TRANSLATION_NODES_PATTERN;

/**
 * The single reading of a manual option entry's storage, shared by everything
 * that interprets fmdb:options — the language sync, the forged-value allowed
 * set — so a storage-format evolution has one parser to update.
 */
public final class ManualOptionEntries {

    private ManualOptionEntries() {
    }

    /**
     * The identity of one stored JSON entry, exactly as stored (no trimming: the
     * sync pairs entries positionally and must not merge near-equal values);
     * null for unparseable JSON. Policy — trimming, dropping empties — belongs
     * to each caller.
     */
    public static String value(String rawOption) {
        try {
            return new JSONObject(rawOption).optString("value", null);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * One aligned entry: the master's identity (value AND default selection — form
     * behavior, not content) with the language's own label. Hand-built in the exact
     * shape the editor's JSON.stringify produces ({"value","label","selected"} in
     * that order), so re-running the sync on an aligned translation reproduces
     * byte-identical entries and stays idempotent.
     *
     * @param masterLabelWhenUntranslated what an entry with no label of its own is
     *        given — see the two align* methods
     */
    private static String withMasterIdentity(String masterRaw, String ownRaw,
            boolean masterLabelWhenUntranslated) {
        try {
            JSONObject master = new JSONObject(masterRaw);
            String label = ownRaw != null ? new JSONObject(ownRaw).optString("label", "") : "";
            if (masterLabelWhenUntranslated && label.trim().isEmpty()) {
                label = master.optString("label", "");
            }

            return "{\"value\":" + JSONObject.quote(master.optString("value", ""))
                    + ",\"label\":" + JSONObject.quote(label)
                    + ",\"selected\":" + master.optBoolean("selected", false) + "}";
        } catch (JSONException e) {
            return masterRaw;
        }
    }

    /**
     * The entries to STORE in one language: the master's values, order and default
     * selections, with that language's own label wherever the value already exists
     * there and an EMPTY label everywhere else. A translation is never pre-filled
     * with the master's words — a copied label cannot be told apart from a translated
     * one, by the contributor scanning the list or by a translation tool, and it would
     * have to be erased before it can be typed over.
     */
    public static List<String> alignForStorage(List<String> masterOptions, List<String> ownOptions) {
        return align(masterOptions, ownOptions, false);
    }

    /**
     * The entries to RENDER in one language: same identity, but an entry not
     * translated yet falls back to the master's label — a form must never offer a
     * blank choice. The fallback is per entry and keyed on a BLANK label, which is
     * exactly what {@link #alignForStorage} leaves behind, so a half-translated list
     * renders the translated labels and the master's words for the rest.
     */
    public static List<String> alignForDisplay(List<String> masterOptions, List<String> ownOptions) {
        return align(masterOptions, ownOptions, true);
    }

    /**
     * Same-value entries are consumed positionally (a queue per value), so two master
     * rows sharing a value — including two rows whose value is still empty — each keep
     * their own translation.
     *
     * Pure, and the single expression of the alignment rule: the save-time
     * re-alignment and the display-time read share it, so a rendered form and a
     * validated submission cannot disagree on the identity.
     */
    private static List<String> align(List<String> masterOptions, List<String> ownOptions,
            boolean masterLabelWhenUntranslated) {
        Map<String, Deque<String>> ownByValue = new HashMap<>();
        for (String raw : ownOptions) {
            String value = value(raw);
            if (value != null) {
                ownByValue.computeIfAbsent(value, unused -> new ArrayDeque<>()).addLast(raw);
            }
        }

        List<String> aligned = new ArrayList<>(masterOptions.size());
        for (String masterRaw : masterOptions) {
            String value = value(masterRaw);
            Deque<String> own = value != null ? ownByValue.get(value) : null;
            aligned.add(withMasterIdentity(masterRaw, own != null ? own.pollFirst() : null,
                    masterLabelWhenUntranslated));
        }

        return aligned;
    }

    /** The raw entries of one language's translation subnode, in stored order. */
    public static List<String> readOptions(Node translation) throws RepositoryException {
        List<String> options = new ArrayList<>();
        if (!translation.hasProperty(OPTIONS_PROPERTY)) {
            return options;
        }

        for (Value value : translation.getProperty(OPTIONS_PROPERTY).getValues()) {
            options.add(value.getString());
        }

        return options;
    }

    /** The j:translation_* subnode of one language, or null when never authored. */
    public static Node findTranslation(JCRNodeWrapper fieldNode, String language) throws RepositoryException {
        NodeIterator translations = fieldNode.getNodes(TRANSLATION_NODES_PATTERN);
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            if (translation.hasProperty(LANGUAGE_PROPERTY)
                    && language.equals(translation.getProperty(LANGUAGE_PROPERTY).getString())) {
                return translation;
            }
        }

        return null;
    }
}
