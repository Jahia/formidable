package org.jahia.modules.formidable.engine.actions.form.email;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.util.JcrProps;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.FIELDS_NODE;

/**
 * The rows of the e-mail that {@code fmdb:emailContentAction} sends: one per answered field, in the order of the
 * form, headed by the field's title in the submission's language, a choice shown by the label of its option — the
 * e-mail a person reads, not the field names and option values the browser submitted.
 * <p>
 * A submitted name that no field of the form declares keeps its name and comes last, in name order. A form that
 * cannot be read gives the submitted names and values alone: the summary never costs the e-mail.
 */
final class ContentEmailRows {

    private static final Logger log = LoggerFactory.getLogger(ContentEmailRows.class);

    private ContentEmailRows() {
    }

    /** One row of the e-mail: what heads it, and the values it shows. */
    record Row(String heading, List<String> values) {
    }

    /** The option entries of a choice field, as its view renders them; null when it has none. */
    @FunctionalInterface
    interface OptionsReader {
        String[] options(JCRNodeWrapper fieldNode) throws RepositoryException;
    }

    static List<Row> of(JCRNodeWrapper formNode, Map<String, List<String>> parameters, OptionsReader options) {
        if (formNode != null) {
            try {
                Map<String, List<String>> unread = new TreeMap<>(parameters);
                List<Row> rows = new ArrayList<>();
                if (formNode.hasNode(FIELDS_NODE)) {
                    walk(formNode.getNode(FIELDS_NODE), unread, options, rows);
                }
                unread.forEach((name, values) -> rows.add(new Row(name, values)));
                return rows;
            } catch (RepositoryException | RuntimeException e) {
                log.debug("Could not read the fields of form '{}' for the content e-mail: {}", path(formNode), e.getMessage());
            }
        }
        List<Row> rows = new ArrayList<>();
        new TreeMap<>(parameters).forEach((name, values) -> rows.add(new Row(name, values)));
        return rows;
    }

    // Depth first, in the order of the children: the order in which the form shows its fields, steps and
    // fieldsets included.
    private static void walk(JCRNodeWrapper node, Map<String, List<String>> unread, OptionsReader options, List<Row> rows)
            throws RepositoryException {
        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            if (!(children.nextNode() instanceof JCRNodeWrapper child)) {
                continue;
            }
            if (child.isNodeType(FmdbMixin.FORM_ELEMENT) && !child.isNodeType(FmdbMixin.NON_SUBMITTABLE)) {
                List<String> values = unread.remove(child.getName());
                if (values != null) {
                    rows.add(new Row(heading(child), shown(child, values, options)));
                }
            }
            walk(child, unread, options, rows);
        }
    }

    private static String heading(JCRNodeWrapper field) {
        String title = JcrProps.string(field, "jcr:title", "");
        return title == null || title.isBlank() ? field.getName() : title.trim();
    }

    private static List<String> shown(JCRNodeWrapper field, List<String> values, OptionsReader options)
            throws RepositoryException {
        String[] entries = options.options(field);
        if (entries == null || entries.length == 0) {
            return values;
        }
        Map<String, String> labels = new HashMap<>();
        for (String entry : entries) {
            try {
                JSONObject option = new JSONObject(entry);
                String label = option.optString("label", "");
                if (!label.isBlank()) {
                    labels.putIfAbsent(option.optString("value", ""), label);
                }
            } catch (JSONException e) {
                // An unreadable entry shows nothing of its own: its value stays as submitted.
            }
        }
        return values.stream().map(value -> labels.getOrDefault(value, value)).toList();
    }

    private static String path(JCRNodeWrapper node) {
        try {
            return node.getPath();
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
