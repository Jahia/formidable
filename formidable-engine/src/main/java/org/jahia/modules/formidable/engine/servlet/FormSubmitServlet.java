package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point for Formidable form submissions.
 *
 * Registered via OSGi HttpService outside the Jahia render chain so that this
 * servlet is the first consumer of the multipart request stream — solving the
 * Guest-user problem where Jahia's FileUpload filter would otherwise consume
 * the stream before any processing can occur.
 *
 * URL: /modules/formidable-engine/form-submit
 *
 * All submission logic lives in {@link FormSubmissionPipeline}.
 * Error codes returned to clients are documented in docs/error-codes.md.
 */
@Component(service = FormSubmitServlet.class, immediate = true)
public class FormSubmitServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(FormSubmitServlet.class);

    static final String ALIAS = "/formidable-engine/form-submit";

    private HttpService httpService;
    private FormidableConfigService config;
    private final List<FormAction> formActions = new CopyOnWriteArrayList<>();

    @Reference
    public void setHttpService(HttpService service) {
        this.httpService = service;
    }

    @Reference
    public void setConfig(FormidableConfigService service) {
        this.config = service;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unbindFormAction")
    protected void bindFormAction(FormAction action) {
        formActions.add(action);
    }

    protected void unbindFormAction(FormAction action) {
        formActions.remove(action);
    }

    @Activate
    public void activate() throws Exception {
        httpService.registerServlet(ALIAS, this, null, null);
        log.info("FormSubmitServlet registered at {}", ALIAS);
    }

    @Deactivate
    public void deactivate() {
        httpService.unregister(ALIAS);
        log.info("FormSubmitServlet unregistered from {}", ALIAS);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            new FormSubmissionPipeline(config, formActions).run(req);
            sendJson(resp, HttpServletResponse.SC_OK, null);
        } catch (SubmissionException e) {
            log.warn("[FormSubmitServlet] Rejected [{}]: {}", e.errorCode.code(), e.getMessage());
            sendJson(resp, e.httpStatus(), e.errorCode.code());
        } catch (Exception e) {
            log.error("[FormSubmitServlet] Unexpected error", e);
            sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ErrorCode.FMDB_500.code());
        }
    }

    private static void sendJson(HttpServletResponse resp, int status, String errorCode) throws IOException {
        JSONObject body = new JSONObject();
        body.put("success", errorCode == null);
        if (errorCode != null) body.put("errorCode", errorCode);
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.getWriter().write(body.toString());
    }
}
