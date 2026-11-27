package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.chain.ChainCheck;
import org.jahia.modules.formidable.quarantine.chain.ChainDecision;
import org.jahia.modules.formidable.quarantine.chain.ChainReason;
import org.jahia.modules.formidable.quarantine.chain.CheckChain;
import org.jahia.modules.formidable.quarantine.chain.OutcomeKey;
import org.jahia.modules.formidable.quarantine.chain.RecordedOutcome;
import org.jahia.modules.formidable.quarantine.chain.Step;
import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;
import org.jahia.modules.formidable.quarantine.state.HeldEvent;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
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
 * the back-off, in state {@code checking}, until the attempts are exhausted. A terminal decision moves the state
 * through the state machine.
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
        final HeldRecord held;
        final List<ItemFile> files = new ArrayList<>();
        final Set<OutcomeKey> called = new HashSet<>();
        final ItemAccess access = new ItemAccess();
        Map<OutcomeKey, RecordedOutcome> recorded;
        List<ChainCheck> checks = List.of();
        HeldState state;

        Pass(HeldRecord held) {
            this.held = held;
        }

        String path() {
            return held.path();
        }
    }

    /**
     * @param held a held submission in state {@code held} or {@code checking}
     * @return the state after the pass
     */
    HeldState run(HeldRecord held) throws QuarantineStoreException, RepositoryException {
        Pass pass = new Pass(held);
        pass.state = HeldState.fromValue(held.state());
        if (pass.state == HeldState.HELD) {
            pass.state = transitions.move(pass.path(), pass.state, HeldEvent.START);
        }
        List<ChainCheck> checks;
        try {
            checks = checksOf(held.formId());
        } catch (ItemNotFoundException gone) {
            return formGone(pass);
        }
        for (ItemRecord item : held.items()) {
            if (item.binaryPresent()) {
                pass.files.add(new ItemFile(pass.path(), item, pass.access));
            }
        }
        pass.checks = checks;
        pass.recorded = OutcomeCodec.recorded(checkStore.outcomes(pass.path()), pass.files, checks);
        CheckChain chain = new CheckChain(settings.allowInProcessFirst(), settings.backoff().maxAttempts());
        ChainDecision decision = chain.decide(checks, pass.files, pass.recorded);
        boolean progressedOnce = false;
        while (decision instanceof ChainDecision.Proceed proceed) {
            boolean progressed = execute(pass, proceed.steps());
            progressedOnce |= progressed;
            decision = chain.decide(checks, pass.files, pass.recorded);
            if (!progressed && decision instanceof ChainDecision.Proceed waiting) {
                return waitForBackoff(pass, progressedOnce, waiting.steps().get(0));
            }
        }
        return conclude(pass, decision);
    }

    /**
     * The form was deleted since the intake: its checks cannot be read, and no run would ever read them. The submission
     * goes to review with its data, rather than being tried again at every run.
     */
    private HeldState formGone(Pass pass) throws QuarantineStoreException {
        String path = pass.path();
        String formId = pass.held.formId();
        log.info("Held submission {}: its form {} is gone, the checks stopped on {}", path, formId,
                DecisionRecord.FORM_GONE);
        checkStore.writeDecision(path, new DecisionRecord(DecisionRecord.FORM_GONE, null, null, null, null,
                clock.instant()));
        return transitions.move(path, pass.state, HeldEvent.REVIEW);
    }

    private List<ChainCheck> checksOf(String formId) throws RepositoryException {
        List<ChainCheck> checks = new ArrayList<>();
        for (CheckSettings check : checkList.of(formId)) {
            checks.add(new ChainCheck(check, qualifiers.apply(check.nodeType())));
        }
        return checks;
    }

    /**
     * Calls the steps. When a step hands a file to a check that reads bytes, the calls run inside one session of the
     * store, and the bytes of the files are open to the checks until the last call returns.
     *
     * @return true when a step brought a new {@code PASS}
     */
    private boolean execute(Pass pass, List<Step> steps) throws QuarantineStoreException {
        if (steps.stream().noneMatch(SubmissionRun::readsBytes)) {
            return callSteps(pass, steps);
        }
        return checkStore.withItems(pass.path(), items -> {
            pass.access.begin(items);
            try {
                return callSteps(pass, steps);
            } finally {
                pass.access.end();
            }
        });
    }

    /** A poll reads no byte, and neither does a metadata check. */
    static boolean readsBytes(Step step) {
        return step instanceof Step.Submit && step.check().qualifier().scope() != Scope.METADATA;
    }

    private boolean callSteps(Pass pass, List<Step> steps) throws QuarantineStoreException {
        boolean progressed = false;
        for (Step step : steps) {
            OutcomeKey key = OutcomeKey.of(step.file(), step.check());
            if (!pass.called.add(key)) {
                continue;
            }
            RecordedOutcome next = call(pass.path(), step, pass.recorded.get(key));
            pass.recorded.put(key, next);
            write(pass, step.check(), key, next);
            progressed |= passed(next);
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
            Submitted submitted = qualifier.submit(fileFor(qualifier, step.file()), step.check().settings());
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

    /** A metadata check gets the metadata of the file, and no way to read its bytes. */
    static QuarantinedFile fileFor(Qualifier qualifier, QuarantinedFile file) {
        return qualifier.scope() == Scope.METADATA ? new MetadataOnlyFile(file) : file;
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

    /**
     * Writes the outcome under every item of the same key: every item whose bytes have the SHA-256 of the key, and for a
     * metadata check, the same name and type too.
     */
    private void write(Pass pass, ChainCheck check, OutcomeKey key, RecordedOutcome outcome)
            throws QuarantineStoreException {
        Instant at = clock.instant();
        for (ItemFile file : pass.files) {
            if (OutcomeKey.of(file, check).equals(key)) {
                checkStore.writeOutcome(pass.path(),
                        OutcomeCodec.encode(file.itemName(), key.checkName(), key.sha256(), outcome, at));
            }
        }
    }

    /**
     * Counts one more pass with no new {@code PASS}, from 0 when this pass brought one. Once the count reaches the
     * attempts of the back-off, the run gives up on the check it waits for, as it does on failures: a check that stays
     * pending never keeps the values for ever.
     */
    private HeldState waitForBackoff(Pass pass, boolean progressed, Step waiting) throws QuarantineStoreException {
        int attempts = (progressed ? 0 : pass.held.attempts()) + 1;
        if (settings.backoff().exhausted(attempts)) {
            store.setAttempts(pass.path(), attempts, null);
            writeDecision(pass, ChainReason.ATTEMPTS_EXHAUSTED.name(), waiting.file().sha256(),
                    waiting.check().name());
            return transitions.move(pass.path(), pass.state, HeldEvent.REVIEW);
        }
        store.setAttempts(pass.path(), attempts, settings.backoff().nextAttempt(attempts, clock.instant()));
        return transitions.move(pass.path(), pass.state, HeldEvent.CONTINUE);
    }

    private HeldState conclude(Pass pass, ChainDecision decision) throws QuarantineStoreException {
        store.setAttempts(pass.path(), pass.held.attempts(), null);
        if (decision instanceof ChainDecision.Reject reject) {
            writeDecision(pass, reject.reason().name(), reject.sha256(), reject.checkName());
            return transitions.finish(pass.path(), pass.state, HeldEvent.CHECK_FAILED);
        }
        if (decision instanceof ChainDecision.Review review) {
            writeDecision(pass, review.reason().name(), review.sha256(), review.checkName());
            return transitions.move(pass.path(), pass.state, HeldEvent.REVIEW);
        }
        return release.release(pass.path(), pass.state);
    }

    /**
     * The item a decision is about: among the items of those bytes, the first whose outcome of the check is not a
     * {@code PASS}, since a metadata check judges each item apart; else the first item of those bytes.
     */
    private static String itemNameOf(Pass pass, String sha256, String checkName) {
        ChainCheck check = pass.checks.stream().filter(c -> c.name().equals(checkName)).findFirst().orElse(null);
        String first = null;
        for (ItemFile file : pass.files) {
            if (!file.sha256().equals(sha256)) {
                continue;
            }
            first = first == null ? file.itemName() : first;
            if (check != null && !passed(pass.recorded.get(OutcomeKey.of(file, check)))) {
                return file.itemName();
            }
        }
        return first;
    }

    private static boolean passed(RecordedOutcome outcome) {
        return outcome != null && outcome.outcome() instanceof Outcome.Result result
                && result.verdict() == Outcome.Verdict.PASS;
    }

    private void writeDecision(Pass pass, String reason, String sha256, String checkName)
            throws QuarantineStoreException {
        String itemName = itemNameOf(pass, sha256, checkName);
        String path = pass.path();
        log.info("Held submission {}: the checks stopped on {} (item {}, check {})", path, reason, itemName,
                checkName);
        checkStore.writeDecision(path, new DecisionRecord(reason, itemName, checkName, sha256, null,
                clock.instant()));
    }
}
