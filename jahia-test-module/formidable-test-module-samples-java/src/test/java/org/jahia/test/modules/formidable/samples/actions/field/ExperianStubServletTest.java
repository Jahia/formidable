package org.jahia.test.modules.formidable.samples.actions.field;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The double of the provider: it answers as Experian's documentation says, on the operation, the token and the
 * body the sample action sends, and refuses everything else the way the provider would.
 */
class ExperianStubServletTest {

    private static final String OPERATION = "/email/validate/v2";

    /** One call of the stub, answered into a status and a JSON body. */
    private static final class Call {
        final int status;
        final JSONObject body;

        Call(int status, String body) {
            this.status = status;
            this.body = body.isEmpty() ? null : new JSONObject(body);
        }
    }

    private static Call post(String pathInfo, String uri, String token, String body) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn(pathInfo);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getHeader("Auth-Token")).thenReturn(token);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        int[] status = new int[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            status[0] = invocation.getArgument(0);
            return null;
        }).when(response).setStatus(org.mockito.ArgumentMatchers.anyInt());

        new ExperianStubServlet().doPost(request, response);

        return new Call(status[0], out.toString());
    }

    private static Call post(String token, String email) throws IOException {
        return post(OPERATION, null, token, "{\"email\":\"" + email + "\"}");
    }

    private static String confidenceOf(Call call) {
        return call.body.getJSONObject("result").getString("confidence");
    }

    @Test
    void theDocumentedOperationWithTheTokenAnswersTheConfidenceTheDomainSays() throws IOException {
        // Verifies the nominal answers, in the provider's JSON: verified for an ordinary domain, and the confidence
        // a domain of the .test zone is named after — the addresses the Cypress spec types.
        Call verified = post(ProviderStubServlet.TOKEN, "ada@example.com");
        assertEquals(200, verified.status);
        assertEquals("verified", confidenceOf(verified));
        assertEquals("ada@example.com", verified.body.getJSONObject("result").getString("email"));

        assertEquals("undeliverable", confidenceOf(post(ProviderStubServlet.TOKEN, "ada@undeliverable.test")));
        assertEquals("disposable", confidenceOf(post(ProviderStubServlet.TOKEN, "ada@disposable.test")));
        assertEquals("unknown", confidenceOf(post(ProviderStubServlet.TOKEN, "ada@unknown.test")));
        assertEquals("acceptAll", confidenceOf(post(ProviderStubServlet.TOKEN, "ada@acceptall.test")));
        assertEquals("undeliverable", confidenceOf(post(ProviderStubServlet.TOKEN, "not-an-address")));
    }

    @Test
    void anythingElseIsRefusedAsTheProviderWould() throws IOException {
        // Verifies what the stub holds the caller to: the operation's path, the token the gateway must inject, a
        // body with the address — and the provider's own timeout on a domain that does not respond.
        assertEquals(404, post("/email/validate/v1", null, ProviderStubServlet.TOKEN, "{\"email\":\"ada@example.com\"}").status);
        assertEquals(401, post(OPERATION, null, "another-token", "{\"email\":\"ada@example.com\"}").status);
        assertEquals(401, post(OPERATION, null, null, "{\"email\":\"ada@example.com\"}").status);
        assertEquals(400, post(OPERATION, null, ProviderStubServlet.TOKEN, "{\"address\":\"ada@example.com\"}").status);
        assertEquals(400, post(OPERATION, null, ProviderStubServlet.TOKEN, "not json").status);
        assertEquals(408, post(ProviderStubServlet.TOKEN, "ada@timeout.test").status);

        HttpServletResponse response = mock(HttpServletResponse.class);
        new ExperianStubServlet().doGet(mock(HttpServletRequest.class), response);
        verify(response).sendError(405);
    }

    @Test
    void theOperationIsReadPastTheAliasWhenTheContainerGivesNoPathInfo() throws IOException {
        // Verifies the fallback for a container that dispatches on the alias without a path info: the operation is
        // what follows the alias in the request URI, and nothing else of the URI matters.
        Call call = post(null, "/modules" + ExperianStubServlet.ALIAS + OPERATION, ProviderStubServlet.TOKEN, "{\"email\":\"ada@example.com\"}");
        assertEquals(200, call.status);
        assertEquals("verified", confidenceOf(call));
        assertNull(post(null, "/modules" + ExperianStubServlet.ALIAS, ProviderStubServlet.TOKEN, "{}").body.optJSONObject("result"));
    }
}
