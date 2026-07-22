package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.javascript.modules.engine.sdk.JSServerExtensionInvoker;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a form action node to a handler registered from a JavaScript module through
 * {@code server.registry.add("formidable-form-action", nodeType, { nodeType, execute })}.
 *
 * The {@code execute} callable receives {@code (actionNode, request, session, parameters, files)}
 * — the same arguments as {@link org.jahia.modules.formidable.engine.api.FormAction#execute} —
 * and must return a plain object {@code {ok: true}} on success or
 * {@code {ok: false, status: number, message: string}} on failure. Exceptions must not cross
 * the language boundary raw; the TypeScript wrapper (registerFormAction in formidable-elements)
 * owns that contract — keep both sides in sync.
 *
 * The mandatory reference to {@link JSServerExtensionInvoker} means this component only
 * activates when a javascript-modules-engine exporting the SDK package is deployed;
 * {@link FormSubmitServlet} references this dispatcher optionally so form submission keeps
 * working (Java actions only) without it.
 */
@Component(service = JsFormActionDispatcher.class, immediate = true)
public class JsFormActionDispatcher {

    static final String REGISTRY_TYPE = "formidable-form-action";

    private static final Logger log = LoggerFactory.getLogger(JsFormActionDispatcher.class);

    private JSServerExtensionInvoker invoker;

    @Reference
    public void setInvoker(JSServerExtensionInvoker invoker) {
        this.invoker = invoker;
    }

    /**
     * Executes the JS action handler registered for {@code nodeType}, if any.
     *
     * @return {@code true} when a matching handler ran successfully, {@code false} when no
     *         handler is registered for this node type
     * @throws FormActionException when the matched handler reported a failure (fail-closed:
     *         malformed results and unexpected errors surface as HTTP 500)
     */
    public boolean tryExecute(String nodeType, JCRNodeWrapper actionNode, HttpServletRequest req,
                              JCRSessionWrapper session, Map<String, List<String>> parameters,
                              List<SubmittedFile> files) throws FormActionException {
        List<Object> results;
        try {
            // Single forEach pass: entries and their callables are only valid inside one
            // pooled GraalVM context, which is recycled on module (un)deploy.
            results = invoker.forEach(REGISTRY_TYPE, (entry, call) -> {
                if (!nodeType.equals(entry.get("nodeType"))) {
                    return null;
                }
                Object result = call.call(entry.get("execute"), actionNode, req, session, parameters, files);
                // forEach drops null handler results; wrap so a (contract-violating) null
                // result still counts as "a handler matched" and fails closed below.
                return new Object[]{result};
            });
        } catch (RuntimeException e) {
            log.error("[JsFormActionDispatcher] JS action for node type '{}' failed unexpectedly.", nodeType, e);
            throw FormActionException.serverError(
                    "JavaScript action for '" + nodeType + "' failed: " + e.getMessage(), e);
        }

        if (results.isEmpty()) {
            return false;
        }
        if (results.size() > 1) {
            log.warn("[JsFormActionDispatcher] {} JS handlers registered for node type '{}'; using the first one.",
                    results.size(), nodeType);
        }

        decodeResult(nodeType, ((Object[]) results.get(0))[0]);
        return true;
    }

    private static void decodeResult(String nodeType, Object result) throws FormActionException {
        if (!(result instanceof Map<?, ?> map)) {
            throw FormActionException.serverError(
                    "JavaScript action for '" + nodeType + "' returned an invalid result.");
        }
        if (Boolean.TRUE.equals(map.get("ok"))) {
            return;
        }

        int status = map.get("status") instanceof Number number ? number.intValue() : 500;
        String message = map.get("message") instanceof String s && !s.isBlank()
                ? s
                : "JavaScript action for '" + nodeType + "' failed.";
        throw new FormActionException(message, status);
    }
}
