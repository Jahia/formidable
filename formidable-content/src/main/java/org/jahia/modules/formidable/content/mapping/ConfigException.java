package org.jahia.modules.formidable.content.mapping;

/**
 * The action is misconfigured: a malformed mapping line, an unknown property, a node type that cannot be created. The
 * message is for the server log (it names the setting and the line); the visitor is only ever told that the form is
 * unavailable, because there is nothing the visitor can do about it.
 *
 * <p>Immutable.</p>
 */
public final class ConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what the log says: the setting, the line, what is wrong
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * @param message what the log says
     * @param cause   the platform failure behind it
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
