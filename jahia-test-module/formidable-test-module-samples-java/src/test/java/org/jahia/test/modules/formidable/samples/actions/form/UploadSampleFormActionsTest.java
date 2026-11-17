package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The two sample actions the end-to-end suite reads on either side of the split of the uploads, and the receiver of
 * the samples' forward target: what each declares, and that what each reports is a count or a name.
 */
class UploadSampleFormActionsTest {

    private static FormActionContext context(HttpServletResponse response, List<SubmittedFile> files) {
        return new FormActionContext(mock(JCRNodeWrapper.class), null, mock(HttpServletRequest.class), response, null,
                Locale.ENGLISH, "mysite", Map.of("fullName", List.of("Ada")), Map.of(), files);
    }

    private static SubmittedFile file() {
        return new SubmittedFile("cv", "cv.pdf", "application/pdf", new byte[] {1});
    }

    @Test
    void theQuarantiningActionOptsInAndReportsTheNumberOfFilesItReceived() {
        // Verifies the opt-in the suite relies on, whatever the node, and the observable it reads: a count.
        UploadQuarantiningFormAction action = new UploadQuarantiningFormAction();
        assertTrue(action.quarantinesUploads(mock(JCRNodeWrapper.class)));

        HttpServletResponse response = mock(HttpServletResponse.class);
        action.execute(context(response, List.of(file())));

        verify(response).setHeader(UploadQuarantiningFormAction.FILES_HEADER, "1");
    }

    @Test
    void theObserverKeepsTheDefaultAndReportsTheNumberOfFilesItReceived() {
        // Verifies that the sample relies on the default of the interface (it does not quarantine), and that it
        // reports what the pipeline handed it: here two files, and none once the pipeline withholds them.
        UploadObserverFormAction action = new UploadObserverFormAction();
        assertFalse(action.quarantinesUploads(mock(JCRNodeWrapper.class)));

        HttpServletResponse withFiles = mock(HttpServletResponse.class);
        action.execute(context(withFiles, List.of(file(), file())));
        HttpServletResponse withoutFiles = mock(HttpServletResponse.class);
        action.execute(context(withoutFiles, List.of()));

        verify(withFiles).setHeader(UploadObserverFormAction.FILES_HEADER, "2");
        verify(withoutFiles).setHeader(UploadObserverFormAction.FILES_HEADER, "0");
    }

    @Test
    void theForwardSinkTellsAFilePartFromAValuePartByItsFileName() {
        // Verifies the reading of a forwarded submission, on the body the forward action writes: a part with a file
        // name is a file, any other a value, and the content of a part is never taken for a part.
        // each line ends on \r, which the line break of the text block follows: the CRLF of a multipart body
        String body = """
                --b\r
                Content-Disposition: form-data; name="fullName"\r
                \r
                Ada\r
                --b\r
                Content-Disposition: form-data; name="cv"; filename="cv.pdf"; filename*=UTF-8''cv.pdf\r
                Content-Type: application/pdf\r
                \r
                %PDF\r
                --b--\r
                """;

        ForwardSinkServlet.Received received = ForwardSinkServlet.parts(body, 3);

        assertEquals(3, received.count());
        assertEquals(List.of("fullName"), received.fields());
        assertEquals(List.of("cv"), received.files());
    }
}
