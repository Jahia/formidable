package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final class TestableFormSubmitServlet extends FormSubmitServlet {
        private final boolean allowed;
        private boolean pipelineInvoked;

        private TestableFormSubmitServlet(boolean allowed) {
            this.allowed = allowed;
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
                void run(HttpServletRequest req) {
                    // No-op: these tests only verify the gate and whether the pipeline would be reached.
                }
            };
        }
    }
}
