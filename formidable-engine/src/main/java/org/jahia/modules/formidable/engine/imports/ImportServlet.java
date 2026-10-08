package org.jahia.modules.formidable.engine.imports;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.jahia.modules.formidable.engine.util.FormidableJcrConstants;
import org.jahia.modules.formidable.engine.actions.form.storage.SaveToJcrFormAction;
import org.jahia.modules.formidable.engine.config.formsimport.FormsImportConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.securityfilter.PermissionService;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The endpoint of the import dialog of the Results page (docs/architecture/forms-import.md, "Running
 * it"), under {@code /modules/formidable-engine/import}, every call with the site in {@code ?site=}:
 * <ul>
 * <li>{@code GET /settings}: whether the button shows for this user, the size bound, and the job to
 * open on, running or not yet closed;</li>
 * <li>{@code POST /jobs}: the upload (multipart, field {@code file}), which starts the dry run;</li>
 * <li>{@code GET /jobs/<id>}: the state and the report of a job, polled by the dialog;</li>
 * <li>{@code POST /jobs/<id>/import}: starts the import of a reviewed dry run;</li>
 * <li>{@code DELETE /jobs/<id>}: closes the job, a dry run cancelled or a report read.</li>
 * </ul>
 * The import is open to the users who may write what it writes: the contents of the site in edit and
 * its results in live. A JSON answer every time.
 */
@Component(
        service = {HttpServlet.class, Servlet.class},
        property = {"alias=/formidable-engine/import"},
        immediate = true
)
public class ImportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    static final String IMPORT_API = "formidable-import";
    static final String SITE_PARAMETER = "site";
    static final String FILE_FIELD = "file";
    private static final Pattern JOB_PATH = Pattern.compile("^/jobs/([A-Za-z0-9_.-]+)(/import)?$");
    private static final String SETTINGS_PATH = "/settings";
    private static final String JOBS_PATH = "/jobs";
    private static final String JCR_ADD_CHILD_NODES = "jcr:addChildNodes";
    private static final Logger log = LoggerFactory.getLogger(ImportServlet.class);

    private final transient AtomicReference<FormsImportConfigService> config = new AtomicReference<>();
    private final transient AtomicReference<ImportJobs> jobs = new AtomicReference<>();
    private final transient AtomicReference<PermissionService> permissionService = new AtomicReference<>();

    @Reference
    public void setConfig(FormsImportConfigService service) {
        config.set(service);
    }

    @Reference
    public void setJobs(ImportJobs service) {
        jobs.set(service);
    }

    @Reference
    public void setPermissionService(PermissionService service) {
        permissionService.set(service);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathOf(req);
        if (SETTINGS_PATH.equals(path)) {
            handle(req, resp, false, (site, user) -> settings(site, user));
            return;
        }
        Matcher job = JOB_PATH.matcher(path);
        if (job.matches() && job.group(2) == null) {
            handle(req, resp, true, (site, user) -> jobs().get(site, job.group(1))
                    .map(ImportServlet::json)
                    .orElseThrow(() -> new NotFound("the job does not exist")));
            return;
        }
        notFound(resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathOf(req);
        if (JOBS_PATH.equals(path)) {
            handle(req, resp, true, (site, user) -> json(upload(req, site)));
            return;
        }
        Matcher job = JOB_PATH.matcher(path);
        if (job.matches() && job.group(2) != null) {
            handle(req, resp, true, (site, user) -> json(jobs().startImport(site, job.group(1))));
            return;
        }
        notFound(resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Matcher job = JOB_PATH.matcher(pathOf(req));
        if (job.matches() && job.group(2) == null) {
            handle(req, resp, true, (site, user) -> {
                jobs().close(site, job.group(1));
                return new JSONObject().put("closed", true);
            });
            return;
        }
        notFound(resp);
    }

    // --- the handlers ---

    private JSONObject settings(String site, JCRSessionWrapper user) throws RepositoryException {
        boolean enabled = config().isImportButtonEnabled();
        boolean allowed = enabled && isAllowed(site, user);
        JSONObject settings = new JSONObject()
                .put("enabled", enabled)
                .put("allowed", allowed)
                .put("maxFileSizeMb", config().getMaxFileSizeMb());
        if (allowed) {
            Optional<ImportJobs.JobView> open = jobs().open(site);
            settings.put("job", open.map(ImportServlet::json).orElse(null));
        }
        return settings;
    }

    private ImportJobs.JobView upload(HttpServletRequest req, String site) throws IOException, RepositoryException, Refused {
        if (!ServletFileUpload.isMultipartContent(req)) {
            throw new Refused(HttpServletResponse.SC_BAD_REQUEST, "the export must be uploaded as a multipart form, in the field 'file'");
        }
        ServletFileUpload upload = new ServletFileUpload();
        upload.setFileSizeMax(config().getMaxFileSizeBytes());
        try {
            FileItemIterator items = upload.getItemIterator(req);
            while (items.hasNext()) {
                FileItemStream item = items.next();
                if (!item.isFormField() && FILE_FIELD.equals(item.getFieldName())) {
                    try (InputStream stream = item.openStream()) {
                        return jobs().create(site, item.getName(), stream, item.getContentType());
                    }
                }
            }
        } catch (FileUploadException e) {
            throw new Refused(HttpServletResponse.SC_BAD_REQUEST, "the upload was refused: " + e.getMessage());
        }
        throw new Refused(HttpServletResponse.SC_BAD_REQUEST, "no file in the field 'file'");
    }

    // --- the plumbing ---

    @FunctionalInterface
    private interface Handler {
        JSONObject handle(String site, JCRSessionWrapper user) throws Exception;
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp, boolean needsImport, Handler handler) throws IOException {
        try {
            if (!isRequestAllowed()) {
                throw new Refused(HttpServletResponse.SC_FORBIDDEN, "not allowed");
            }
            String site = req.getParameter(SITE_PARAMETER);
            JCRSessionWrapper user = JCRSessionFactory.getInstance().getCurrentUserSession(FormidableJcrConstants.WORKSPACE_EDIT);
            if (site == null || site.isBlank() || !user.nodeExists("/sites/" + site)) {
                throw new NotFound("the site does not exist");
            }
            if (needsImport && (!config().isImportButtonEnabled() || !isAllowed(site, user))) {
                throw new Refused(HttpServletResponse.SC_FORBIDDEN, "the import is not enabled, or not for this user");
            }
            write(resp, HttpServletResponse.SC_OK, handler.handle(site, user));
        } catch (NotFound e) {
            write(resp, HttpServletResponse.SC_NOT_FOUND, error(e.getMessage()));
        } catch (Refused e) {
            write(resp, e.status, error(e.getMessage()));
        } catch (ImportJobs.RefusedException e) {
            write(resp, HttpServletResponse.SC_CONFLICT, error(e.getMessage()));
        } catch (Exception e) {
            log.error("[FormsImport] {} {} failed", req.getMethod(), req.getRequestURI(), e);
            write(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error("the request failed: " + e.getMessage()));
        }
    }

    /** The user may write the forms (the contents of the site, in edit) and the results (the site, in live). */
    private static boolean isAllowed(String site, JCRSessionWrapper user) throws RepositoryException {
        JCRNodeWrapper contents = user.getNode("/sites/" + site + "/" + ImportWriter.CONTENTS_NODE);
        JCRSessionWrapper live = JCRSessionFactory.getInstance().getCurrentUserSession(FormidableJcrConstants.WORKSPACE_LIVE);
        String resultsPath = "/sites/" + site + "/" + SaveToJcrFormAction.RESULTS_ROOT_NAME;
        JCRNodeWrapper results = live.nodeExists(resultsPath) ? live.getNode(resultsPath) : live.getNode("/sites/" + site);
        return contents.hasPermission(JCR_ADD_CHILD_NODES) && results.hasPermission(JCR_ADD_CHILD_NODES);
    }

    boolean isRequestAllowed() {
        PermissionService permissions = permissionService.get();
        if (permissions == null) {
            return false;
        }
        Map<String, Object> query = new HashMap<>();
        query.put("api", IMPORT_API);
        return permissions.hasPermission(query);
    }

    private static String pathOf(HttpServletRequest req) {
        String path = req.getPathInfo();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private static JSONObject json(ImportJobs.JobView job) {
        return new JSONObject()
                .put("id", job.id())
                .put("state", job.state().stored())
                .put("report", job.report() == null ? JSONObject.NULL : new JSONObject(job.report()))
                .put("message", job.message() == null ? JSONObject.NULL : job.message())
                .put("updated", job.updated().toString());
    }

    private static JSONObject error(String message) {
        return new JSONObject().put("error", message);
    }

    private static void write(HttpServletResponse resp, int status, JSONObject body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.getWriter().write(body.toString());
    }

    private static void notFound(HttpServletResponse resp) throws IOException {
        write(resp, HttpServletResponse.SC_NOT_FOUND, error("no such endpoint"));
    }

    private FormsImportConfigService config() {
        FormsImportConfigService service = config.get();
        if (service == null) {
            throw new IllegalStateException("FormsImportConfigService is not available");
        }
        return service;
    }

    private ImportJobs jobs() {
        ImportJobs service = jobs.get();
        if (service == null) {
            throw new IllegalStateException("ImportJobs is not available");
        }
        return service;
    }

    private static final class NotFound extends Exception {
        NotFound(String message) {
            super(message);
        }
    }

    private static final class Refused extends Exception {
        private final int status;

        Refused(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
