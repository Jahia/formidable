package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The inspection of the quarantine tree, for the test suite: a {@code GET} on
 * {@code /modules/formidable-quarantine/inspection?site=<siteKey>&form=<formId>} answers the held submissions of the
 * form as JSON: the state of each, the names of its fields, and of each item its file name, size, SHA-256 and whether
 * its bytes are still there. Never a value, never a sensitive value, never a byte.
 *
 * <p>Server administrators only: any other caller, a site administrator included, gets 403 and nothing else.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=" + InspectionServlet.ALIAS},
        immediate = true)
public class InspectionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(InspectionServlet.class);

    public static final String ALIAS = "/formidable-quarantine/inspection";
    static final String SITE_PARAMETER = "site";
    static final String FORM_PARAMETER = "form";
    /** A site key, and a JCR identifier: no path separator, so the parameters name one folder of the tree. */
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern FORM_ID = Pattern.compile("[A-Fa-f0-9-]{1,64}");

    private final transient ServerAdministrators administrators;
    private transient QuarantineStore store;

    public InspectionServlet() {
        this(ServerAdministrators.ofThePlatform());
    }

    InspectionServlet(ServerAdministrators administrators) {
        this.administrators = administrators;
    }

    @Reference
    public void setStore(QuarantineStore store) {
        this.store = store;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            if (!administrators.currentUserIsOne()) {
                answer(resp, HttpServletResponse.SC_FORBIDDEN, error("server administrators only"));
                return;
            }
            String site = req.getParameter(SITE_PARAMETER);
            String form = req.getParameter(FORM_PARAMETER);
            if (site == null || form == null || !SITE_KEY.matcher(site).matches() || !FORM_ID.matcher(form).matches()) {
                answer(resp, HttpServletResponse.SC_BAD_REQUEST, error("the parameters site and form are required"));
                return;
            }
            answer(resp, HttpServletResponse.SC_OK, inspection(site, form, store.list(site, form)));
        } catch (QuarantineStoreException e) {
            log.warn("Could not inspect the quarantine tree: {}", e.getMessage());
            answerQuietly(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error("the quarantine tree could not be read"));
        } catch (IOException e) {
            // A servlet lets no exception out: the caller reads a broken answer, the log says why.
            log.warn("Could not write the inspection: {}", e.getMessage());
        }
    }

    /** The JSON of the held submissions of a form: names, states, sizes and hashes only. */
    static JSONObject inspection(String site, String form, List<HeldRecord> records) {
        JSONArray submissions = new JSONArray();
        for (HeldRecord held : records) {
            submissions.put(submission(held));
        }
        return new JSONObject().put("site", site).put("form", form).put("submissions", submissions);
    }

    private static JSONObject submission(HeldRecord held) {
        JSONArray items = new JSONArray();
        for (ItemRecord item : held.items()) {
            items.put(new JSONObject()
                    .put("name", item.name())
                    .put("fieldName", item.fieldName())
                    .put("fileName", item.fileName())
                    .put("mimeType", nullable(item.mimeType()))
                    .put("size", item.size())
                    .put("sha256", item.sha256())
                    .put("binaryPresent", item.binaryPresent()));
        }
        return new JSONObject()
                .put("path", held.path())
                .put("state", held.state())
                .put("createdAt", instant(held.createdAt()))
                .put("attempts", held.attempts())
                .put("nextAttempt", instant(held.nextAttempt()))
                .put("completedActions", held.completedActions())
                .put("fields", new JSONArray(held.valueFields()))
                .put("sensitiveFields", new JSONArray(held.sensitiveFields()))
                .put("valuesPresent", held.valuesPresent())
                .put("items", items);
    }

    private static Object instant(Instant at) {
        return at == null ? JSONObject.NULL : at.toString();
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static JSONObject error(String message) {
        return new JSONObject().put("error", message);
    }

    private static void answerQuietly(HttpServletResponse resp, int status, JSONObject body) {
        try {
            answer(resp, status, body);
        } catch (IOException e) {
            log.warn("Could not write the inspection: {}", e.getMessage());
        }
    }

    private static void answer(HttpServletResponse resp, int status, JSONObject body) throws IOException {
        resp.setStatus(status);
        resp.setHeader("Cache-Control", "no-store");
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.getWriter().write(body.toString());
    }
}
