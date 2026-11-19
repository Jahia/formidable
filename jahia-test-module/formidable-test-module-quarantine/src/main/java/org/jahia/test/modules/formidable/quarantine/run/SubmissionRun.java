package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.test.modules.formidable.quarantine.chain.ChainCheck;
import org.jahia.test.modules.formidable.quarantine.chain.ChainDecision;
import org.jahia.test.modules.formidable.quarantine.chain.CheckChain;
import org.jahia.test.modules.formidable.quarantine.chain.OutcomeKey;
import org.jahia.test.modules.formidable.quarantine.chain.RecordedOutcome;
import org.jahia.test.modules.formidable.quarantine.chain.Step;
import org.jahia.test.modules.formidable.quarantine.check.CheckSettings;
import org.jahia.test.modules.formidable.quarantine.check.Outcome;
import org.jahia.test.modules.formidable.quarantine.check.Qualifier;
import org.jahia.test.modules.formidable.quarantine.check.Submitted;
import org.jahia.test.modules.formidable.quarantine.state.HeldEvent;
import org.jahia.test.modules.formidable.quarantine.state.HeldState;
import org.jahia.test.modules.formidable.quarantine.store.CheckStore;
import org.jahia.test.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One pass of the run over one held submission: the chain decides, the run calls the checks of each step, records
 * each answer under the SHA-256 of intake of the file, and asks the chain again. A pass calls each check at most once
 * for each file: when a pass of steps brings no new {@code PASS} and no terminal decision, the submission waits for
 * the back-off, in state {@code checking}. A terminal decision moves the state through the state machine.
 */
final class SubmissionRun {

    private static final Logger log = LoggerFactory.getLogger(SubmissionRun.class);

    /** The reason of a failure when a check threw instead of answering. */
    static final String CHECK_ERROR = "check.error";

    private final QuarantineStore store;
    private final CheckStore checkStore;
    private final CheckList checkList;
    private final Function<String, Qualifier> qualifiers;
    private final Transitions transitions;
    private final Release release;
    private final RunSettings settings;
    private final Clock clock;

    SubmissionRun(QuarantineStore store, CheckStore checkStore, CheckList checkList,
                  Function<String, Qualifier> qualifiers, Release release, RunSettings settings, Clock clock) {
        this.store = store;
        this.checkStore = checkStore;
        this.checkList = checkList;
        this.qualifiers = qualifiers;
        this.transitions = new Transitions(store);
        this.release = release;
        this.settings = settings;
        this.clock = clock;
    }

    /** What one pass knows of the submission. */
    private static final class Pass {
        final HeldRecord record;
        final List<ItemFile> files = new ArrayList<>();
        final Set<OutcomeKey> called = new HashSet<>();
        Map<OutcomeKey, RecordedOutcome> recorded;
        HeldState state;

        Pass(HeldRecord record) {
            this.record = record;
        }

        String path() {
            return record.path();
        }
    }

    /**
     * @param record a held submission in state {@code held} or {@code checking}
     * @return the state after the pass
     */
    HeldState run(HeldRecord record) throws QuarantineStoreException, RepositoryException {
        Pass pass = new Pass(record);
        pass.state = HeldState.fromValue(record.state());
        if (pass.state == HeldState.HELD) {
            pass.state = transitions.move(pass.path(), pass.state, HeldEvent.START);
        }
        List<ChainCheck> checks = checksOf(record.formId());
        for (ItemRecord item : record.items()) {
            if (item.binaryPresent()) {
                pass.files.add(new ItemFile(pass.path(), item, checkStore));
            }
        }
        pass.recorded = OutcomeCodec.recorded(checkStore.outcomes(pass.path()));
        CheckChain chain = new CheckChain(settings.allowInProcessFirst(), settings.backoff().maxAttempts());
        ChainDecision decision = chain.decide(checks, pass.files, pass.recorded);
        while (decision instanceof ChainDecision.Proceed proceed) {
            boolean progressed = execute(pass, proceed.steps());
            decision = chain.decide(checks, pass.files, pass.recorded);
            if (!progressed && decision instanceof ChainDecision.Proceed) {
                return waitForBackoff(pass);
            }
        }
        return conclude(pass, decision);
    }

    private List<ChainCheck> checksOf(String formId) throws RepositoryException {
        List<ChainCheck> checks = new ArrayList<>();
        for (CheckSettings check : checkList.of(formId)) {
            checks.add(new ChainCheck(check, qualifiers.apply(check.nodeType())));
        }
        return checks;
    }

