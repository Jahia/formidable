package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sample hold ranks above the hold of formidable-quarantine and claims only a form whose checks hold a
 * {@code fmdbsample:sampleCheck}: it hands any other form to the next hold below it.
 */
class SampleSubmissionHoldTest {

    private DeferredActions deferredActions;
    private SubmissionHold quarantineHold;

    @BeforeEach
    void setUp() {
        UploadObservations.reset();
        deferredActions = mock(DeferredActions.class);
        quarantineHold = mock(SubmissionHold.class);
    }

    private static HeldSubmission submission() {
        return new HeldSubmission("form-id", "mysite", Locale.ENGLISH, Map.of("fullName", List.of("Ada")), Map.of(),
                List.of(new SubmittedFile("cv", "cv.pdf", "application/pdf", new byte[] {1})),
                new SubmissionOrigin(null, null, null));
    }

    private static Map<String, Object> ranked(int ranking, long serviceId) {
        return Map.of(LowerHolds.SERVICE_RANKING, ranking, LowerHolds.SERVICE_ID, serviceId);
    }

    private SampleSubmissionHold hold(SampleChecks checks) {
        SampleSubmissionHold hold = new SampleSubmissionHold(checks);
        hold.setDeferredActions(deferredActions);
        hold.addOtherHold(quarantineHold, ranked(100, 10L));
        return hold;
    }

    private static JCRNodeWrapper formWithChecks(String... types) throws RepositoryException {
        List<JCRNodeWrapper> checks = new ArrayList<>();
        for (String type : types) {
            JCRNodeWrapper check = mock(JCRNodeWrapper.class);
            when(check.getPrimaryNodeTypeName()).thenReturn(type);
            checks.add(check);
        }
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.iterator()).thenReturn(checks.iterator());
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        when(list.getNodes()).thenReturn(iterator);
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.hasNode(SampleChecks.QUALIFIERS)).thenReturn(true);
        when(form.getNode(SampleChecks.QUALIFIERS)).thenReturn(list);
        return form;
    }

    @Test
    void aFormWithASampleCheckIsReleasedAtOnceAndNotHandedOn() throws Exception {
        HeldSubmission submission = submission();

        hold(formId -> true).hold(submission);

        verify(deferredActions).run(any(), anyInt(), any(IntConsumer.class));
        verify(quarantineHold, never()).hold(any());
        assertEquals(1, UploadObservations.json().getJSONArray("hold").getInt(0));
    }

    @Test
    void aFormWithNoSampleCheckIsHandedToTheNextHoldBelow() throws Exception {
        HeldSubmission submission = submission();

        hold(formId -> false).hold(submission);

        verify(quarantineHold).hold(submission);
        verify(deferredActions, never()).run(any(), anyInt(), any(IntConsumer.class));
        assertEquals(0, UploadObservations.json().getJSONArray("hold").length());
    }

    @Test
    void aFormWithNoSampleCheckIsRefusedWhenNoHoldRanksBelow() throws Exception {
        SampleSubmissionHold hold = new SampleSubmissionHold(formId -> false);
        hold.setDeferredActions(deferredActions);
        hold.addOtherHold(quarantineHold, ranked(300, 10L));

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));

        verify(quarantineHold, never()).hold(any());
    }

    @Test
    void aFormWhoseChecksCannotBeReadIsRefusedAndHandedToNoOtherHold() throws Exception {
        SampleSubmissionHold hold = hold(formId -> {
            throw new RepositoryException("the form is not in live");
        });

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));

        verify(quarantineHold, never()).hold(any());
        verify(deferredActions, never()).run(any(), anyInt(), any(IntConsumer.class));
    }

    @Test
    void theRankingTheComponentIsBoundWithDecidesWhichHoldsAreBelow() throws Exception {
        SampleSubmissionHold hold = hold(formId -> false);
        hold.configure(Map.of(LowerHolds.SERVICE_RANKING, 50));
        HeldSubmission submission = submission();

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission));

        verify(quarantineHold, never()).hold(any());
    }

    @Test
    void theSampleHoldRanksAboveTheHoldOfTheQuarantineModule() {
        assertTrue(SampleSubmissionHold.RANKING > 100);
        assertEquals(SampleSubmissionHold.COMPONENT_NAME, SampleSubmissionHold.class.getName());
    }

    @Test
    void theClaimReadsTheSampleCheckInTheList() throws RepositoryException {
        assertTrue(SampleChecks.holdsASampleCheck(
                formWithChecks("fmdbquar:remoteStubCheck", SampleChecks.SAMPLE_CHECK)));
        assertFalse(SampleChecks.holdsASampleCheck(formWithChecks("fmdbquar:remoteStubCheck")));
        assertFalse(SampleChecks.holdsASampleCheck(formWithChecks()));
        assertFalse(SampleChecks.holdsASampleCheck(mock(JCRNodeWrapper.class)));
    }
}
