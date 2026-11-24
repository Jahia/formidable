package org.jahia.modules.formidable.quarantine.web;

import org.jahia.modules.formidable.quarantine.ops.Operation;
import org.jahia.modules.formidable.quarantine.ops.OperationRefusedException;
import org.jahia.modules.formidable.quarantine.ops.OperationResult;
import org.jahia.modules.formidable.quarantine.ops.QuarantineOperations;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The endpoint of the operations: the parameters it accepts, the status of each refusal, and an answer of states. */
class OperationsServletTest {

    private static final String SITE = "formidableTestSite";
    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final String HELD_PATH = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID
            + "/2026/11/19/held-0b7c1f3e-0000-4000-8000-000000000001";

    private QuarantineOperations operations;

    private record Call(int status, String body) {
    }

    @BeforeEach
    void setUp() {
        operations = mock(QuarantineOperations.class);
    }

    /** A call of the specs: a POST carries the header that a cross-site form or a simple request cannot send. */
    private static Call post(HttpServlet servlet, Map<String, String> parameters) throws Exception {
        return post(servlet, parameters, "XMLHttpRequest");
    }

    private static Call post(HttpServlet servlet, Map<String, String> parameters, String requestedWith)
            throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(JsonAnswers.REQUESTED_WITH)).thenReturn(requestedWith);
        parameters.forEach((name, value) -> when(request.getParameter(name)).thenReturn(value));
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out, true));
        int[] status = new int[1];
        doAnswer(invocation -> {
            status[0] = invocation.getArgument(0);
            return null;
        }).when(response).setStatus(anyInt());
        servlet.service(request, response);
        return new Call(status[0], out.toString());
    }

    private OperationsServlet operationsServlet() {
        OperationsServlet servlet = new OperationsServlet();
        servlet.setOperations(operations);
        return servlet;
    }

    @Test
    void anAcceptedOperationAnswersTheStateBeforeAndAfter() throws Exception {
        when(operations.apply(HELD_PATH, Operation.RETRY))
                .thenReturn(new OperationResult(HELD_PATH, Operation.RETRY, HeldState.NEEDS_REVIEW, HeldState.CHECKING));

        Call call = post(operationsServlet(), Map.of("path", HELD_PATH, "operation", "retry"));

        assertEquals(200, call.status());
        JSONObject body = new JSONObject(call.body());
        assertEquals(Map.of("path", HELD_PATH, "operation", "retry", "from", "needsReview", "state", "checking"),
                body.toMap());
    }

    @Test
    void eachRefusalHasItsStatus() throws Exception {
        Map<OperationRefusedException.Reason, Integer> statuses = Map.of(
                OperationRefusedException.Reason.FORBIDDEN, 403,
                OperationRefusedException.Reason.NOT_FOUND, 404,
                OperationRefusedException.Reason.CONFLICT, 409);
        for (Map.Entry<OperationRefusedException.Reason, Integer> expected : statuses.entrySet()) {
            doThrow(new OperationRefusedException(expected.getKey(), "refused"))
                    .when(operations).apply(HELD_PATH, Operation.REJECT);

            Call call = post(operationsServlet(), Map.of("path", HELD_PATH, "operation", "reject"));

            assertEquals(expected.getValue(), call.status(), expected.getKey().name());
        }
    }

    @Test
    void aPostWithoutTheRequestedWithHeaderIsRefusedAndRunsNoOperation() throws Exception {
        Call call = post(operationsServlet(), Map.of("path", HELD_PATH, "operation", "reject"), null);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, call.status());
        verify(operations, never()).apply(anyString(), any());
    }

    @Test
    void anUnknownOperationOrAPathThatNamesNoHeldSubmissionIsABadRequest() throws Exception {
        List<Map<String, String>> requests = List.of(
                Map.of("path", HELD_PATH, "operation", "delete"),
                Map.of("path", HELD_PATH),
                Map.of("operation", "reject"),
                Map.of("path", "/sites/" + SITE + "/home", "operation", "reject"),
                Map.of("path", HELD_PATH + "/../../home", "operation", "reject"),
                Map.of("path", HELD_PATH + "/item0", "operation", "reject"));
        for (Map<String, String> parameters : requests) {
            assertEquals(400, post(operationsServlet(), parameters).status(), parameters.toString());
        }
        verify(operations, never()).apply(anyString(), any());
    }
}
