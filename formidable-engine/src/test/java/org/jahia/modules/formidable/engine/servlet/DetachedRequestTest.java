package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.DetachedRequestException;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stand-ins an action receives after release: what they answer, and that they refuse the rest by name. */
class DetachedRequestTest {

    private static final SubmissionOrigin ORIGIN = new SubmissionOrigin("fr", "https://example.com/claim", "Europe/Paris");

    @Test
    void theRequestAnswersTheThreeValuesOfTheOrigin() {
        // Verifies the documented list, with a header name in any case, as a servlet container answers it.
        HttpServletRequest request = DetachedRequest.request(ORIGIN);

        assertEquals("fr", request.getParameter("lang"));
        assertEquals("https://example.com/claim", request.getHeader("Referer"));
        assertEquals("https://example.com/claim", request.getHeader("referer"));
        assertEquals("Europe/Paris", request.getHeader("X-Formidable-Time-Zone"));
    }

    @Test
    void aValueTheSubmissionDidNotCarryIsNull() {
        // Verifies an absent value reads as it does on a request: null, and no exception.
        HttpServletRequest request = DetachedRequest.request(new SubmissionOrigin(null, null, null));

        assertNull(request.getParameter("lang"));
        assertNull(request.getHeader("Referer"));
        assertNull(request.getHeader("X-Formidable-Time-Zone"));
    }

    @Test
    void theRequestRefusesAnyOtherCallByName() {
        // Verifies an action that reads anything else fails by the named exception, and that the message says what
        // was asked, so that the developer of the action finds the call.
        HttpServletRequest request = DetachedRequest.request(ORIGIN);

        DetachedRequestException header = assertThrows(DetachedRequestException.class, () -> request.getHeader("Cookie"));
        DetachedRequestException parameter = assertThrows(DetachedRequestException.class, () -> request.getParameter("fid"));
        assertThrows(DetachedRequestException.class, request::getRemoteAddr);
        assertThrows(DetachedRequestException.class, request::getSession);
        assertThrows(DetachedRequestException.class, () -> request.getAttribute("org.jahia.user"));

        assertTrue(header.getMessage().contains("HttpServletRequest#getHeader(\"Cookie\")"), header.getMessage());
        assertTrue(parameter.getMessage().contains("getParameter(\"fid\")"), parameter.getMessage());
    }

    @Test
    void theResponseRefusesEveryCall() {
        // Verifies no action writes to a browser that is gone: a header and a cookie are refused alike.
        HttpServletResponse response = DetachedRequest.response();

        assertThrows(DetachedRequestException.class, () -> response.setHeader("X-Sample", "1"));
        assertThrows(DetachedRequestException.class, () -> response.sendRedirect("/home"));
    }

    @Test
    void aStandInBehavesAsAnObject() {
        // Verifies a log line or a collection that holds a stand-in does not trip the refusal.
        HttpServletRequest request = DetachedRequest.request(ORIGIN);

        assertEquals("DetachedRequest", request.toString());
        assertTrue(Set.of(request).contains(request));
        assertNotEquals(request, DetachedRequest.request(ORIGIN));
        assertEquals(request.hashCode(), request.hashCode());
    }
}
