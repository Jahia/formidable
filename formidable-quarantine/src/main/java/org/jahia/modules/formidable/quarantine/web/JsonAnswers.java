package org.jahia.modules.formidable.quarantine.web;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** What the endpoints of the module share: a JSON answer that no cache keeps, and the two barriers of a POST. */
final class JsonAnswers {

    private static final Logger log = LoggerFactory.getLogger(JsonAnswers.class);

    static final String FORBIDDEN = "server administrators only";
    /** The header a POST must carry: a cross-site form or a simple request cannot send it with no preflight. */
    static final String REQUESTED_WITH = "X-Requested-With";
    /**
     * The media type a POST must declare: a browser sends this type cross-site only after a preflight, which the module
     * never answers.
     */
    static final String JSON_MEDIA_TYPE = "application/json";

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
            resp.setContentType(JSON_MEDIA_TYPE);
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.getWriter().write(body.toString());
        } catch (IOException e) {
            log.warn("Could not write the answer: {}", e.getMessage());
        }
    }

    /**
     * Refuses a POST that a cross-site page could make the browser of a connected user send. Two barriers, each
     * enough alone: without {@link #REQUESTED_WITH} the POST gets 403, and with a {@code Content-Type} whose essence
     * is not {@value #JSON_MEDIA_TYPE} it gets 415. A browser sends {@value #JSON_MEDIA_TYPE} cross-site only after a
     * preflight, which the module never answers. It sends the three simple types, form-urlencoded, multipart and
     * {@code text/plain}, with no preflight, so they are refused. The essence is the type before the first {@code ;},
     * so {@code text/plain;application/json} is refused too.
     *
     * @return true when the request was refused and answered
     */
    static boolean refusedAsCrossSite(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getHeader(REQUESTED_WITH) == null) {
            answer(resp, HttpServletResponse.SC_FORBIDDEN, error("a POST needs the header " + REQUESTED_WITH));
            return true;
        }
        if (!JSON_MEDIA_TYPE.equals(essence(req.getContentType()))) {
            answer(resp, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    error("a POST declares the media type " + JSON_MEDIA_TYPE));
            return true;
        }
        return false;
    }

    private static String essence(String contentType) {
        return contentType == null ? null : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }
}
