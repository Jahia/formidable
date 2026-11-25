package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.modules.formidable.quarantine.api.QuarantineRunException;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunInProgressException;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunner;
import org.jahia.modules.formidable.quarantine.api.RunReport;
import org.jahia.modules.formidable.quarantine.api.RunnerSettings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * "Run now", for the test suite: {@code /modules/formidable-quarantine/run}.
 * <ul>
 * <li>{@code POST ?site=<siteKey>[&form=<formId>][&ignoreSchedule=true]} makes one pass of the run over the due held
 * submissions of the site, or of one form, and answers each submission it processed with its state after the run;</li>
 * <li>{@code GET} answers the settings the next run applies, and the period of the scheduled run on this server
 * ({@code "schedule": {"periodSeconds"}}, 0 when it is off), so that a spec that edits the configuration can wait
 * until the module applies it.</li>
 * </ul>
 * A run that another run holds back (the lock of the run of the site, on this server or another one) answers 409, "a
 * run is in progress": nothing ran.
 * The answer names paths, states and settings: never a value, never a byte.
 *
 * <p>Server administrators only: any other caller gets 403, and nothing runs. A POST also needs the header
 * {@code X-Requested-With}.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=" + RunServlet.ALIAS}, immediate = true)
public class RunServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(RunServlet.class);

    public static final String ALIAS = "/formidable-quarantine/run";
    static final String SITE_PARAMETER = "site";
    static final String FORM_PARAMETER = "form";
    static final String IGNORE_SCHEDULE_PARAMETER = "ignoreSchedule";
    /** A site key, and a JCR identifier: no path separator, so the parameters name one folder of the tree. */
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern FORM_ID = Pattern.compile("[A-Fa-f0-9-]{1,64}");

    private final transient ServerAdministrators administrators;
    private final transient LongSupplier scheduledPeriod;
    private final transient AtomicReference<QuarantineRunner> run = new AtomicReference<>();

    public RunServlet() {
        this(ServerAdministrators.ofThePlatform(), ScheduledRun::periodSeconds);
    }

    RunServlet(ServerAdministrators administrators, LongSupplier scheduledPeriod) {
        this.administrators = administrators;
        this.scheduledPeriod = scheduledPeriod;
    }

    @Reference
    public void setRun(QuarantineRunner run) {
        this.run.set(run);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        if (!administrators.currentUserIsOne()) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_FORBIDDEN, JsonAnswers.error(JsonAnswers.FORBIDDEN));
            return;
        }
        JSONObject body;
        try {
            body = new JSONObject().put("settings", settings(run.get().runnerSettings()))
                    .put("schedule", new JSONObject().put("periodSeconds", scheduledPeriod.getAsLong()));
        } catch (JSONException e) {
            log.error("Could not write the settings of the run: {}", e.getMessage());
            JsonAnswers.answer(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonAnswers.error("the settings could not be written"));
            return;
        }
        JsonAnswers.answer(resp, HttpServletResponse.SC_OK, body);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if (!administrators.currentUserIsOne()) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_FORBIDDEN, JsonAnswers.error(JsonAnswers.FORBIDDEN));
            return;
        }
        if (JsonAnswers.refusedAsCrossSite(req, resp)) {
            return;
        }
        String site = req.getParameter(SITE_PARAMETER);
        String form = req.getParameter(FORM_PARAMETER);
        if (site == null || !SITE_KEY.matcher(site).matches() || (form != null && !FORM_ID.matcher(form).matches())) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonAnswers.error("the parameter site is required, and form names one form"));
            return;
        }
        boolean ignoreSchedule = Boolean.parseBoolean(req.getParameter(IGNORE_SCHEDULE_PARAMETER));
        try {
            QuarantineRunner current = run.get();
            RunReport report = current.runNow(site, form, ignoreSchedule);
            JsonAnswers.answer(resp, HttpServletResponse.SC_OK, report(report, current.runnerSettings()));
        } catch (QuarantineRunInProgressException e) {
            log.info("The run of the quarantine of site {} is held back: {}", site, e.getMessage());
            JsonAnswers.answer(resp, HttpServletResponse.SC_CONFLICT, JsonAnswers.error("a run is in progress"));
        } catch (QuarantineRunException e) {
            log.warn("The run of the quarantine of site {} failed: {}", site, e.getMessage());
            JsonAnswers.answer(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonAnswers.error("the quarantine tree could not be read"));
        } catch (RuntimeException e) {
            // the class alone: the message of an unexpected failure may quote a held value
            log.error("The run of the quarantine of site {} failed with {}", site, e.getClass().getName());
            JsonAnswers.answer(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonAnswers.error("the run failed"));
        }
    }

    static JSONObject report(RunReport report, RunnerSettings settings) {
        JSONArray processed = new JSONArray();
        for (RunReport.Processed one : report.processed()) {
            processed.put(new JSONObject()
                    .put("path", one.path())
                    .put("form", one.formId())
                    .put("from", one.from())
                    .put("state", nullable(one.state()))
                    .put("error", nullable(one.error())));
        }
        return new JSONObject()
                .put("site", report.siteKey())
                .put("form", nullable(report.formId()))
                .put("settings", settings(settings))
                .put("processed", processed);
    }

    static JSONObject settings(RunnerSettings settings) {
        return new JSONObject()
                .put("allowInProcessFirst", settings.allowInProcessFirst())
                .put("maxAttempts", settings.maxAttempts())
                .put("backoffInitialSeconds", settings.backoffInitialSeconds())
                .put("backoffCapSeconds", settings.backoffCapSeconds());
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }
}
