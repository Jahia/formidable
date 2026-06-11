package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormSubmitServletTest {

    @Test
    void rejectsRequestWhenSecurityFilterDeniesSubmission() throws Exception {
        // Verifies the Security Filter gate: when the submit scope is denied,
        // the servlet must reject the request before the submission pipeline runs.
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(body));

        TestableFormSubmitServlet servlet = new TestableFormSubmitServlet(false);
        servlet.setConfig(mock(FormidableConfigService.class));

        servlet.doPost(request, response);

        // Expected outcome: HTTP 403 + FMDB-011 JSON error, and no pipeline execution.
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        assertTrue(body.toString().contains("\"success\":false"));
        assertTrue(body.toString().contains("\"errorCode\":\"FMDB-011\""));
        assertFalse(servlet.pipelineInvoked);
    }

    @Test
    void runsPipelineWhenSecurityFilterAllowsSubmission() throws Exception {
        // Verifies the happy path through the gate: when the submit scope is allowed,
        // the servlet must delegate to the submission pipeline.
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(body));

        TestableFormSubmitServlet servlet = new TestableFormSubmitServlet(true);
        servlet.setConfig(mock(FormidableConfigService.class));

        servlet.doPost(request, response);

        // Expected outcome: HTTP 200 success response, and the pipeline is invoked once.
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(body.toString().contains("\"success\":true"));
        assertTrue(servlet.pipelineInvoked);
    }

    @Test
    void returnsSubmissionErrorStatusAndCodeWhenPipelineRejectsRequest() throws Exception {
        // Verifies the standard submission-failure contract:
        // the servlet must expose the mapped HTTP status and opaque error code.
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(body));

        TestableFormSubmitServlet servlet = new TestableFormSubmitServlet(
                true,
                new SubmissionException(ErrorCode.FMDB_009, "auth required"),
                null
        );
        servlet.setConfig(mock(FormidableConfigService.class));

        servlet.doPost(request, response);

        JSONObject json = new JSONObject(body.toString());
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // Expected outcome: the JSON response contains success=false and the submission error code.
        assertFalse(json.getBoolean("success"));
        assertEquals("FMDB-009", json.getString("errorCode"));
    }

    @Test
    void includesActionProgressWhenActionFailureCarriesProgressMetadata() throws Exception {
        // Verifies the action-progress contract for downstream action failures.
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(body));

        TestableFormSubmitServlet servlet = new TestableFormSubmitServlet(
                true,
                new SubmissionException(ErrorCode.FMDB_008, "action failed", 1, 3),
                null
        );
        servlet.setConfig(mock(FormidableConfigService.class));

        servlet.doPost(request, response);

        JSONObject json = new JSONObject(body.toString());
        verify(response).setStatus(422);
        // Expected outcome: the servlet preserves both the opaque error code
        // and the completed/total action counters.
        assertEquals("FMDB-008", json.getString("errorCode"));
        assertEquals(1, json.getInt("actionsCompleted"));
        assertEquals(3, json.getInt("actionsTotal"));
    }

    @Test
    void returnsOpaqueInternalErrorWhenPipelineThrowsUnexpectedException() throws Exception {
        // Verifies the defensive catch-all path: unexpected exceptions must not leak
        // implementation details to the client.
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();

        when(response.getWriter()).thenReturn(new PrintWriter(body));

        TestableFormSubmitServlet servlet = new TestableFormSubmitServlet(
                true,
                null,
                new IllegalStateException("boom")
        );
        servlet.setConfig(mock(FormidableConfigService.class));

        servlet.doPost(request, response);

        JSONObject json = new JSONObject(body.toString());
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        // Expected outcome: the response collapses to FMDB-500 with no action-progress metadata.
        assertFalse(json.getBoolean("success"));
        assertEquals("FMDB-500", json.getString("errorCode"));
        assertFalse(json.has("actionsCompleted"));
        assertFalse(json.has("actionsTotal"));
    }

    private static final class TestableFormSubmitServlet extends FormSubmitServlet {
        private final boolean allowed;
        private final SubmissionException submissionFailure;
        private final RuntimeException unexpectedFailure;
        private boolean pipelineInvoked;

        private TestableFormSubmitServlet(boolean allowed) {
            this(allowed, null, null);
        }

        private TestableFormSubmitServlet(boolean allowed,
                                          SubmissionException submissionFailure,
                                          RuntimeException unexpectedFailure) {
            this.allowed = allowed;
            this.submissionFailure = submissionFailure;
            this.unexpectedFailure = unexpectedFailure;
        }

        @Override
        boolean isRequestAllowed() {
            return allowed;
        }

        @Override
        FormSubmissionPipeline createPipeline() {
            pipelineInvoked = true;
            return new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of()) {
                @Override
                void run(HttpServletRequest req) throws SubmissionException {
                    if (submissionFailure != null) {
                        throw submissionFailure;
                    }
                    if (unexpectedFailure != null) {
                        throw unexpectedFailure;
                    }
                    // No-op: these tests only verify the gate and whether the pipeline would be reached.
                }
            };
        }
    }
}
