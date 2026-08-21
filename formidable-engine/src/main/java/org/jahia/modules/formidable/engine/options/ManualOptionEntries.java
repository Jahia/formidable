package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.List;

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
