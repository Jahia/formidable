package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FmdbNodeName;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the field actions a form declares: under a field carrying {@code fmdbmix:fieldActions}, the {@code actions}
 * list and its nodes taking {@code fmdbmix:fieldAction}, in list order. The metadata collector calls {@link #read}
 * for every field it registers, so the pipeline reads the repository once; the pre-check endpoint calls
 * {@link #collect} on the form it resolved.
 *
 * <p>On a sensitive field ({@code fmdbmix:sensitiveField}) every action is read with
 * {@link ResolvedFieldAction#onSensitiveField()} set: which of them run is the {@link FieldActionDispatcher}'s call,
 * since it alone knows the registered Java actions and which of them opt in.</p>
 */
public final class FieldActionCollector {

    private FieldActionCollector() {
    }

    /** The actions declared under one field: nothing when the field carries no switch, or an empty list. */
    public static List<ResolvedFieldAction> read(JCRNodeWrapper field) throws RepositoryException {
        if (!field.isNodeType(FmdbMixin.FIELD_ACTIONS) || !field.hasNode(FmdbNodeName.ACTIONS)) {
            return List.of();
        }
        boolean sensitive = field.isNodeType(FmdbMixin.SENSITIVE_FIELD);
        List<ResolvedFieldAction> actions = new ArrayList<>();
        NodeIterator it = field.getNode(FmdbNodeName.ACTIONS).getNodes();
        while (it.hasNext()) {
            Node child = it.nextNode();
            if (child instanceof JCRNodeWrapper action && action.isNodeType(FmdbMixin.FIELD_ACTION)) {
                actions.add(ResolvedFieldAction.read(action, sensitive));
            }
        }
        return List.copyOf(actions);
    }

    /**
     * Marks the actions kept under the name as attached to a sensitive field: every field of a name submits under it,
     * so when one of them carries {@code fmdbmix:sensitiveField} the actions a plain field of the name brought judge
     * the secret too. Both collectors mark a name here, the pre-check's and the submission's.
     */
    public static void markSensitive(Map<String, List<ResolvedFieldAction>> byField, String name) {
        byField.computeIfPresent(name, (n, kept) -> kept.stream().map(ResolvedFieldAction::onASensitiveField).toList());
    }

    /**
     * Every submit-capable field of the form that declares actions, by field name — the first field of a name wins,
     * as it does in the pipeline's whitelist, and a name one of whose fields is sensitive is sensitive. Fields are
     * found anywhere under the form, steps and fieldsets included.
     */
    public static Map<String, List<ResolvedFieldAction>> collect(JCRNodeWrapper form) throws RepositoryException {
        Map<String, List<ResolvedFieldAction>> byField = new LinkedHashMap<>();
        Set<String> sensitiveNames = new HashSet<>();
        walk(form, byField, sensitiveNames);
        // marked once the whole form is walked: a sensitive field may come before the field of its name that has actions
        sensitiveNames.forEach(name -> markSensitive(byField, name));
        return byField;
    }

    private static void walk(JCRNodeWrapper node, Map<String, List<ResolvedFieldAction>> byField, Set<String> sensitiveNames)
            throws RepositoryException {
        if (node.isNodeType(FmdbMixin.FORM_ELEMENT) && !node.isNodeType(FmdbMixin.NON_SUBMITTABLE)) {
            List<ResolvedFieldAction> actions = read(node);
            if (!actions.isEmpty()) {
                byField.putIfAbsent(node.getName(), actions);
            }
            if (node.isNodeType(FmdbMixin.SENSITIVE_FIELD)) {
                sensitiveNames.add(node.getName());
            }
        }
        NodeIterator it = node.getNodes();
        while (it.hasNext()) {
            Node child = it.nextNode();
            if (child instanceof JCRNodeWrapper childNode) {
                walk(childNode, byField, sensitiveNames);
            }
        }
    }
}
