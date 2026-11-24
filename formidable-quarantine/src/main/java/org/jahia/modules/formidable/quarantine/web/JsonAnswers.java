package org.jahia.modules.formidable.quarantine.web;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** What the endpoints of the module share: a JSON answer that no cache keeps, and the operation past the alias. */
final class JsonAnswers {

    private static final Logger log = LoggerFactory.getLogger(JsonAnswers.class);

    static final String FORBIDDEN = "server administrators only";
    /** The header a POST must carry: a cross-site form or a simple request cannot send it with no preflight. */
    static final String REQUESTED_WITH = "X-Requested-With";

    private JsonAnswers() {
    }

    static JSONObject error(String message) {
        return new JSONObject().put("error", message);
    }

    /** Writes the answer; a failure to write is logged, never thrown: a servlet lets no exception out. */
    static void answer(HttpServletResponse resp, int status, JSONObject body) {
        try {
            resp.setStatus(status);
            resp.setHeader("Cache-Control", "no-store");
            resp.setContentType("application/json");
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.getWriter().write(body.toString());
        } catch (IOException e) {
            log.warn("Could not write the answer: {}", e.getMessage());
        }
    }

    /**
     * Refuses a POST that does not carry {@link #REQUESTED_WITH}, with 403: a cross-site page cannot make the browser
     * of a connected administrator send it. The POSTs of the module read no body, so no media type is checked.
     *
     * @return true when the request was refused and answered
     */
    static boolean refusedAsCrossSite(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getHeader(REQUESTED_WITH) != null) {
            return false;
        }
        answer(resp, HttpServletResponse.SC_FORBIDDEN, error("a POST needs the header " + REQUESTED_WITH));
        return true;
    }

    /** The path under the alias: the path info, or the request URI past the alias when the container gives none. */
    static String operationOf(HttpServletRequest req, String alias) {
        if (req.getPathInfo() != null) {
            return req.getPathInfo();
        }
        String uri = req.getRequestURI();
        int at = uri == null ? -1 : uri.indexOf(alias);
        return at < 0 ? "" : uri.substring(at + alias.length());
    }
}
