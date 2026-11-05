package org.jahia.modules.formidable.engine.api;

/**
 * The sensitive fields ({@code fmdbmix:sensitiveField}, a password) whose values a {@link ContextualFormAction}
 * receives, as its {@link ContextualFormAction#sensitiveFieldsConsumed} answers. Immutable.
 *
 * <p>Only two selections exist: {@link #none()}, the default, and {@link #all()}, every sensitive field of the
 * submitted form. A selection of fields by name may come later as an added factory, with no change to the method
 * that returns the selection.</p>
 *
 * <p>The constructor is private: a module returns one of the factories' instances and cannot make another.</p>
 */
public final class SensitiveFields {

    private static final SensitiveFields NONE = new SensitiveFields(false);
    private static final SensitiveFields ALL = new SensitiveFields(true);

    private final boolean everyField;

    private SensitiveFields(boolean everyField) {
        this.everyField = everyField;
    }

    /** No sensitive field: the action receives no sensitive value. */
    public static SensitiveFields none() {
        return NONE;
    }

    /** Every sensitive field of the submitted form. */
    public static SensitiveFields all() {
        return ALL;
    }

    /** Whether this selects every sensitive field of the form. */
    public boolean selectsAll() {
        return everyField;
    }

    @Override
    public String toString() {
        return everyField ? "SensitiveFields[all]" : "SensitiveFields[none]";
    }
}
