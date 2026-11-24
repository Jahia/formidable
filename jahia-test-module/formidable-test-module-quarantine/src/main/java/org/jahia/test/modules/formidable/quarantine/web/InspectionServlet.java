package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.test.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.test.modules.formidable.quarantine.store.CheckStore;
import org.jahia.test.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.test.modules.formidable.quarantine.store.OutcomeRecord;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * The inspection of the quarantine tree, for the test suite: a {@code GET} on
 * {@code /modules/formidable-quarantine/inspection?site=<siteKey>&form=<formId>} answers the held submissions of the
 * form as JSON: the state of each, the names of its fields, of each item its file name, size, SHA-256 and whether
 * its bytes are still there, the outcome of each check for each item, the decision that stopped the run, and the audit
 * records of the operations and of the run, with the names of their users. Never a
 * value, never a sensitive value, never a byte, never a ticket.
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
    private static final String SHA256 = "sha256";

    private final transient ServerAdministrators administrators;
    private final transient AtomicReference<QuarantineStore> store = new AtomicReference<>();
    private final transient AtomicReference<CheckStore> checkStore = new AtomicReference<>();

    public InspectionServlet() {
        this(ServerAdministrators.ofThePlatform());
    }

    InspectionServlet(ServerAdministrators administrators) {
        this.administrators = administrators;
    }

    @Reference
    public void setStore(QuarantineStore store) {
        this.store.set(store);
    }

    @Reference
    public void setCheckStore(CheckStore checkStore) {
        this.checkStore.set(checkStore);
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
            answer(resp, HttpServletResponse.SC_OK, inspection(site, form, store.get().list(site, form)));
        } catch (QuarantineStoreException e) {
            log.warn("Could not inspect the quarantine tree: {}", e.getMessage());
            answerQuietly(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error("the quarantine tree could not be read"));
        } catch (IOException e) {
            // A servlet lets no exception out: the caller reads a broken answer, the log says why.
            log.warn("Could not write the inspection: {}", e.getMessage());
        }
    }

    /** The JSON of the held submissions of a form: names, states, sizes, hashes and outcomes only. */
    JSONObject inspection(String site, String form, List<HeldRecord> records) throws QuarantineStoreException {
        CheckStore checks = checkStore.get();
        JSONArray submissions = new JSONArray();
        for (HeldRecord held : records) {
            submissions.put(submission(held, checks.outcomes(held.path()), checks.decision(held.path()).orElse(null))
                    .put("audit", audit(checks.audit(held.path()))));
        }
        return new JSONObject().put("site", site).put("form", form).put("submissions", submissions);
    }

    private static JSONObject submission(HeldRecord held, List<OutcomeRecord> outcomes,
                                         DecisionRecord decision) {
        JSONArray items = new JSONArray();
        for (ItemRecord item : held.items()) {
            items.put(new JSONObject()
                    .put("name", item.name())
                    .put("fieldName", nullable(item.fieldName()))
                    .put("fileName", nullable(item.fileName()))
                    .put("mimeType", nullable(item.mimeType()))
                    .put("size", item.size())
                    .put(SHA256, item.sha256())
                    .put("binaryPresent", item.binaryPresent()));
        }
        return new JSONObject()
                .put("path", held.path())
                .put("state", held.state())
                .put("createdAt", instant(held.createdAt()))
                .put("changedAt", instant(held.changedAt()))
                .put("attempts", held.attempts())
                .put("nextAttempt", instant(held.nextAttempt()))
                .put("completedActions", held.completedActions())
                .put("fields", new JSONArray(held.valueFields()))
                .put("sensitiveFields", new JSONArray(held.sensitiveFields()))
                .put("valuesPresent", held.valuesPresent())
                .put("items", items)
                .put("checks", checks(outcomes))
                .put("decision", decision == null ? JSONObject.NULL : decision(decision));
    }

    /** The outcome of each check for each item: no ticket, only whether one is pending. */
    private static JSONArray checks(List<OutcomeRecord> outcomes) {
        JSONArray checks = new JSONArray();
        for (OutcomeRecord outcome : outcomes) {
            checks.put(new JSONObject()
                    .put("item", outcome.itemName())
                    .put("check", outcome.checkName())
                    .put("outcome", outcome.outcome())
                    .put("reason", nullable(outcome.reason()))
                    .put("retryable", outcome.retryable())
                    .put("ticketPresent", outcome.ticket() != null)
                    .put("attempts", outcome.attempts())
                    .put(SHA256, outcome.sha256())
                    .put("at", instant(outcome.at())));
        }
        return checks;
    }

    /** Who changed the state, how and when: the name of the user, never a value. */
    private static JSONArray audit(List<AuditRecord> records) {
        JSONArray audit = new JSONArray();
        for (AuditRecord entry : records) {
            audit.put(new JSONObject()
                    .put("operation", entry.operation())
                    .put("user", nullable(entry.userName()))
                    .put("from", entry.fromState())
                    .put("to", entry.toState())
                    .put("at", instant(entry.at())));
        }
        return audit;
    }

    private static JSONObject decision(DecisionRecord decision) {
        return new JSONObject()
                .put("reason", decision.reason())
                .put("item", nullable(decision.itemName()))
                .put("check", nullable(decision.checkName()))
                .put(SHA256, nullable(decision.sha256()))
                .put("failedAction", decision.failedAction() == null ? JSONObject.NULL : decision.failedAction())
                .put("at", instant(decision.at()));
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
