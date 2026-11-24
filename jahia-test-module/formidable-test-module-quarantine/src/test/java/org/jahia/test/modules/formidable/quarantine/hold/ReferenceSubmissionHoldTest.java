package org.jahia.test.modules.formidable.quarantine.hold;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rule of ownership of the reference hold (design rule 2): it stores a form whose "Checks" list holds a check of
 * the module, hands any other form to the next hold by ranking, and refuses when there is none, or when it cannot
 * read the list.
 */
class ReferenceSubmissionHoldTest {

    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";

    private QuarantineStore store;
    private SubmissionHold sampleHold;

    @BeforeEach
    void setUp() {
        store = mock(QuarantineStore.class);
        sampleHold = mock(SubmissionHold.class);
    }

    private static HeldSubmission submission() {
        SubmittedFile file = new SubmittedFile("supportingDocument", "claim-note.txt", "text/plain",
                "A note".getBytes(StandardCharsets.UTF_8));
        return new HeldSubmission(FORM_ID, "formidableTestSite", Locale.ENGLISH, Map.of("fullName", List.of("Ada")),
                Map.of(), List.of(file), new SubmissionOrigin("en", null, null));
    }

    private ReferenceSubmissionHold hold(boolean ownsTheForm) {
        ReferenceSubmissionHold hold = new ReferenceSubmissionHold(formId -> ownsTheForm);
        hold.setStore(store);
        return hold;
    }

    private static Map<String, Object> ranked(int ranking, long serviceId) {
        return Map.of(OtherHolds.SERVICE_RANKING, ranking, OtherHolds.SERVICE_ID, serviceId);
    }

    @Test
    void aFormWithACheckOfTheModuleIsStoredAndNotHandedOn() throws Exception {
        ReferenceSubmissionHold hold = hold(true);
        hold.addOtherHold(sampleHold, ranked(0, 10L));
        HeldSubmission submission = submission();

        hold.hold(submission);

        verify(store).hold(submission);
        verify(sampleHold, never()).hold(any());
    }

    @Test
    void aFormWithNoCheckOfTheModuleIsHandedToTheNextHoldAndNotStored() throws Exception {
        ReferenceSubmissionHold hold = hold(false);
        hold.addOtherHold(sampleHold, ranked(0, 10L));
        HeldSubmission submission = submission();

        hold.hold(submission);

        verify(sampleHold).hold(submission);
        verify(store, never()).hold(any());
    }

    @Test
    void aFormWithNoCheckOfTheModuleIsRefusedWhenNoOtherHoldIsRegistered() throws Exception {
        ReferenceSubmissionHold hold = hold(false);

        SubmissionHoldException refused = assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));

        assertTrue(refused.getMessage().contains("no other hold is registered"), refused.getMessage());
        verify(store, never()).hold(any());
    }

    @Test
    void aFormIsRefusedOnceTheOtherHoldIsGone() throws Exception {
        ReferenceSubmissionHold hold = hold(false);
        hold.addOtherHold(sampleHold, ranked(0, 10L));
        hold.removeOtherHold(sampleHold);

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));
        verify(sampleHold, never()).hold(any());
    }

    @Test
    void aFormWhoseListCannotBeReadIsRefusedAndHandedToNoOtherHold() throws Exception {
        ReferenceSubmissionHold hold = new ReferenceSubmissionHold(formId -> {
            throw new RepositoryException("the form is not in live");
        });
        hold.setStore(store);
        hold.addOtherHold(sampleHold, ranked(0, 10L));

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));

        verify(sampleHold, never()).hold(any());
        verify(store, never()).hold(any());
    }

    @Test
    void aStoreThatFailsRefusesTheSubmission() throws Exception {
        ReferenceSubmissionHold hold = hold(true);
        HeldSubmission submission = submission();
        when(store.hold(submission)).thenThrow(new QuarantineStoreException("the repository refused the write"));

        SubmissionHoldException refused = assertThrows(SubmissionHoldException.class, () -> hold.hold(submission));

        assertTrue(refused.getMessage().contains(FORM_ID), refused.getMessage());
        assertFalse(refused.getMessage().contains("Ada"), "the message names no value");
    }

    @Test
    void theNextHoldIsTheOneOfTheHighestRankingThenOfTheLowestServiceId() {
        SubmissionHold low = mock(SubmissionHold.class);
        SubmissionHold high = mock(SubmissionHold.class);
        SubmissionHold highButLater = mock(SubmissionHold.class);
        OtherHolds others = new OtherHolds();
        others.add(low, ranked(0, 1L));
        others.add(highButLater, ranked(5, 30L));
        others.add(high, ranked(5, 20L));

        assertSame(high, others.next().orElseThrow());

        others.remove(high);
        assertSame(highButLater, others.next().orElseThrow());
    }

    @Test
    void aNewRankingOfABoundHoldChangesTheNextHold() throws Exception {
        ReferenceSubmissionHold hold = hold(false);
        SubmissionHold other = mock(SubmissionHold.class);
        hold.addOtherHold(sampleHold, ranked(5, 10L));
        hold.addOtherHold(other, ranked(0, 20L));
        HeldSubmission submission = submission();

        hold.updatedOtherHold(other, ranked(10, 20L));
        hold.hold(submission);

        verify(other).hold(submission);
        verify(sampleHold, never()).hold(any());
    }

    @Test
    void aRankingThatIsNotAnIntegerCountsAsZero() {
        SubmissionHold odd = mock(SubmissionHold.class);
        SubmissionHold ranked = mock(SubmissionHold.class);
        OtherHolds others = new OtherHolds();
        others.add(odd, Map.of(OtherHolds.SERVICE_RANKING, "50", OtherHolds.SERVICE_ID, 1L));
        others.add(ranked, ranked(1, 2L));

        assertSame(ranked, others.next().orElseThrow());
    }

    @Test
    void theReferenceHoldRanksAboveTheSampleHoldWhichDeclaresNoRanking() {
        assertTrue(ReferenceSubmissionHold.RANKING > 0);
        assertEquals(ReferenceSubmissionHold.COMPONENT_NAME, ReferenceSubmissionHold.class.getName());
    }
}
