package org.jahia.modules.formidable.quarantine.web;

import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The endpoint of the retention answers server administrators alone, with the paths it purged. */
class RetentionServletTest {

    private static final String SITE = "formidableTestSite";
    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final String HELD_PATH = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID
            + "/2026/11/19/held-0b7c1f3e-0000-4000-8000-000000000001";

    private QuarantineRetention retention;

    private record Call(int status, String body) {
    }

    @BeforeEach
    void setUp() throws Exception {
        retention = mock(QuarantineRetention.class);
        when(retention.purge(anyString(), any())).thenReturn(new QuarantineRetention.Report(30, List.of(HELD_PATH),
                List.of()));
    }

    /** A call of the specs: a POST carries the header that a cross-site form or a simple request cannot send. */
    private static Call post(HttpServlet servlet, Map<String, String> parameters) throws Exception {
        return post(servlet, parameters, "XMLHttpRequest");
    }

    private static Call post(HttpServlet servlet, Map<String, String> parameters, String requestedWith)
            throws Exception {
        return post(servlet, parameters, requestedWith, "application/json; charset=UTF-8");
    }

    private static Call post(HttpServlet servlet, Map<String, String> parameters, String requestedWith,
                             String contentType) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(JsonAnswers.REQUESTED_WITH)).thenReturn(requestedWith);
        when(request.getContentType()).thenReturn(contentType);
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

    private RetentionServlet retentionServlet(boolean administrator) {
        RetentionServlet servlet = new RetentionServlet(() -> administrator);
        servlet.setRetention(retention);
        return servlet;
    }

    @Test
    void theRetentionAnswersTheServerAdministratorsAlone() throws Exception {
        Call call = post(retentionServlet(false), Map.of("site", SITE, "olderThanDays", "0"));

        assertEquals(403, call.status());
        verify(retention, never()).purge(anyString(), any());
    }

    @Test
    void aPostWithoutTheRequestedWithHeaderIsRefusedAndPurgesNothing() throws Exception {
        Call call = post(retentionServlet(true), Map.of("site", SITE, "olderThanDays", "0"), null);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, call.status());
        verify(retention, never()).purge(anyString(), any());
    }

    @Test
    void aPostThatIsNotJsonIsRefusedAndPurgesNothing() throws Exception {
        Map<String, String> parameters = Map.of("site", SITE, "olderThanDays", "0");
        for (String contentType : new String[]{null, "text/plain;application/json", "application/x-www-form-urlencoded",
                "multipart/form-data; boundary=x"}) {
            Call call = post(retentionServlet(true), parameters, "XMLHttpRequest", contentType);

            assertEquals(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, call.status(), String.valueOf(contentType));
        }
        verify(retention, never()).purge(anyString(), any());
    }

    @Test
    void anUnexpectedErrorOfTheRetentionAnswersTheJsonOfTheModule() throws Exception {
        when(retention.purge(anyString(), any())).thenThrow(new IllegalStateException("The tree broke"));

        Call call = assertDoesNotThrow(() -> post(retentionServlet(true), Map.of("site", SITE)));

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, call.status());
        assertTrue(new JSONObject(call.body()).has("error"), call.body());
    }

    @Test
    void theRetentionAnswersThePurgedPaths() throws Exception {
        Call call = post(retentionServlet(true), Map.of("site", SITE));

        assertEquals(200, call.status());
        JSONObject body = new JSONObject(call.body());
        assertEquals(List.of(HELD_PATH), body.getJSONArray("purged").toList());
        assertEquals(30, body.getInt("retentionDays"));
        verify(retention).purge(SITE, null);
    }

    @Test
    void theRetentionTakesALimitOfDaysForOneCall() throws Exception {
        post(retentionServlet(true), Map.of("site", SITE, "olderThanDays", "0"));
        verify(retention).purge(SITE, 0);

        for (String days : List.of("-1", "x", "123456")) {
            assertEquals(400, post(retentionServlet(true), Map.of("site", SITE, "olderThanDays", days)).status(), days);
        }
        assertEquals(400, post(retentionServlet(true), Map.of("site", "../x")).status());
    }
}
