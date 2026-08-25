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
     */
    public static String withMasterIdentity(String masterRaw, String ownRaw) {
        try {
            JSONObject master = new JSONObject(masterRaw);
            String label = master.optString("label", "");
            if (ownRaw != null) {
                label = new JSONObject(ownRaw).optString("label", label);
            }

            return "{\"value\":" + JSONObject.quote(master.optString("value", ""))
                    + ",\"label\":" + JSONObject.quote(label)
                    + ",\"selected\":" + master.optBoolean("selected", false) + "}";
        } catch (JSONException e) {
            return masterRaw;
        }
    }

    /**
     * One language's entries rewritten as the master's values, order and default
     * selections, keeping that language's own label wherever the value already exists
     * there. Same-value entries are consumed positionally (a queue per value), so two
     * master rows sharing a value — including two rows whose value is still empty —
     * each keep their own translation.
     *
     * Pure, and the single expression of the alignment rule: the save-time
     * re-alignment and the display-time read share it, so a rendered form and a
     * validated submission cannot disagree on the identity.
     */
    public static List<String> align(List<String> masterOptions, List<String> ownOptions) {
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
            aligned.add(withMasterIdentity(masterRaw, own != null ? own.pollFirst() : null));
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
