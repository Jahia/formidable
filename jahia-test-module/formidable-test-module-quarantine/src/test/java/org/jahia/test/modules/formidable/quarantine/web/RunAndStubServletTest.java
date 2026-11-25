package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.modules.formidable.quarantine.api.QuarantineRunner;
import org.jahia.modules.formidable.quarantine.api.RunReport;
import org.jahia.modules.formidable.quarantine.api.RunnerSettings;
import org.jahia.test.modules.formidable.quarantine.stub.StubCallLog;
import org.jahia.test.modules.formidable.quarantine.stub.StubControl;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The "run now" endpoint and the control of the stubs answer a server administrator alone: any other caller gets 403,
 * nothing runs and nothing changes.
 */
class RunAndStubServletTest {

    private static final String SITE = "formidableTestSite";
    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final String HELD_PATH = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-1";

    private QuarantineRunner run;
    private StubControl control;

    private record Call(int status, String body) {
    }

    @BeforeEach
    void setUp() throws Exception {
        run = mock(QuarantineRunner.class);
        when(run.runnerSettings()).thenReturn(new RunnerSettings(false, 10, 60, 3600));
        when(run.runNow(anyString(), any(), anyBoolean())).thenReturn(new RunReport(SITE, FORM_ID,
                List.of(new RunReport.Processed(HELD_PATH, FORM_ID, "held", "completed", null))));
        control = new StubControl();
        control.log().add(new StubCallLog.StubCall("fmdbquar:remoteStubCheck", "remoteCheck", "abc",
                StubCallLog.Kind.SUBMIT));
    }

    /** A call of the specs: a POST carries the header that a cross-site form or a simple request cannot send. */
    private static Call call(HttpServlet servlet, String method, String pathInfo, Map<String, String> parameters)
            throws Exception {
        return call(servlet, method, pathInfo, parameters, "XMLHttpRequest");
    }

    private static Call call(HttpServlet servlet, String method, String pathInfo, Map<String, String> parameters,
                             String requestedWith) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getHeader(JsonAnswers.REQUESTED_WITH)).thenReturn(requestedWith);
        when(request.getPathInfo()).thenReturn(pathInfo);
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

    private RunServlet runServlet(boolean administrator) {
        RunServlet servlet = new RunServlet(() -> administrator);
        servlet.setRun(run);
        return servlet;
    }

    private StubServlet stubServlet(boolean administrator) {
        StubServlet servlet = new StubServlet(() -> administrator);
        servlet.setControl(control);
        return servlet;
    }

    @Test
    void theRunRefusesACallerWhoIsNotAServerAdministratorAndRunsNothing() throws Exception {
        Call post = call(runServlet(false), "POST", null, Map.of("site", SITE));
        Call get = call(runServlet(false), "GET", null, Map.of());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, post.status());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, get.status());
        assertFalse(post.body().contains(HELD_PATH), post.body());
        verify(run, never()).runNow(anyString(), any(), anyBoolean());
    }

    @Test
    void aPostWithoutTheRequestedWithHeaderIsRefusedAndChangesNothing() throws Exception {
        Call post = call(runServlet(true), "POST", null, Map.of("site", SITE), null);
        Call lift = call(stubServlet(true), "POST", StubServlet.LIFT, Map.of(), null);
        Call clear = call(stubServlet(true), "POST", StubServlet.CLEAR, Map.of(), null);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, post.status());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, lift.status());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, clear.status());
        verify(run, never()).runNow(anyString(), any(), anyBoolean());
        assertTrue(control.outage().isDown());
        assertEquals(1, control.log().calls().size());
    }

    @Test
    void theRunAnswersTheStateOfEachSubmissionItProcessed() throws Exception {
        Call post = call(runServlet(true), "POST", null, Map.of("site", SITE, "form", FORM_ID, "ignoreSchedule", "true"));

        assertEquals(HttpServletResponse.SC_OK, post.status());
        verify(run).runNow(SITE, FORM_ID, true);
        JSONObject processed = new JSONObject(post.body()).getJSONArray("processed").getJSONObject(0);
        assertEquals(HELD_PATH, processed.getString("path"));
        assertEquals("held", processed.getString("from"));
        assertEquals("completed", processed.getString("state"));
        assertEquals(10, new JSONObject(post.body()).getJSONObject("settings").getInt("maxAttempts"));
    }

    @Test
    void aRunThatFailsWithARuntimeExceptionAnswersAJsonError() throws Exception {
        when(run.runNow(anyString(), any(), anyBoolean())).thenThrow(new IllegalStateException("a bug of the run"));

        Call post = call(runServlet(true), "POST", null, Map.of("site", SITE));

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, post.status());
        assertTrue(new JSONObject(post.body()).has("error"), post.body());
    }

    @Test
    void theRunRefusesASiteOrAFormThatNamesAPath() throws Exception {
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, call(runServlet(true), "POST", null, Map.of()).status());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST,
                call(runServlet(true), "POST", null, Map.of("site", "../users")).status());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST,
                call(runServlet(true), "POST", null, Map.of("site", SITE, "form", FORM_ID + "/2026")).status());
        verify(run, never()).runNow(anyString(), any(), anyBoolean());
    }

    @Test
    void theRunAnswersItsSettings() throws Exception {
        JSONObject settings = new JSONObject(call(runServlet(true), "GET", null, Map.of()).body())
                .getJSONObject("settings");

        assertFalse(settings.getBoolean("allowInProcessFirst"));
        assertEquals(10, settings.getInt("maxAttempts"));
        assertEquals(60, settings.getLong("backoffInitialSeconds"));
        assertEquals(3600, settings.getLong("backoffCapSeconds"));
    }

    @Test
    void theStubControlRefusesACallerWhoIsNotAServerAdministratorAndChangesNothing() throws Exception {
        assertEquals(HttpServletResponse.SC_FORBIDDEN, call(stubServlet(false), "POST", StubServlet.LIFT, Map.of()).status());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, call(stubServlet(false), "POST", StubServlet.CLEAR, Map.of()).status());
        Call get = call(stubServlet(false), "GET", StubServlet.CALLS, Map.of());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, get.status());
        assertFalse(get.body().contains("remoteCheck"), get.body());
        assertTrue(control.outage().isDown());
        assertEquals(1, control.log().calls().size());
    }

    @Test
    void anAdministratorLiftsAndSetsTheOutageAndReadsAndClearsTheCalls() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, call(stubServlet(true), "POST", StubServlet.LIFT, Map.of()).status());
        assertFalse(control.outage().isDown());
        call(stubServlet(true), "POST", StubServlet.SET, Map.of());
        assertTrue(control.outage().isDown());

        JSONObject calls = new JSONObject(call(stubServlet(true), "GET", StubServlet.CALLS,
                Map.of(StubServlet.CHECK_PARAMETER, "remoteCheck")).body());
        assertEquals("SUBMIT", calls.getJSONArray("calls").getJSONObject(0).getString("kind"));
        assertEquals(0, new JSONObject(call(stubServlet(true), "GET", StubServlet.CALLS,
                Map.of(StubServlet.CHECK_PARAMETER, "otherCheck")).body()).getJSONArray("calls").length());

        call(stubServlet(true), "POST", StubServlet.CLEAR, Map.of());
        assertTrue(control.log().calls().isEmpty());
    }
}
