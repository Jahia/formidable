package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The samples the end-to-end suite reads the uploads from: the observer action, the sample hold and the receiver of
 * the samples' forward target. What each records is a count or a name.
 */
class UploadSampleFormActionsTest {

    @BeforeEach
    void forget() {
        UploadObservations.reset();
    }

    private static SubmittedFile file() {
        return new SubmittedFile("cv", "cv.pdf", "application/pdf", new byte[] {1});
    }

    private static FormActionContext context(String action, String list, List<SubmittedFile> files) throws Exception {
        JCRNodeWrapper listNode = mock(JCRNodeWrapper.class);
        when(listNode.getName()).thenReturn(list);
        JCRNodeWrapper actionNode = mock(JCRNodeWrapper.class);
        when(actionNode.getName()).thenReturn(action);
        when(actionNode.getParent()).thenReturn(listNode);
        // no response: after release the action has none to write on
        return new FormActionContext(actionNode, null, mock(HttpServletRequest.class), null, null,
                Locale.ENGLISH, "mysite", Map.of("fullName", List.of("Ada")), Map.of(), files);
    }

    private static HeldSubmission submission(List<SubmittedFile> files) {
        return new HeldSubmission("form-id", "mysite", Locale.ENGLISH, Map.of("fullName", List.of("Ada")), Map.of(),
                files, new SubmissionOrigin(null, null, null));
    }

    @Test
    void theObserverRecordsTheNumberOfFilesItReceivedAndWhereItRan() throws Exception {
        // Verifies the observable the suite reads: one entry for each call, in order, with the name of the action,
        // the list that holds it and the count of the files the engine handed over. No response is needed.
        UploadObserverFormAction action = new UploadObserverFormAction();

        action.execute(context("first", "actions", List.of()));
        action.execute(context("second", "deferredActions", List.of(file(), file())));

        JSONObject seen = UploadObservations.json();
        assertEquals(2, seen.getJSONArray("observer").length());
        JSONObject first = seen.getJSONArray("observer").getJSONObject(0);
        assertEquals("first", first.getString("action"));
        assertEquals("actions", first.getString("list"));
        assertEquals(0, first.getInt("files"));
        JSONObject second = seen.getJSONArray("observer").getJSONObject(1);
        assertEquals("deferredActions", second.getString("list"));
        assertEquals(2, second.getInt("files"));
    }

    @Test
    void theSampleHoldRecordsItsFilesAndReleasesAtOnce() throws Exception {
        // Verifies the double: it records the count of the files it was handed, then runs the actions after release
        // of the same submission, from the first one.
        DeferredActions deferredActions = mock(DeferredActions.class);
        SampleSubmissionHold hold = new SampleSubmissionHold();
        hold.setDeferredActions(deferredActions);
        HeldSubmission held = submission(List.of(file()));

        hold.hold(held);

        assertEquals(1, UploadObservations.json().getJSONArray("hold").getInt(0));
        verify(deferredActions).run(eq(held), eq(0), any(IntConsumer.class));
    }

    @Test
    void theSampleHoldRefusesTheSubmissionWhenAnActionAfterReleaseFails() throws Exception {
        // Verifies a release that fails inside the request refuses the submission, with the failure as its cause.
        DeferredActions deferredActions = mock(DeferredActions.class);
        DeferredActionException failure = new DeferredActionException("Action 't:mail' failed", 0, 1, null);
        doThrow(failure).when(deferredActions).run(any(), eq(0), any(IntConsumer.class));
        SampleSubmissionHold hold = new SampleSubmissionHold();
        hold.setDeferredActions(deferredActions);

        SubmissionHoldException error = assertThrows(SubmissionHoldException.class,
                () -> hold.hold(submission(List.of(file()))));

        assertSame(failure, error.getCause());
    }

    @Test
    void aResetForgetsEveryCall() throws Exception {
        // Verifies a spec starts from nothing: after a reset, neither sample reports a call.
        new UploadObserverFormAction().execute(context("first", "actions", List.of()));
        UploadObservations.hold(1);

        UploadObservations.reset();

        assertEquals(0, UploadObservations.json().getJSONArray("observer").length());
        assertEquals(0, UploadObservations.json().getJSONArray("hold").length());
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
