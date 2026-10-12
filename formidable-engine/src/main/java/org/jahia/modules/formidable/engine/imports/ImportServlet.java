package org.jahia.modules.formidable.engine.imports;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.jahia.modules.formidable.engine.util.FormidableJcrConstants;
import org.jahia.modules.formidable.engine.config.formsimport.FormsImportConfigService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.securityfilter.PermissionService;
import org.json.JSONException;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jahia.modules.formidable.engine.util.FormidableJcrConstants.SITES;

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
 * The import is open to the administrators of the site, who hold {@code site-admin} on its node: it
 * writes the contents of the site and its results. A JSON answer every time.
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
    /** The permission of the administrators of a site, held on its node. */
    static final String SITE_ADMIN_PERMISSION = "site-admin";
    private static final Pattern JOB_PATH = Pattern.compile("^/jobs/([A-Za-z0-9_.-]+)(/import)?$");
    private static final String SETTINGS_PATH = "/settings";
    private static final String JOBS_PATH = "/jobs";
    private static final String REFUSED_UPLOAD = "the upload was refused: ";
    private static final Logger log = LoggerFactory.getLogger(ImportServlet.class);

    private final transient AtomicReference<FormsImportConfigService> config = new AtomicReference<>();
    private final transient AtomicReference<ImportJobs> jobs = new AtomicReference<>();
    private final transient AtomicReference<PermissionService> permissionService = new AtomicReference<>();

    /** A request once routed: the site it names, the user's session on edit, and the job its path holds, if any. */
    private record Call(HttpServletRequest request, String site, JCRSessionWrapper user, String jobId) {
    }

    @FunctionalInterface
    private interface Handler {
        JSONObject handle(Call call) throws IOException, RepositoryException, NotFound, Refused, ImportJobs.RefusedException;
    }

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String path = pathOf(req);
        String jobId = jobIdOf(path, false);
        if (SETTINGS_PATH.equals(path)) {
            handle(req, resp, null, false, this::settings);
        } else if (jobId != null) {
            handle(req, resp, jobId, true, this::job);
        } else {
            notFound(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String path = pathOf(req);
        String jobId = jobIdOf(path, true);
        if (JOBS_PATH.equals(path)) {
            handle(req, resp, null, true, this::upload);
        } else if (jobId != null) {
            handle(req, resp, jobId, true, this::startImport);
        } else {
            notFound(resp);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        String jobId = jobIdOf(pathOf(req), false);
        if (jobId != null) {
            handle(req, resp, jobId, true, this::close);
        } else {
            notFound(resp);
        }
    }

    // --- the handlers ---

    private JSONObject settings(Call call) throws RepositoryException {
        boolean enabled = config().isImportButtonEnabled();
        boolean allowed = enabled && isAllowed(call.site(), call.user());
        JSONObject settings = new JSONObject()
                .put("enabled", enabled)
                .put("allowed", allowed)
                .put("maxFileSizeMb", config().getMaxFileSizeMb());
        if (allowed) {
            Optional<ImportJobs.JobView> open = jobs().open(call.site());
            // an explicit null: JSONObject drops the key for a Java null
            settings.put("job", open.<Object>map(ImportServlet::json).orElse(JSONObject.NULL));
        }
        return settings;
    }

    private JSONObject job(Call call) throws RepositoryException, NotFound {
        return jobs().get(call.site(), call.jobId())
                .map(ImportServlet::json)
                .orElseThrow(() -> new NotFound("the job does not exist"));
    }

    /**
     * The upload goes to the disk first, so that a file above the bound is refused here, with its reason:
     * read straight into the repository, the limit would surface as a resource without data.
     */
    private JSONObject upload(Call call) throws IOException, RepositoryException, Refused {
        HttpServletRequest req = call.request();
        if (!ServletFileUpload.isMultipartContent(req)) {
            throw new Refused(HttpServletResponse.SC_BAD_REQUEST, "the export must be uploaded as a multipart form, in the field 'file'");
        }
        ServletFileUpload upload = new ServletFileUpload();
        upload.setFileSizeMax(config().getMaxFileSizeBytes());
        Path copy = Files.createTempFile("forms-import-upload-", ".zip");
        try {
            FileItemIterator items = upload.getItemIterator(req);
            while (items.hasNext()) {
                FileItemStream item = items.next();
                if (!item.isFormField() && FILE_FIELD.equals(item.getFieldName())) {
                    try (InputStream stream = item.openStream()) {
                        Files.copy(stream, copy, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try (InputStream stored = Files.newInputStream(copy)) {
                        return json(jobs().create(call.site(), item.getName(), stored, item.getContentType()));
                    }
                }
            }
        } catch (FileUploadException e) {
            throw new Refused(HttpServletResponse.SC_BAD_REQUEST, REFUSED_UPLOAD + e.getMessage());
        } catch (FileUploadBase.FileUploadIOException e) {
            // the size bound, raised while the stream is read
            throw new Refused(HttpServletResponse.SC_BAD_REQUEST, REFUSED_UPLOAD + e.getCause().getMessage());
        } finally {
            Files.deleteIfExists(copy);
        }
        throw new Refused(HttpServletResponse.SC_BAD_REQUEST, "no file in the field 'file'");
    }

    private JSONObject startImport(Call call) throws IOException, RepositoryException, ImportJobs.RefusedException, Refused {
        return json(jobs().startImport(call.site(), call.jobId(), choicesOf(call.request())));
    }

    /**
     * The choices of the review, {@code {"choices":{"contact-us":"create"}}} in a JSON body; none when the
     * request carries no JSON, which leaves every form on the default.
     */
    private static Map<String, ImportChoice> choicesOf(HttpServletRequest req) throws IOException, Refused {
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return Map.of();
        }
        String body;
        try (BufferedReader reader = req.getReader()) {
            body = reader.lines().collect(Collectors.joining());
        }
        if (body.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject choices = new JSONObject(body).optJSONObject("choices");
            return choices == null ? Map.of() : ImportChoice.fromJson(choices.toString());
        } catch (JSONException | IllegalArgumentException e) {
            throw new Refused(HttpServletResponse.SC_BAD_REQUEST, "the choices must name, per source form, resultsOnly or create: " + e.getMessage());
        }
    }

    private JSONObject close(Call call) throws RepositoryException, ImportJobs.RefusedException {
        jobs().close(call.site(), call.jobId());
        return new JSONObject().put("closed", true);
    }

    // --- the plumbing ---

    private void handle(HttpServletRequest req, HttpServletResponse resp, String jobId, boolean needsImport, Handler handler) {
        try {
            if (!isRequestAllowed()) {
                throw new Refused(HttpServletResponse.SC_FORBIDDEN, "not allowed");
            }
            String site = req.getParameter(SITE_PARAMETER);
            JCRSessionWrapper user = JCRSessionFactory.getInstance().getCurrentUserSession(FormidableJcrConstants.WORKSPACE_EDIT);
            if (site == null || site.isBlank() || !user.nodeExists(SITES + site)) {
                throw new NotFound("the site does not exist");
            }
            if (needsImport && (!config().isImportButtonEnabled() || !isAllowed(site, user))) {
                throw new Refused(HttpServletResponse.SC_FORBIDDEN, "the import is not enabled, or not for this user");
            }
            write(resp, HttpServletResponse.SC_OK, handler.handle(new Call(req, site, user, jobId)));
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

    /** The administrators of the site: the import writes its contents, in edit, and its results, in live. */
    private static boolean isAllowed(String site, JCRSessionWrapper user) throws RepositoryException {
        return user.getNode(SITES + site).hasPermission(SITE_ADMIN_PERMISSION);
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

    /** The job id of a {@code /jobs/<id>} path, or of a {@code /jobs/<id>/import} one when the start is wanted; else null. */
    private static String jobIdOf(String path, boolean start) {
        Matcher job = JOB_PATH.matcher(path);
        return job.matches() && (job.group(2) != null) == start ? job.group(1) : null;
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

    private static void write(HttpServletResponse resp, int status, JSONObject body) {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            resp.getWriter().write(body.toString());
        } catch (IOException e) {
            log.warn("[FormsImport] The answer could not be written", e);
        }
    }

    private static void notFound(HttpServletResponse resp) {
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
