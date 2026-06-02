package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.securityfilter.PermissionService;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point for Formidable form submissions.
 *
 * Registered via OSGi HTTP Whiteboard outside the Jahia render chain so that
 * this servlet is the first consumer of the multipart request stream — solving
 * the Guest-user problem where Jahia's FileUpload filter would otherwise
 * consume the stream before any processing can occur.
 *
 * URL: /modules/formidable-engine/form-submit
 *
 * All submission logic lives in {@link FormSubmissionPipeline}.
 * Error codes returned to clients are documented in docs/error-codes.md.
 */
@Component(
    service = { HttpServlet.class, Servlet.class },
    property = { "alias=/formidable-engine/form-submit" },
    immediate = true
)
public class FormSubmitServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    static final String SUBMIT_API = "formidable-submit";

    private static final Logger log = LoggerFactory.getLogger(FormSubmitServlet.class);

    private final AtomicReference<FormidableConfigService> config = new AtomicReference<>();
    private final AtomicReference<PermissionService> permissionService = new AtomicReference<>();
    private final List<FormAction> formActions = new CopyOnWriteArrayList<>();

    @Reference
    public void setConfig(FormidableConfigService service) {
        config.set(service);
    }

    @Reference
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService.set(permissionService);
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unbindFormAction")
    protected void bindFormAction(FormAction action) {
        formActions.add(action);
    }

    protected void unbindFormAction(FormAction action) {
        formActions.remove(action);
    }

    @Activate
    public void activate() {
        log.info("FormSubmitServlet activated");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!isRequestAllowed()) {
            String errorCode = ErrorCode.FMDB_011.code();
            if (log.isWarnEnabled()) {
                log.warn("[FormSubmitServlet] Rejected [{}]: request did not match Security Filter scope '{}'",
                        errorCode, SUBMIT_API);
            }
            sendJson(resp, HttpServletResponse.SC_FORBIDDEN, errorCode, null);
            return;
        }

        try {
            createPipeline().run(req);
            sendJson(resp, HttpServletResponse.SC_OK, null, null);
        } catch (SubmissionException e) {
            logSubmissionFailure(e);
            sendJson(resp, e.httpStatus(), e.errorCode.code(), e);
        } catch (Exception e) {
            log.error("[FormSubmitServlet] Unexpected error", e);
            sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ErrorCode.FMDB_500.code(), null);
        }
    }

    FormSubmissionPipeline createPipeline() {
        return new FormSubmissionPipeline(getConfigService(), formActions);
    }

    boolean isRequestAllowed() {
        Map<String, Object> query = new HashMap<>();
        query.put("api", SUBMIT_API);
        return getPermissionService().hasPermission(query);
    }

    private FormidableConfigService getConfigService() {
        FormidableConfigService service = config.get();
        if (service == null) {
            throw new IllegalStateException("FormidableConfigService is not available.");
        }
        return service;
    }

    private PermissionService getPermissionService() {
        PermissionService service = permissionService.get();
        if (service == null) {
            throw new IllegalStateException("PermissionService is not available.");
        }
        return service;
    }

    private static void logSubmissionFailure(SubmissionException e) {
        String errorCode = e.errorCode.code();
        String message = e.getMessage();
        if (e.httpStatus() >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR && e.getCause() != null) {
            if (log.isErrorEnabled()) {
                log.error("[FormSubmitServlet] Rejected [{}]: {}", errorCode, message, e);
            }
            return;
        }

        if (log.isWarnEnabled()) {
            log.warn("[FormSubmitServlet] Rejected [{}]: {}", errorCode, message);
        }
    }

    private static void sendJson(HttpServletResponse resp, int status, String errorCode, SubmissionException ex) throws IOException {
        JSONObject body = new JSONObject();
        body.put("success", errorCode == null);
        if (errorCode != null) {
            body.put("errorCode", errorCode);
        }
        if (ex != null && ex.hasActionProgress()) {
            body.put("actionsCompleted", ex.actionsCompleted);
            body.put("actionsTotal", ex.actionsTotal);
        }
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.getWriter().write(body.toString());
    }
}
