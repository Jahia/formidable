package org.jahia.modules.formidable.engine.actions.form.storage;

import org.jahia.modules.formidable.engine.api.FmdbNodeName;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void aSubmissionStoredWithNoFileHoldsItsValuesAlone() throws Exception {
        // Verifies what a quarantining action relies on: with an empty list of files, the submission keeps its
        // values, a blank one left out, and gets no files folder.
        JCRNodeWrapper submission = mock(JCRNodeWrapper.class);
        JCRNodeWrapper data = mock(JCRNodeWrapper.class);
        when(submission.getNode(FmdbNodeName.DATA)).thenReturn(data);
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("fullName", List.of("Ada Lovelace"));
        values.put("comment", List.of(" "));

        SaveToJcrFormAction.writeContent(submission, values, List.of(), mock(JCRSessionWrapper.class));

        verify(data).setProperty("fullName", "Ada Lovelace");
        verify(data, never()).setProperty(eq("comment"), anyString());
        verify(submission, never()).addNode(anyString(), anyString());
    }

    @Test
    void aSubmissionStoredWithAFileGetsItsFilesFolder() throws Exception {
        // Verifies the other side of the same call, the one Save to JCR keeps: a file opens the files folder of the
        // submission and the folder of its field. A control of the test above, which would pass on a writer of no file.
        JCRNodeWrapper submission = mock(JCRNodeWrapper.class);
        JCRNodeWrapper data = mock(JCRNodeWrapper.class);
        JCRNodeWrapper files = mock(JCRNodeWrapper.class);
        when(submission.getNode(FmdbNodeName.DATA)).thenReturn(data);
        when(submission.addNode(FmdbNodeName.FILES, "jnt:folder")).thenReturn(files);
        // the folder of the field is where the bytes go, through Jahia's own helpers: the test stops before them
        when(files.addNode("cv", "jnt:folder")).thenThrow(new RepositoryException("stop before the bytes"));
        SubmittedFile file = new SubmittedFile("cv", "cv.pdf", "application/pdf", new byte[] {1});

        assertThrows(RepositoryException.class,
                () -> SaveToJcrFormAction.writeContent(submission, Map.of(), List.of(file), mock(JCRSessionWrapper.class)));

        verify(submission).addNode(FmdbNodeName.FILES, "jnt:folder");
        verify(files).addNode("cv", "jnt:folder");
    }

    @Test
    void anAbsentOrBlankHeaderMeansNoZone() {
        assertNull(SaveToJcrFormAction.submitterTimeZone(null));
        assertNull(SaveToJcrFormAction.submitterTimeZone(""));
        assertNull(SaveToJcrFormAction.submitterTimeZone("   "));
    }
}
