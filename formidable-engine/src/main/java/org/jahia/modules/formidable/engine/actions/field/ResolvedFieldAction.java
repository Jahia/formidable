package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.Locale;

/**
 * One field action as the engine runs it: the node's identity and type, and the four settings the contributor
 * wrote on it through {@code fmdbmix:fieldActionFeedback}. Read once per submission or pre-check, so the run
 * itself never goes back to the repository for a setting.
 *
 * @param id              the action node's UUID
 * @param nodeType        its primary type: what binds it to the Java {@code FieldAction} a module registers
 * @param trigger         when the browser asks: on leaving the field, or at submission only
 * @param severity        what a refusal does: block the submission, or warn and let it through
 * @param whenUnavailable what an unanswered check means: the value is accepted, or refused
 * @param onSensitiveField whether the field it is attached to carries {@code fmdbmix:sensitiveField}: the action then
 *                        runs only when its Java type opts in ({@code FieldAction#consumesSensitiveValues}), its verdict
 *                        is never cached, and its message never interpolates the value
 */
public record ResolvedFieldAction(String id, String nodeType, Trigger trigger, Severity severity, Unavailable whenUnavailable,
                                  boolean onSensitiveField) {

    /** When a field action runs from the browser. It always runs again at submission when it blocks. */
    public enum Trigger {
        BLUR, SUBMIT;

        static Trigger of(String value) {
            return "submit".equalsIgnoreCase(value) ? SUBMIT : BLUR;
        }
    }

    /** What a refusal does. */
    public enum Severity {
        BLOCK, WARN;

        static Severity of(String value) {
            return "warn".equalsIgnoreCase(value) ? WARN : BLOCK;
        }
    }

    /** What an unanswered check means. */
    public enum Unavailable {
        ACCEPT, REJECT;

        static Unavailable of(String value) {
            return "reject".equalsIgnoreCase(value) ? REJECT : ACCEPT;
        }
    }

    /** The same action, attached to a sensitive field. */
    public ResolvedFieldAction onASensitiveField() {
        return onSensitiveField ? this : new ResolvedFieldAction(id, nodeType, trigger, severity, whenUnavailable, true);
    }

    /** Whether this action's refusal stops the submission. */
    public boolean blocking() {
        return severity == Severity.BLOCK;
    }

    /**
     * Reads a field-action node. The four settings come with the type — the marker includes
     * {@code fmdbmix:fieldActionFeedback} — so every action has them; a property removed by hand, or a value
     * the choicelist never offered, falls back to its CND default — blur, block, accept — so the engine and the
     * editor agree on what an unset value means. {@code onSensitiveField} says whether the field it is attached to is
     * sensitive.
     */
    public static ResolvedFieldAction read(JCRNodeWrapper node, boolean onSensitiveField) throws RepositoryException {
        return new ResolvedFieldAction(
                node.getIdentifier(),
                node.getPrimaryNodeTypeName(),
                Trigger.of(property(node, FmdbProperty.TRIGGER)),
                Severity.of(property(node, FmdbProperty.SEVERITY)),
                Unavailable.of(property(node, FmdbProperty.WHEN_UNAVAILABLE)),
                onSensitiveField
        );
    }

    private static String property(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getString().trim().toLowerCase(Locale.ROOT) : null;
    }
}