    /** @return true when a step brought a new {@code PASS} */
    private boolean execute(Pass pass, List<Step> steps) throws QuarantineStoreException {
        boolean progressed = false;
        for (Step step : steps) {
            OutcomeKey key = new OutcomeKey(step.file().sha256(), step.check().name());
            if (!pass.called.add(key)) {
                continue;
            }
            RecordedOutcome next = call(pass.path(), step, pass.recorded.get(key));
            pass.recorded.put(key, next);
            write(pass, key, next);
            progressed |= next.outcome() instanceof Outcome.Result result && result.verdict() == Outcome.Verdict.PASS;
        }
        return progressed;
    }

    private RecordedOutcome call(String path, Step step, RecordedOutcome previous) {
        int attempts = previous == null ? 0 : previous.attempts();
        String ticket = previous == null ? null : previous.ticket();
        Qualifier qualifier = step.check().qualifier();
        try {
            if (step instanceof Step.Poll poll) {
                return recordOf(qualifier.poll(poll.ticket()), poll.ticket(), attempts);
            }
            Submitted submitted = qualifier.submit(step.file(), step.check().settings());
            if (submitted instanceof Submitted.Ticket answeredLater) {
                return new RecordedOutcome(Outcome.PENDING, answeredLater.id(), attempts);
            }
            return recordOf(((Submitted.Answered) submitted).outcome(), ticket, attempts);
        } catch (RuntimeException e) {
            log.warn("Held submission {}: check {} threw {} instead of answering", path, step.check().name(),
                    e.getClass().getName());
            return recordOf(new Outcome.Failed(true, CHECK_ERROR), ticket, attempts);
        }
    }

    /** A result starts the failures over; a failure counts one more; a pending answer keeps its ticket. */
    static RecordedOutcome recordOf(Outcome outcome, String ticket, int attempts) {
        if (outcome instanceof Outcome.Result) {
            return new RecordedOutcome(outcome, null, 0);
        }
        if (outcome instanceof Outcome.Failed) {
            return new RecordedOutcome(outcome, ticket, attempts + 1);
        }
        if (ticket == null) {
            // a poll always carries a ticket; a pending answer with none could never be polled
            return new RecordedOutcome(new Outcome.Failed(false, CHECK_ERROR), null, attempts + 1);
        }
        return new RecordedOutcome(outcome, ticket, attempts);
    }

    /** Writes the outcome under every item whose bytes have the SHA-256 of the key. */
    private void write(Pass pass, OutcomeKey key, RecordedOutcome outcome) throws QuarantineStoreException {
        Instant at = clock.instant();
        for (ItemFile file : pass.files) {
            if (file.sha256().equals(key.sha256())) {
                checkStore.writeOutcome(pass.path(),
                        OutcomeCodec.encode(file.itemName(), key.checkName(), key.sha256(), outcome, at));
            }
        }
    }

    private HeldState waitForBackoff(Pass pass) throws QuarantineStoreException {
        int attempts = pass.record.attempts() + 1;
        store.setAttempts(pass.path(), attempts, settings.backoff().nextAttempt(attempts, clock.instant()));
        return transitions.move(pass.path(), pass.state, HeldEvent.CONTINUE);
    }

    private HeldState conclude(Pass pass, ChainDecision decision) throws QuarantineStoreException {
        store.setAttempts(pass.path(), pass.record.attempts(), null);
        if (decision instanceof ChainDecision.Reject reject) {
            writeDecision(pass, reject.reason().name(), reject.sha256(), reject.checkName());
            HeldState rejected = transitions.move(pass.path(), pass.state, HeldEvent.CHECK_FAILED);
            store.deleteValues(pass.path());
            return rejected;
        }
        if (decision instanceof ChainDecision.Review review) {
            writeDecision(pass, review.reason().name(), review.sha256(), review.checkName());
            return transitions.move(pass.path(), pass.state, HeldEvent.REVIEW);
        }
        return release.release(pass.path(), pass.state);
    }

    private void writeDecision(Pass pass, String reason, String sha256, String checkName)
            throws QuarantineStoreException {
        String itemName = null;
        for (ItemFile file : pass.files) {
            if (itemName == null && file.sha256().equals(sha256)) {
                itemName = file.itemName();
            }
        }
        log.info("Held submission {}: the checks stopped on {} (item {}, check {})", pass.path(), reason, itemName,
                checkName);
        checkStore.writeDecision(pass.path(), new DecisionRecord(reason, itemName, checkName, sha256, null,
                clock.instant()));
    }
}
