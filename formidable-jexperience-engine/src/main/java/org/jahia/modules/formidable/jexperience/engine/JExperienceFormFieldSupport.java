package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JExperienceFormFieldSupport {

    public static final String JEXPERIENCE_PROFILE_PROPERTY = "jExperienceProfileProperty";
    public static final String JEXPERIENCE_PREFILL_FROM_PROFILE = "jExperiencePrefillFromProfile";
    // public static final String JEXPERIENCE_INCLUDE_IN_EVENT = "jExperienceIncludeInEvent";

    private JExperienceFormFieldSupport() {
    }

    public static List<JCRNodeWrapper> collectMappableFieldNodes(JCRNodeWrapper root) throws RepositoryException {
        List<JCRNodeWrapper> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(JCRNodeWrapper node, List<JCRNodeWrapper> result) throws RepositoryException {
        if (resolveFieldShape(node).isPresent()) {
            result.add(node);
        }

        NodeIterator iterator = node.getNodes();
        while (iterator.hasNext()) {
            javax.jcr.Node child = iterator.nextNode();
            if (child instanceof JCRNodeWrapper childNode) {
                collect(childNode, result);
            }
        }
    }

    public static Optional<JExperienceFieldShape> resolveFieldShape(JCRNodeWrapper node) throws RepositoryException {
        String nodeType = node.getPrimaryNodeTypeName();
        return switch (nodeType) {
            case "fmdb:inputText", "fmdb:textarea", "fmdb:radio" ->
                    Optional.of(new JExperienceFieldShape(JExperienceFieldValueKind.STRING, false));
            case "fmdb:inputEmail", "fmdb:select" ->
                    Optional.of(new JExperienceFieldShape(
                            JExperienceFieldValueKind.STRING,
                            readBooleanProperty(node, "multiple")
                    ));
            case "fmdb:checkbox" ->
                    Optional.of(new JExperienceFieldShape(JExperienceFieldValueKind.STRING, true));
            case "fmdb:inputDate", "fmdb:inputDatetimeLocal" ->
                    Optional.of(new JExperienceFieldShape(JExperienceFieldValueKind.DATE, false));
            default -> Optional.empty();
        };
    }

    public static Optional<String> getProfileProperty(JCRNodeWrapper node) throws RepositoryException {
        if (!node.hasProperty(JEXPERIENCE_PROFILE_PROPERTY)) {
            return Optional.empty();
        }

        String value = node.getProperty(JEXPERIENCE_PROFILE_PROPERTY).getString();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(value.trim());
    }

    public static boolean isPrefillFromProfile(JCRNodeWrapper node) throws RepositoryException {
        return readBooleanProperty(node, JEXPERIENCE_PREFILL_FROM_PROFILE);
    }

    // public static boolean isIncludedInEvent(JCRNodeWrapper node) throws RepositoryException {
    //     if (!node.hasProperty(JEXPERIENCE_INCLUDE_IN_EVENT)) {
    //         return true;
    //     }
    //
    //     return node.getProperty(JEXPERIENCE_INCLUDE_IN_EVENT).getBoolean();
    // }

    private static boolean readBooleanProperty(JCRNodeWrapper node, String propertyName) throws RepositoryException {
        return node.hasProperty(propertyName) && node.getProperty(propertyName).getBoolean();
    }
}
