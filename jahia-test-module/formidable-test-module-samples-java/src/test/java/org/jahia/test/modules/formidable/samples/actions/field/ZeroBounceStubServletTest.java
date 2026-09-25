package org.jahia.test.modules.formidable.samples.actions.field;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The double of ZeroBounce: the key read off the URL, the status decided by the address, the provider's own way of
 * refusing a key — a 200 with an error field.
 */
class ZeroBounceStubServletTest {

    private static final String OPERATION = "/v2/validate";

    private static JSONObject get(String pathInfo, String key, String email, int[] status) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn(pathInfo);
        when(request.getParameter("api_key")).thenReturn(key);
        when(request.getParameter("email")).thenReturn(email);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        doAnswer(invocation -> {
            status[0] = invocation.getArgument(0);
            return null;
        }).when(response).setStatus(anyInt());

        new ZeroBounceStubServlet().doGet(request, response);

        return out.toString().isEmpty() ? null : new JSONObject(out.toString());
    }

    private static JSONObject validate(String email) throws IOException {
        int[] status = new int[1];
        JSONObject body = get(OPERATION, ProviderStubServlet.TOKEN, email, status);
        assertEquals(200, status[0], email);
        return body;
    }

    @Test
    void theStatusFollowsTheAddressInTheProvidersJson() throws IOException {
        // Verifies the nominal answers the Cypress spec types: valid for an ordinary address, the status a domain of the
        // .test zone is named after, the role reading of a role local part, invalid syntax for a value that is not one.
        JSONObject valid = validate("ada@example.com");
        assertEquals("valid", valid.getString("status"));
        assertEquals("ada@example.com", valid.getString("address"));
        assertEquals("invalid", validate("ada@invalid.test").getString("status"));
        assertEquals("mailbox_not_found", validate("ada@invalid.test").getString("sub_status"));
        assertEquals("do_not_mail", validate("ada@disposable.test").getString("status"));
        assertEquals("disposable", validate("ada@disposable.test").getString("sub_status"));
        assertEquals("role_based", validate("info@example.com").getString("sub_status"));
        assertEquals("catch-all", validate("ada@catchall.test").getString("status"));
        assertEquals("unknown", validate("ada@unknown.test").getString("status"));
        assertEquals("failed_syntax_check", validate("not-an-address").getString("sub_status"));
    }

    @Test
    void anotherKeyIsRefusedTheWayTheProviderDoesItAndTheRestAsAServerWould() throws IOException {
        // Verifies the provider's own refusal — a 200 carrying error, which the sample reads as unavailable — then the
        // stub's: a missing address, another operation, and a POST, which the provider's GET double does not serve.
        int[] status = new int[1];
        assertEquals(ZeroBounceStubServlet.REFUSED_KEY, get(OPERATION, "another-key", "ada@example.com", status).getString("error"));
        assertEquals(200, status[0]);
        get(OPERATION, ProviderStubServlet.TOKEN, "", status);
        assertEquals(400, status[0]);
        get("/v1/validate", ProviderStubServlet.TOKEN, "ada@example.com", status);
        assertEquals(404, status[0]);

        HttpServletResponse response = mock(HttpServletResponse.class);
        new ZeroBounceStubServlet().doPost(mock(HttpServletRequest.class), response);
        verify(response).sendError(405);
    }
}
