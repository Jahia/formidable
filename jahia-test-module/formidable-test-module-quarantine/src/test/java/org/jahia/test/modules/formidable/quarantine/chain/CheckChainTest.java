package org.jahia.test.modules.formidable.quarantine.chain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jahia.test.modules.formidable.quarantine.check.CheckSettings;
import org.jahia.test.modules.formidable.quarantine.check.OnInconclusive;
import org.jahia.test.modules.formidable.quarantine.check.OnUnavailable;
import org.jahia.test.modules.formidable.quarantine.check.Scope;
import org.junit.jupiter.api.Test;

import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.IMAGE;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.TEXT;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.broken;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.check;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.down;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.failed;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.image;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.inProcess;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.inconclusive;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.key;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.passed;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.pending;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.remote;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckChainTest {

    private static final int MAX_ATTEMPTS = 3;

    private final CheckChain chain = new CheckChain(false, MAX_ATTEMPTS);
    private final Map<OutcomeKey, RecordedOutcome> recorded = new HashMap<>();

    private final ChainFixtures.TestFile file = text("aaa");
    private final ChainCheck first = remote("first");
    private final ChainCheck second = remote("second");

    // --- the list itself

    @Test
    void anEmptyListReleasesNothing() {
        assertEquals(new ChainDecision.Review(ChainReason.NO_CHECK, null, null),
                chain.decide(List.of(), List.of(file), recorded));
    }

    @Test
    void aSubmissionWithNoFileIsReviewed() {
        assertEquals(new ChainDecision.Review(ChainReason.NO_FILE, null, null),
                chain.decide(List.of(first), List.of(), recorded));
    }

    @Test
    void aCheckThatNoQualifierServesStopsTheWholeList() {
        ChainCheck orphan = new ChainCheck(CheckSettings.withDefaults("test:goneCheck", "gone"), null);
        assertEquals(new ChainDecision.Review(ChainReason.QUALIFIER_MISSING, null, "gone"),
                chain.decide(List.of(first, orphan), List.of(file), recorded));
    }

    @Test
    void aQualifierOfAnotherNodeTypeDoesNotServeTheCheck() {
        ChainCheck mismatched = new ChainCheck(CheckSettings.withDefaults("test:otherCheck", "other"),
                first.qualifier());
        assertEquals(new ChainDecision.Review(ChainReason.QUALIFIER_MISSING, null, "other"),
                chain.decide(List.of(mismatched), List.of(file), recorded));
    }

    @Test
    void theChainRefusesAMaximumOfNoAttempt() {
        assertThrows(IllegalArgumentException.class, () -> new CheckChain(false, 0));
    }

    // --- order

    @Test
    void theFirstCheckOfTheListRunsFirst() {
        assertEquals(proceed(new Step.Submit(file, first)), decide(first, second));
    }

    @Test
    void theOrderOfTheListWinsOverTheOrderOfTheNames() {
        assertEquals(proceed(new Step.Submit(file, second)), decide(second, first));
    }

    @Test
    void theNextCheckRunsOnceTheEarlierOnePassed() {
        recorded.put(key(file, first), passed());
        assertEquals(proceed(new Step.Submit(file, second)), decide(first, second));
    }

    @Test
    void aLaterCheckNeverSeesAFileThatAnEarlierOneDidNotPass() {
        List<RecordedOutcome> notPassed = List.of(pending("t-1"), inconclusive(), down(1, null), broken(), failed());
        for (RecordedOutcome outcome : notPassed) {
            recorded.put(key(file, first), outcome);
            ChainDecision decision = decide(first, second);
            if (decision instanceof ChainDecision.Proceed proceed) {
                for (Step step : proceed.steps()) {
                    assertEquals(first, step.check(), "after " + outcome);
                }
            }
        }
    }

    @Test
    void aPassRecordedForOtherBytesDoesNotCount() {
        recorded.put(new OutcomeKey("other", first.name()), passed());
        assertEquals(proceed(new Step.Submit(file, first)), decide(first, second));
    }

    // --- first fail (control of Q21)

    @Test
    void theFirstFailRejectsAndTheLaterCheckNeverRuns() {
        recorded.put(key(file, first), failed());
        ChainDecision decision = decide(first, second);
        assertEquals(new ChainDecision.Reject(ChainReason.CHECK_FAILED, "aaa", "first"), decision);
    }

    @Test
    void aFailOnOneFileRejectsTheWholeSubmission() {
        ChainFixtures.TestFile other = text("bbb");
        recorded.put(key(file, first), pending("t-1"));
        recorded.put(key(other, first), failed());
        assertEquals(new ChainDecision.Reject(ChainReason.CHECK_FAILED, "bbb", "first"),
                chain.decide(List.of(first, second), List.of(file, other), recorded));
    }

    @Test
    void aFailWinsOverAReviewOfAnEarlierFile() {
        ChainFixtures.TestFile other = text("bbb");
        recorded.put(key(file, first), inconclusive());
        recorded.put(key(other, first), failed());
        assertInstanceOf(ChainDecision.Reject.class,
                chain.decide(List.of(first, second), List.of(file, other), recorded));
    }

    @Test
    void aFailOnALaterCheckRejectsToo() {
        recorded.put(key(file, first), passed());
        recorded.put(key(file, second), failed());
        assertEquals(new ChainDecision.Reject(ChainReason.CHECK_FAILED, "aaa", "second"), decide(first, second));
    }

    // --- in-process first (control of Q23)

    @Test
    void anInProcessCheckPlacedFirstRefusesTheWholeList() {
        ChainCheck parser = inProcess("parser");
        assertEquals(new ChainDecision.Review(ChainReason.IN_PROCESS_FIRST, "aaa", "parser"), decide(parser, first));
    }

    @Test
    void anInProcessCheckAloneIsRefused() {
        ChainCheck parser = inProcess("parser");
        assertEquals(new ChainDecision.Review(ChainReason.IN_PROCESS_FIRST, "aaa", "parser"), decide(parser));
    }

    @Test
    void theOperatorFlagAllowsAnInProcessCheckFirst() {
        ChainCheck parser = inProcess("parser");
        CheckChain allowing = new CheckChain(true, MAX_ATTEMPTS);
        assertEquals(proceed(new Step.Submit(file, parser)),
                allowing.decide(List.of(parser, first), List.of(file), recorded));
    }

    @Test
    void anInProcessCheckAfterARemoteOneRuns() {
        ChainCheck parser = inProcess("parser");
        recorded.put(key(file, first), passed());
        assertEquals(proceed(new Step.Submit(file, parser)), decide(first, parser));
    }

    @Test
    void anInProcessCheckFirstThatDoesNotApplyToTheFileIsNoRefusal() {
        ChainCheck imageParser = check("imageParser", Scope.IN_PROCESS, IMAGE, OnInconclusive.REVIEW,
                OnUnavailable.RETRY);
        assertEquals(proceed(new Step.Submit(file, first)), decide(imageParser, first));
    }

    @Test
    void anInProcessCheckBeforeTheFirstRemoteCheckOfAnyFileRefusesTheList() {
        ChainCheck imageScan = check("imageScan", Scope.REMOTE, IMAGE, OnInconclusive.REVIEW, OnUnavailable.RETRY);
        ChainCheck parser = check("parser", Scope.IN_PROCESS, null, OnInconclusive.REVIEW, OnUnavailable.RETRY);
        ChainFixtures.TestFile picture = image("ccc");
        assertEquals(new ChainDecision.Review(ChainReason.IN_PROCESS_FIRST, "aaa", "parser"),
                chain.decide(List.of(imageScan, parser, first), List.of(picture, file), recorded));
    }

    // --- withheld file

    @Test
    void aFileThatNoCheckAppliesToIsWithheld() {
        ChainFixtures.TestFile picture = image("ccc");
        assertEquals(new ChainDecision.Review(ChainReason.FILE_WITHHELD, "ccc", null),
                chain.decide(List.of(first, second), List.of(picture), recorded));
    }

    @Test
    void theOtherFilesAreCheckedBeforeAFileIsWithheld() {
        ChainFixtures.TestFile picture = image("ccc");
        assertEquals(proceed(new Step.Submit(file, first)),
                chain.decide(List.of(first), List.of(picture, file), recorded));
        recorded.put(key(file, first), passed());
        assertEquals(new ChainDecision.Review(ChainReason.FILE_WITHHELD, "ccc", null),
                chain.decide(List.of(first), List.of(picture, file), recorded));
    }

    // --- release

    @Test
    void everyCheckPassedOnEveryFileReleases() {
        ChainFixtures.TestFile other = text("bbb");
        recorded.put(key(file, first), passed());
        recorded.put(key(file, second), passed());
        recorded.put(key(other, first), passed());
        recorded.put(key(other, second), passed());
        assertEquals(ChainDecision.RELEASE, chain.decide(List.of(first, second), List.of(file, other), recorded));
    }

    @Test
    void eachFileProceedsAtItsOwnCheck() {
        ChainFixtures.TestFile other = text("bbb");
        recorded.put(key(file, first), passed());
        recorded.put(key(other, first), pending("t-2"));
        assertEquals(new ChainDecision.Proceed(List.of(new Step.Submit(file, second),
                        new Step.Poll(other, first, "t-2"))),
                chain.decide(List.of(first, second), List.of(file, other), recorded));
    }

    // --- pending

    @Test
    void aPendingCheckIsPolledWithItsTicket() {
        recorded.put(key(file, first), pending("t-1"));
        assertEquals(proceed(new Step.Poll(file, first, "t-1")), decide(first, second));
    }

    // --- inconclusive

    @Test
    void anInconclusiveAnswerGoesToReviewByDefault() {
        recorded.put(key(file, first), inconclusive());
        assertEquals(new ChainDecision.Review(ChainReason.INCONCLUSIVE, "aaa", "first"), decide(first, second));
    }

    @Test
    void anInconclusiveAnswerRejectsWhenTheCheckSaysSo() {
        ChainCheck strict = check("strict", Scope.REMOTE, TEXT, OnInconclusive.REJECT, OnUnavailable.RETRY);
        recorded.put(key(file, strict), inconclusive());
        assertEquals(new ChainDecision.Reject(ChainReason.INCONCLUSIVE, "aaa", "strict"), decide(strict, second));
    }

    // --- unavailable

    @Test
    void aRetryableFailureIsSubmittedAgainWhileAttemptsRemain() {
        recorded.put(key(file, first), down(MAX_ATTEMPTS - 1, null));
        assertEquals(proceed(new Step.Submit(file, first)), decide(first, second));
    }

    @Test
    void aRetryableFailureOfAPollPollsTheSameTicketAgain() {
        recorded.put(key(file, first), down(1, "t-1"));
        assertEquals(proceed(new Step.Poll(file, first, "t-1")), decide(first, second));
    }

    @Test
    void exhaustedAttemptsGoToReview() {
        recorded.put(key(file, first), down(MAX_ATTEMPTS, null));
        assertEquals(new ChainDecision.Review(ChainReason.ATTEMPTS_EXHAUSTED, "aaa", "first"), decide(first, second));
    }

    @Test
    void aFailureThatCannotBeRetriedGoesToReview() {
        recorded.put(key(file, first), broken());
        assertEquals(new ChainDecision.Review(ChainReason.UNAVAILABLE, "aaa", "first"), decide(first, second));
    }

    @Test
    void anUnavailableCheckRejectsWhenTheCheckSaysSo() {
        ChainCheck strict = check("strict", Scope.REMOTE, TEXT, OnInconclusive.REVIEW, OnUnavailable.REJECT);
        recorded.put(key(file, strict), down(1, null));
        assertEquals(new ChainDecision.Reject(ChainReason.UNAVAILABLE, "aaa", "strict"), decide(strict, second));
        recorded.put(key(file, strict), broken());
        assertEquals(new ChainDecision.Reject(ChainReason.UNAVAILABLE, "aaa", "strict"), decide(strict, second));
    }

    private ChainDecision decide(ChainCheck... checks) {
        return chain.decide(List.of(checks), List.of(file), recorded);
    }

    private static ChainDecision proceed(Step step) {
        return new ChainDecision.Proceed(List.of(step));
    }
}
