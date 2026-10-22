package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * A {@link FormAction} that needs more than the legacy five arguments: the response (to set a cookie), the
 * submission's locale and site, a way to answer the browser, and optionally the values of the sensitive fields.
 *
 * <p>Register it as an OSGi service exactly like a plain action ({@code @Component(service = FormAction.class)}):
 * the pipeline recognises the sub-interface and calls {@link #execute(FormActionContext)} instead of the legacy
 * method. Existing actions are untouched.</p>
 *
 * <p>Threading: a singleton shared by every submission. Keep no per-request or per-user state in fields; the
 * context is the request.</p>
 *
 * <p>How it fails: throw {@link FormActionException}. With {@link FormActionException#refusal} the visitor reads a
 * translated message on the form (error code {@code FMDB-018}); with any other constructor the browser only learns
 * {@code FMDB-008}. Keep refusals generic: a message that tells "unknown e-mail" from "wrong password" is an account
 * enumeration oracle.</p>
 */
public interface ContextualFormAction extends FormAction {

    /**
     * Executes the action.
     *
     * @param context the submission: configuration node, form, request, response, session, locale, values
     * @return what the browser should do next; {@link FormActionResult#none()} for nothing, never {@code null}
     * @throws FormActionException when the action fails and the pipeline must stop
     */
    FormActionResult execute(FormActionContext context) throws FormActionException;

    /**
     * Whether the action reads the values of the fields carrying {@code fmdbmix:sensitiveField}. Declared once, by
     * the type: an action that answers {@code true} receives them in {@link FormActionContext#sensitiveParameters()}
     * and takes the responsibility of never storing, mailing, logging nor returning them; any other never sees them.
     */
    default boolean consumesSensitiveValues() {
        return false;
    }

    /** Never called by the pipeline, which dispatches {@link #execute(FormActionContext)}. */
    @Override
    default void execute(JCRNodeWrapper actionNode, HttpServletRequest req, JCRSessionWrapper session,
                         Map<String, List<String>> parameters, List<SubmittedFile> files) throws FormActionException {
        throw new UnsupportedOperationException(getClass().getName()
                + " is a ContextualFormAction: the pipeline calls execute(FormActionContext)");
    }
}
