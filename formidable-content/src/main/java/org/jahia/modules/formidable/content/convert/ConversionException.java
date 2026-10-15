package org.jahia.modules.formidable.content.convert;

/**
 * A submitted value does not fit the property it is mapped to ("abc" for a number, "31/12/2026" for a date), or a
 * property has a type the action does not fill from a field. The message is for the log; the visitor reads one
 * generic sentence. Immutable.
 */
public final class ConversionException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what the log says: the property and what is wrong, never the value itself
     */
    public ConversionException(String message) {
        super(message);
    }
}
