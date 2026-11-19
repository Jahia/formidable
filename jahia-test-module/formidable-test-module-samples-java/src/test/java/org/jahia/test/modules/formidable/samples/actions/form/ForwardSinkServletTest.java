package org.jahia.test.modules.formidable.samples.actions.form;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The forward sink counts each forward it accepts, and refuses them on demand, as a server administrator asks. */
class ForwardSinkServletTest {

    private static final String FORWARD = "--b\r\nContent-Disposition: form-data; name=\"fullName\"\r\n\r\nAda\r\n--b--\r\n";

    private record Call(int status, JSONObject body) {
    }

    private static Call call(ForwardSinkServlet sink, String method, String operation) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getPathInfo()).thenReturn(operation);
        ByteArrayInputStream bytes = new ByteArrayInputStream(FORWARD.getBytes(StandardCharsets.ISO_8859_1));
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override
            public int read() {
                return bytes.read();
            }
        });
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out, true));
        int[] status = new int[1];
        doAnswer(invocation -> {
            status[0] = invocation.getArgument(0);
            return null;
        }).when(response).setStatus(anyInt());
        try {
            sink.service(request, response);
        } catch (ServletException e) {
            throw new IOException(e);
        }
        return new Call(status[0], new JSONObject(out.toString()));
    }

    @Test
    void byDefaultEachForwardIsAcceptedAndCounted() throws IOException {
        ForwardSinkServlet sink = new ForwardSinkServlet(() -> false);

        assertEquals(200, call(sink, "POST", "/").status());
        JSONObject state = call(sink, "GET", "/").body();

        assertEquals(1, state.getInt("count"));
        assertFalse(state.getBoolean("refusing"));
        assertEquals(0, state.getInt("refused"));
    }

    @Test
    void whileItRefusesAForwardGets503AndIsNotCounted() throws IOException {
        ForwardSinkServlet sink = new ForwardSinkServlet(() -> true);

        assertEquals(200, call(sink, "POST", "/refuse").status());
        assertEquals(503, call(sink, "POST", "/").status());
        JSONObject refusing = call(sink, "GET", "/").body();
        assertTrue(refusing.getBoolean("refusing"));
        assertEquals(0, refusing.getInt("count"), "a refused forward is not received");
        assertEquals(1, refusing.getInt("refused"));

        assertEquals(200, call(sink, "POST", "/accept").status());
        assertEquals(200, call(sink, "POST", "/").status());
        JSONObject accepting = call(sink, "GET", "/").body();
        assertFalse(accepting.getBoolean("refusing"));
        assertEquals(1, accepting.getInt("count"));
    }

    @Test
    void onlyAServerAdministratorSetsTheRefusal() throws IOException {
        ForwardSinkServlet sink = new ForwardSinkServlet(() -> false);

        assertEquals(403, call(sink, "POST", "/refuse").status());
        assertEquals(200, call(sink, "POST", "/").status());
        assertEquals(1, call(sink, "GET", "/").body().getInt("count"));
    }

    @Test
    void aResetLiftsTheRefusal() throws IOException {
        ForwardSinkServlet sink = new ForwardSinkServlet(() -> true);
        call(sink, "POST", "/refuse");
        call(sink, "POST", "/");

        call(sink, "POST", "/reset");
        JSONObject state = call(sink, "GET", "/").body();

        assertFalse(state.getBoolean("refusing"));
        assertEquals(0, state.getInt("refused"));
        assertEquals(200, call(sink, "POST", "/").status());
    }
}
