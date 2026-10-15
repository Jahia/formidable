package org.jahia.modules.formidable.content.plan;

import org.jahia.modules.formidable.content.mapping.PairRule;

/**
 * The submission itself cannot become content: a value that does not fit its property, too many files for a single
 * property, a file without its companion. The visitor can fix it, so the action answers with a sentence the visitor
 * reads; the message is for the log. Immutable.
 */
public final class ValueException extends Exception {

    private static final long serialVersionUID = 1L;

    /** What the visitor is told. */
    public enum Kind {
        /** A value is not valid: the generic sentence. */
        BAD_VALUE,
        /** A file is missing its companion: the author's {@code pairRefusalMessage}. */
        PAIR
    }

    private final Kind kind;
    private final transient PairRule rule;

    /**
     * @param kind    what the visitor is told
     * @param message what the log says, never the submitted value
     */
    public ValueException(Kind kind, String message) {
        this(kind, message, null);
    }

    /**
     * @param kind    what the visitor is told
     * @param message what the log says
     * @param rule    the pair rule that was broken, for {@link Kind#PAIR}
     */
    public ValueException(Kind kind, String message, PairRule rule) {
        super(message);
        this.kind = kind;
        this.rule = rule;
    }

    /** @return what the visitor is told */
    public Kind kind() {
        return kind;
    }

    /** @return the pair rule that was broken, or {@code null} */
    public PairRule rule() {
        return rule;
    }
}
