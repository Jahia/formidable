package org.jahia.modules.formidable.engine.actions;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for pluggable form actions.
 * Implement this interface and register the implementation as an OSGi service
 * to make a new action available to {@link org.jahia.modules.formidable.engine.servlet.FormSubmitServlet}.
 */
public interface FormAction {

    /**
     * Returns the primary JCR node type this action handles (e.g. "fmdb:captchaAction").
     */
    String getNodeType();

    /**
     * Executes the action.
     *
     * @param actionNode  the JCR node that holds the action's configuration properties
     * @param req         the current HTTP request (form POST)
     * @param renderContext the Jahia render context
     * @param session     the JCR session
     * @param parameters  decoded request parameters
     * @throws FormActionException if the action fails in a way that should stop processing
     *                             and return an error to the client
     */
    void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException;
}

