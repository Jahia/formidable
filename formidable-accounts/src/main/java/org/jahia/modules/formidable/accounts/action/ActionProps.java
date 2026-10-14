package org.jahia.modules.formidable.accounts.action;

import org.jahia.services.content.JCRNodeWrapper;

/**
 * Null-safe readers of an action node's properties, with explicit defaults. Static, stateless.
 */
final class ActionProps {

    private ActionProps() {
    }

    /** @return the trimmed string value, or {@code null} when absent, blank or unreadable */
    static String string(JCRNodeWrapper node, String name) {
        try {
            if (!node.hasProperty(name)) {
                return null;
            }
            String value = node.getProperty(name).getString();
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** @return the boolean value, or {@code defaultValue} when absent or unreadable */
    static boolean bool(JCRNodeWrapper node, String name, boolean defaultValue) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getBoolean() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** @return the long value, or {@code defaultValue} when absent or unreadable */
    static long number(JCRNodeWrapper node, String name, long defaultValue) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getLong() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
