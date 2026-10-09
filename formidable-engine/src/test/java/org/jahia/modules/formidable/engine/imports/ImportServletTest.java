package org.jahia.modules.formidable.engine.imports;

import org.jahia.services.securityfilter.PermissionService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The routing and the first gate of the endpoint, before any session is opened. */
class ImportServletTest {

    private final StringWriter body = new StringWriter();

    private HttpServletRequest request(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getPathInfo()).thenReturn(path);
        when(request.getRequestURI()).thenReturn("/modules/formidable-engine/import" + path);
        return request;
    }

    private HttpServletResponse response() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return response;
    }

    @Test
    void anUnknownPathIsNotFoundBeforeAnyCheck() throws Exception {
        ImportServlet servlet = new ImportServlet();
        HttpServletResponse response = response();

        servlet.doGet(request("GET", "/jobs/abc/import"), response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        assertEquals("no such endpoint", new JSONObject(body.toString()).getString("error"));
    }

    @Test
    void aRouteIsRefusedWhenTheApiPermissionIsNotGranted() throws Exception {
        ImportServlet servlet = new ImportServlet();
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.hasPermission(anyMap())).thenReturn(false);
        servlet.setPermissionService(permissions);
        HttpServletResponse response = response();

        servlet.doDelete(request("DELETE", "/jobs/job-1"), response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertEquals("not allowed", new JSONObject(body.toString()).getString("error"));
    }

    @Test
    void withoutAPermissionServiceNothingIsAllowed() throws Exception {
        ImportServlet servlet = new ImportServlet();
        HttpServletResponse response = response();

        servlet.doPost(request("POST", "/jobs"), response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
