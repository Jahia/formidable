package org.jahia.modules.formidable.content.action;

import org.jahia.modules.formidable.content.mapping.ConfigException;
import org.jahia.modules.formidable.content.mapping.Mappings;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

/**
 * Readers of an action node's properties with explicit defaults. Static, stateless. A property that cannot be read is a
 * {@link ConfigException}, never a silent default: an action that ran with half of its mappings because one read failed
 * would write something the author did not ask for.
 */
final class ActionProps {

    private ActionProps() {
    }

    /**
     * @param node the action node
     * @param name the property
     * @return the trimmed string value, or {@code null} when absent or blank
     * @throws ConfigException when the property cannot be read
     */
    static String string(JCRNodeWrapper node, String name) throws ConfigException {
        try {
            if (!node.hasProperty(name)) {
                return null;
            }
            String value = node.getProperty(name).getString();
            return value == null || value.isBlank() ? null : value.trim();
        } catch (RepositoryException | RuntimeException e) {
            throw new ConfigException("The setting '" + name + "' of " + node.getPath() + " could not be read", e);
        }
    }

    /**
     * @param node         the action node
     * @param name         the property
     * @param defaultValue the value when the property is absent
     * @return the boolean value
     * @throws ConfigException when the property cannot be read
     */
    static boolean bool(JCRNodeWrapper node, String name, boolean defaultValue) throws ConfigException {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getBoolean() : defaultValue;
        } catch (RepositoryException | RuntimeException e) {
            throw new ConfigException("The setting '" + name + "' of " + node.getPath() + " could not be read", e);
        }
    }

    /**
     * @param node the action node
     * @param name a multi-valued string property
     * @return its non-blank lines in order (a value holding several lines counts as several), empty when absent
     * @throws ConfigException when the property cannot be read
     */
    static List<String> lines(JCRNodeWrapper node, String name) throws ConfigException {
        try {
            if (!node.hasProperty(name)) {
                return List.of();
            }
            JCRPropertyWrapper property = node.getProperty(name);
            List<String> values = new ArrayList<>();
            if (property.isMultiple()) {
                for (JCRValueWrapper value : property.getValues()) {
                    values.add(value.getString());
                }
            } else {
                values.add(property.getString());
            }
            return Mappings.flatten(values);
        } catch (RepositoryException | RuntimeException e) {
            throw new ConfigException("The setting '" + name + "' of " + node.getPath() + " could not be read", e);
        }
    }

    /**
     * @param node the action node
     * @param name a weak-reference property
     * @return the node it points to, in the action's own session, or {@code null} when the property is empty
     * @throws ConfigException when it points to a node that does not exist (in the live workspace: not published?)
     */
    static JCRNodeWrapper reference(JCRNodeWrapper node, String name) throws ConfigException {
        try {
            if (!node.hasProperty(name)) {
                return null;
            }
            JCRNodeWrapper target = (JCRNodeWrapper) node.getProperty(name).getNode();
            if (target == null) {
                throw new ConfigException("The setting '" + name + "' of " + node.getPath() + " points to a node that does not exist in the live workspace: publish it");
            }
            return target;
        } catch (RepositoryException | RuntimeException e) {
            throw new ConfigException("The setting '" + name + "' of " + node.getPath() + " points to a node that cannot be read in the live workspace: publish it", e);
        }
    }
}
