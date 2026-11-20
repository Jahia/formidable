package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.test.modules.formidable.quarantine.ops.QuarantineRetention;
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
import java.util.regex.Pattern;

/**
 * The retention, for the test suite: {@code POST /modules/formidable-quarantine/retention?site=<siteKey>
 * [&olderThanDays=<n>]} purges the rejected held submissions of the site older than the limit, and answers
 * {@code {"site", "retentionDays", "purged": [<path>], "failed": [<path>]}}. {@code olderThanDays} replaces the setting
 * {@code retentionDays} for this call only.
 *
 * <p>Server administrators only: any other caller gets 403, and nothing is purged. A POST also needs the header
 * {@code X-Requested-With}.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=" + RetentionServlet.ALIAS},
        immediate = true)
public class RetentionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(RetentionServlet.class);

    public static final String ALIAS = "/formidable-quarantine/retention";
    static final String SITE_PARAMETER = "site";
    static final String OLDER_THAN_DAYS_PARAMETER = "olderThanDays";
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** Up to five digits: a limit of days, never a negative one. */
    private static final Pattern DAYS = Pattern.compile("\\d{1,5}");

    private final transient ServerAdministrators administrators;
    private transient QuarantineRetention retention;

    public RetentionServlet() {
        this(ServerAdministrators.ofThePlatform());
    }

    RetentionServlet(ServerAdministrators administrators) {
        this.administrators = administrators;
    }

    @Reference
    public void setRetention(QuarantineRetention retention) {
        this.retention = retention;
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
        String days = req.getParameter(OLDER_THAN_DAYS_PARAMETER);
        if (site == null || !SITE_KEY.matcher(site).matches() || (days != null && !DAYS.matcher(days).matches())) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_BAD_REQUEST,
                    JsonAnswers.error("the parameter site is required, and olderThanDays is a number of days"));
            return;
        }
        try {
            QuarantineRetention.Report report = retention.purge(site, days == null ? null : Integer.valueOf(days));
            JsonAnswers.answer(resp, HttpServletResponse.SC_OK, new JSONObject()
                    .put("site", site)
                    .put("retentionDays", report.retentionDays())
                    .put("purged", new JSONArray(report.purged()))
                    .put("failed", new JSONArray(report.failed())));
        } catch (QuarantineStoreException | RuntimeException e) {
            log.warn("The retention of the quarantine of site {} failed: {}", site, e.toString());
            JsonAnswers.answer(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonAnswers.error("the quarantine tree could not be read"));
        }
    }
}
