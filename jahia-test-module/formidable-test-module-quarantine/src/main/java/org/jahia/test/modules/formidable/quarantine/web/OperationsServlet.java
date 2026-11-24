package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.test.modules.formidable.quarantine.ops.Operation;
import org.jahia.test.modules.formidable.quarantine.ops.OperationRefusedException;
import org.jahia.test.modules.formidable.quarantine.ops.OperationResult;
import org.jahia.test.modules.formidable.quarantine.ops.QuarantineOperations;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
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
import java.util.regex.Pattern;

/**
 * The operations of a manager: {@code POST /modules/formidable-quarantine/operations?path=<held>&operation=<op>}, where
 * the operation is {@code retry}, {@code release}, {@code resume} or {@code reject}. It answers
 * {@code {"path", "operation", "from", "state"}}: the state before and after. Never a value, a byte or a ticket.
 *
 * <p>A manager of the form, or a server administrator, may run an operation: any other caller gets 403, and nothing
 * changes. A state that does not allow the operation gets 409. {@link QuarantineOperations} makes these checks. A POST
 * without the header {@code X-Requested-With} gets 403 first.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=" + OperationsServlet.ALIAS},
        immediate = true)
public class OperationsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(OperationsServlet.class);

    public static final String ALIAS = "/formidable-quarantine/operations";
    static final String PATH_PARAMETER = "path";
    static final String OPERATION_PARAMETER = "operation";
    /** The path of a held submission, as the store writes it: no other node, no relative segment. */
    static final Pattern HELD_PATH = Pattern.compile(
            "/sites/[A-Za-z0-9_-]{1,64}/formidable-quarantine/[A-Fa-f0-9-]{1,64}/\\d{4}/\\d{2}/\\d{2}/held-[A-Fa-f0-9-]{36}");

    /** Bound once by DS, read by every request thread: a final holder, not a mutable field of the servlet. */
    private final transient AtomicReference<QuarantineOperations> operations = new AtomicReference<>();

    @Reference
    public void setOperations(QuarantineOperations operations) {
        this.operations.set(operations);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if (JsonAnswers.refusedAsCrossSite(req, resp)) {
            return;
        }
        String path = req.getParameter(PATH_PARAMETER);
        Operation operation = Operation.fromValue(req.getParameter(OPERATION_PARAMETER));
        if (path == null || !HELD_PATH.matcher(path).matches() || operation == null) {
            JsonAnswers.answer(resp, HttpServletResponse.SC_BAD_REQUEST, JsonAnswers.error(
                    "the parameter path names a held submission, and operation is retry, release, resume or reject"));
            return;
        }
        try {
            OperationResult result = operations.get().apply(path, operation);
            JsonAnswers.answer(resp, HttpServletResponse.SC_OK, new JSONObject()
                    .put("path", result.path())
                    .put(OPERATION_PARAMETER, result.operation().value())
                    .put("from", result.from().value())
                    .put("state", result.to().value()));
        } catch (OperationRefusedException e) {
            JsonAnswers.answer(resp, statusOf(e.reason()), JsonAnswers.error(e.getMessage()));
        } catch (QuarantineStoreException e) {
            log.warn("The operation {} on {} failed: {}", operation.value(), path, e.toString());
            JsonAnswers.answer(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonAnswers.error("the operation failed"));
        } catch (RuntimeException e) {
            // the class alone: the message of an action that a release or a resume ran may quote a held value
            log.warn("The operation {} on {} failed with {}", operation.value(), path, e.getClass().getName());
            JsonAnswers.answer(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonAnswers.error("the operation failed"));
        }
    }

    static int statusOf(OperationRefusedException.Reason reason) {
        switch (reason) {
            case NOT_FOUND:
                return HttpServletResponse.SC_NOT_FOUND;
            case CONFLICT:
                return HttpServletResponse.SC_CONFLICT;
            default:
                return HttpServletResponse.SC_FORBIDDEN;
        }
    }
}
