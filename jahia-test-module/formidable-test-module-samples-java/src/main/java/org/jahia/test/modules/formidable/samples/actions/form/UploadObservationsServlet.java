package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.test.modules.formidable.samples.actions.field.ProviderStubServlet;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Answers what the upload samples saw, for the test suite: a {@code GET} answers the calls of the observer action
 * and of the sample hold since the last reset ({@link UploadObservations}), and a {@code POST} on {@code /reset}
 * forgets them. An action that runs after release has no response to write a header on, so the suite reads here.
 *
 * <p>Open to any caller, on purpose, as the doubles of the providers are: it holds counts and node names.</p>
 */
@Component(
    service = { HttpServlet.class, Servlet.class },
    property = { "alias=" + UploadObservationsServlet.ALIAS },
    immediate = true
)
public class UploadObservationsServlet extends ProviderStubServlet {

    private static final long serialVersionUID = 1L;

    public static final String ALIAS = "/formidable-samples/upload-observations";
    static final String RESET_OPERATION = "/reset";

    @Override
    protected String alias() {
        return ALIAS;
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp, String operation) throws IOException {
        if (!RESET_OPERATION.equals(operation)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No POST on " + operation);
            return;
        }
        UploadObservations.reset();
        answer(resp, HttpServletResponse.SC_OK, UploadObservations.json());
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp, String operation) throws IOException {
        answer(resp, HttpServletResponse.SC_OK, UploadObservations.json());
    }
}
