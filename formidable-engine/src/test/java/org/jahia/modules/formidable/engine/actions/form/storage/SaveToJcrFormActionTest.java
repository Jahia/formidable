package org.jahia.modules.formidable.engine.actions.form.storage;

import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.servlet.DetachedRequest;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SaveToJcrFormActionTest {

    @Test
    void aKnownZoneIdIsKeptAsDeclared() {
        assertEquals("Europe/Paris", SaveToJcrFormAction.submitterTimeZone("Europe/Paris"));
        assertEquals("America/New_York", SaveToJcrFormAction.submitterTimeZone(" America/New_York "));
        // Generic zones are zones too: the results cope with them, the storage keeps them.
        assertEquals("UTC", SaveToJcrFormAction.submitterTimeZone("UTC"));
        assertEquals("Etc/GMT", SaveToJcrFormAction.submitterTimeZone("Etc/GMT"));
    }

    @Test
    void anythingButAKnownZoneIdIsDropped() {
        // The header is under the client's control and its value is shown to editors.
        assertNull(SaveToJcrFormAction.submitterTimeZone("Mars/Olympus_Mons"));
        assertNull(SaveToJcrFormAction.submitterTimeZone("europe/paris"));
        assertNull(SaveToJcrFormAction.submitterTimeZone("<script>alert(1)</script>"));
        assertNull(SaveToJcrFormAction.submitterTimeZone("Europe/" + "P".repeat(80)));
    }

    @Test
    void anAbsentOrBlankHeaderMeansNoZone() {
        assertNull(SaveToJcrFormAction.submitterTimeZone(null));
        assertNull(SaveToJcrFormAction.submitterTimeZone(""));
        assertNull(SaveToJcrFormAction.submitterTimeZone("   "));
    }

    @Test
    void theOriginOfASubmissionIsWrittenAfterReleaseWithNoHttpRequest() throws Exception {
        // Verifies all the action reads of the request is what a released submission kept: handed the stand-in, which
        // throws on any other call, the action writes the language, the referer and the time zone of the submitter.
        JCRNodeWrapper submission = mock(JCRNodeWrapper.class);

        SaveToJcrFormAction.stampOrigin(submission,
                DetachedRequest.request(new SubmissionOrigin("fr", "https://example.com/claim", "Europe/Paris")));

        verify(submission).setProperty("origin", "formidable");
        verify(submission).setProperty("locale", "fr");
        verify(submission).setProperty("referer", "https://example.com/claim");
        verify(submission).setProperty("timeZone", "Europe/Paris");
    }

    @Test
    void aTimeZoneKeptForAReleaseIsStillValidatedWhenItIsWritten() throws Exception {
        // Verifies the value a hold stored is no more trusted than a header: a zone the platform does not know is
        // dropped at release, as it is in a request.
        JCRNodeWrapper submission = mock(JCRNodeWrapper.class);

        SaveToJcrFormAction.stampOrigin(submission,
                DetachedRequest.request(new SubmissionOrigin(null, null, "<script>alert(1)</script>")));

        verify(submission, never()).setProperty(eq("timeZone"), anyString());
        verify(submission, never()).setProperty(eq("locale"), anyString());
    }
}
