package org.jahia.modules.formidable.quarantine.hold;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
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
 * The rule of ownership of the hold of the module, one test per row of the table of docs/architecture/quarantine.md,
 * "Ownership of a form": it stores a form of a site that has the module, hands any other form to the next hold below
 * its ranking, and refuses when there is none, or when it cannot read the form or its site.
 */
class QuarantineSubmissionHoldTest {

    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";

    private QuarantineStore store;
    private SubmissionHold lowerHold;

    @BeforeEach
    void setUp() {
        store = mock(QuarantineStore.class);
        lowerHold = mock(SubmissionHold.class);
    }

    private static HeldSubmission submission() {
        SubmittedFile file = new SubmittedFile("supportingDocument", "claim-note.txt", "text/plain",
                "A note".getBytes(StandardCharsets.UTF_8));
        return new HeldSubmission(FORM_ID, "formidableTestSite", Locale.ENGLISH, Map.of("fullName", List.of("Ada")),
                Map.of(), List.of(file), new SubmissionOrigin("en", null, null));
    }

    private QuarantineSubmissionHold hold(boolean siteHasTheModule) {
        QuarantineSubmissionHold hold = new QuarantineSubmissionHold(formId -> siteHasTheModule);
        hold.setStore(store);
        return hold;
    }

    private static Map<String, Object> ranked(int ranking, long serviceId) {
        return Map.of(OtherHolds.SERVICE_RANKING, ranking, OtherHolds.SERVICE_ID, serviceId);
    }

    @Test
    void aFormOfASiteThatHasTheModuleIsStoredAndNotHandedOn() throws Exception {
        QuarantineSubmissionHold hold = hold(true);
        hold.addOtherHold(lowerHold, ranked(0, 10L));
        HeldSubmission submission = submission();

        hold.hold(submission);

        verify(store).hold(submission);
        verify(lowerHold, never()).hold(any());
    }

    @Test
    void aFormOfASiteWithoutTheModuleIsHandedToTheNextHoldAndNotStored() throws Exception {
        QuarantineSubmissionHold hold = hold(false);
        hold.addOtherHold(lowerHold, ranked(0, 10L));
        HeldSubmission submission = submission();

        hold.hold(submission);

        verify(lowerHold).hold(submission);
        verify(store, never()).hold(any());
    }

    @Test
    void aFormOfASiteWithoutTheModuleIsRefusedWhenNoOtherHoldIsRegistered() throws Exception {
        QuarantineSubmissionHold hold = hold(false);

        SubmissionHoldException refused = assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));

        assertTrue(refused.getMessage().contains("no other hold ranks below it"), refused.getMessage());
        verify(store, never()).hold(any());
    }

    @Test
    void aFormWhoseSiteCannotBeReadIsRefusedAndHandedToNoOtherHold() throws Exception {
        QuarantineSubmissionHold hold = new QuarantineSubmissionHold(formId -> {
            throw new RepositoryException("the form is not in live");
        });
        hold.setStore(store);
        hold.addOtherHold(lowerHold, ranked(0, 10L));

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));

        verify(lowerHold, never()).hold(any());
        verify(store, never()).hold(any());
    }

    @Test
    void aHoldThatRanksAboveIsNeverHandedTheFormBack() throws Exception {
        // the sample hold ranks above and handed the form down: handing it back would loop
        SubmissionHold higherHold = mock(SubmissionHold.class);
        QuarantineSubmissionHold hold = hold(false);
        hold.addOtherHold(higherHold, ranked(200, 10L));

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));

        verify(higherHold, never()).hold(any());
    }

    @Test
    void aFormIsRefusedOnceTheOtherHoldIsGone() throws Exception {
        QuarantineSubmissionHold hold = hold(false);
        hold.addOtherHold(lowerHold, ranked(0, 10L));
        hold.removeOtherHold(lowerHold);

        assertThrows(SubmissionHoldException.class, () -> hold.hold(submission()));
        verify(lowerHold, never()).hold(any());
    }

    @Test
    void aStoreThatFailsRefusesTheSubmission() throws Exception {
        QuarantineSubmissionHold hold = hold(true);
        HeldSubmission submission = submission();
        when(store.hold(submission)).thenThrow(new QuarantineStoreException("the repository refused the write"));

        SubmissionHoldException refused = assertThrows(SubmissionHoldException.class, () -> hold.hold(submission));

        assertTrue(refused.getMessage().contains(FORM_ID), refused.getMessage());
        assertFalse(refused.getMessage().contains("Ada"), "the message names no value");
    }

    @Test
    void theNextHoldIsTheOneOfTheHighestRankingBelowThenOfTheLowestServiceId() {
        SubmissionHold low = mock(SubmissionHold.class);
        SubmissionHold high = mock(SubmissionHold.class);
        SubmissionHold highButLater = mock(SubmissionHold.class);
        SubmissionHold above = mock(SubmissionHold.class);
        OtherHolds others = new OtherHolds();
        others.add(low, ranked(0, 1L));
        others.add(highButLater, ranked(5, 30L));
        others.add(high, ranked(5, 20L));
        others.add(above, ranked(200, 5L));

        assertSame(high, others.nextBelow(QuarantineSubmissionHold.RANKING).orElseThrow());

        others.remove(high);
        assertSame(highButLater, others.nextBelow(QuarantineSubmissionHold.RANKING).orElseThrow());
    }

    @Test
    void aNewRankingOfABoundHoldChangesTheNextHold() throws Exception {
        QuarantineSubmissionHold hold = hold(false);
        SubmissionHold other = mock(SubmissionHold.class);
        hold.addOtherHold(lowerHold, ranked(5, 10L));
        hold.addOtherHold(other, ranked(0, 20L));
        HeldSubmission submission = submission();

        hold.updatedOtherHold(other, ranked(10, 20L));
        hold.hold(submission);

        verify(other).hold(submission);
        verify(lowerHold, never()).hold(any());
    }

    @Test
    void aRankingThatIsNotAnIntegerCountsAsZero() {
        SubmissionHold odd = mock(SubmissionHold.class);
        SubmissionHold ranked = mock(SubmissionHold.class);
        OtherHolds others = new OtherHolds();
        others.add(odd, Map.of(OtherHolds.SERVICE_RANKING, "50", OtherHolds.SERVICE_ID, 1L));
        others.add(ranked, ranked(1, 2L));

        assertSame(ranked, others.nextBelow(QuarantineSubmissionHold.RANKING).orElseThrow());
    }

    @Test
    void theHoldOfTheModuleRanksOneHundred() {
        assertEquals(100, QuarantineSubmissionHold.RANKING);
        assertEquals(QuarantineSubmissionHold.COMPONENT_NAME, QuarantineSubmissionHold.class.getName());
    }
}
