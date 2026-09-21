package org.jahia.modules.formidable.engine.fieldactions;

import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.services.content.JCRNodeWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Renders a field action's {@code hidden.execute} view — the JavaScript way of writing a field action — and hands
 * back its output. A seam: the dispatcher's tests answer a JSON string, the runtime goes through the render service.
 */
@FunctionalInterface
interface ViewRenderer {

    /**
     * @param actionNode the field-action node, read in {@code live}
     * @param request    what the view judges; the runtime hands it to the view as a request attribute
     * @param req        the visitor's request, the context the view renders in
     * @param resp       the visitor's response, which the render chain may touch
     * @return the view's output, or {@code null} when there is no request to render in
     * @throws Exception whatever the render chain throws; the dispatcher reads it as "unavailable"
     */
    String render(JCRNodeWrapper actionNode, FieldActionRequest request, HttpServletRequest req, HttpServletResponse resp)
            throws Exception;
}
