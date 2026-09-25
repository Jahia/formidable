package org.jahia.test.modules.formidable.samples.actions.field;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * What the doubles of the providers share: a servlet the samples module registers under {@code /modules}, answering
 * the provider's own JSON on the operation the sample action calls, and refusing everything else the way the
 * provider would. A double lets no exception out, reads the operation past its alias, and writes JSON; which method
 * it serves, which credential it expects and what it answers is each double's own — one per provider, next to the
 * action it doubles. Open to any caller, on purpose: it holds nothing and judges nothing real.
 */
public abstract class ProviderStubServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ProviderStubServlet.class);

    /** The credential every double accepts: the one the development provider line carries. */
    public static final String TOKEN = "stub-token";
    static final int MAX_BODY_CHARS = 4096;

    /** The alias the double is registered under, the base URL of its provider line. */
    protected abstract String alias();

    /** A POST on the operation under the alias; a double that does not serve POST answers 405. */
    protected void post(HttpServletRequest req, HttpServletResponse resp, String operation) throws IOException {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /** A GET on the operation under the alias; a double that does not serve GET answers 405. */
    protected void get(HttpServletRequest req, HttpServletResponse resp, String operation) throws IOException {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            post(req, resp, operationOf(req));
        } catch (IOException e) {
            // A servlet lets no exception out: the caller reads a broken answer, the log says why.
            log.warn("[{}] Could not read the request or write the answer: {}", getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    protected final void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            get(req, resp, operationOf(req));
        } catch (IOException e) {
            log.warn("[{}] Could not read the request or write the answer: {}", getClass().getSimpleName(), e.getMessage());
        }
    }

    /** The path under the alias: the servlet's path info, or the request URI past the alias when the container gives none. */
    String operationOf(HttpServletRequest req) {
        if (req.getPathInfo() != null) {
            return req.getPathInfo();
        }
        String uri = req.getRequestURI();
        int at = uri == null ? -1 : uri.indexOf(alias());
        return at < 0 ? uri : uri.substring(at + alias().length());
    }

    /** The request body, capped: a double reads a small JSON and nothing else. */
    protected static String read(Reader reader) throws IOException {
        StringBuilder body = new StringBuilder();
        char[] chunk = new char[512];
        int read;
        while (body.length() < MAX_BODY_CHARS && (read = reader.read(chunk)) != -1) {
            body.append(chunk, 0, read);
        }
        return body.toString();
    }

    protected static void answer(HttpServletResponse resp, int status, JSONObject body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.getWriter().write(body.toString());
    }
}
