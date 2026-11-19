package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.test.modules.formidable.quarantine.chain.OutcomeKey;
import org.jahia.test.modules.formidable.quarantine.chain.RecordedOutcome;
import org.jahia.test.modules.formidable.quarantine.check.Outcome;
import org.jahia.test.modules.formidable.quarantine.store.OutcomeRecord;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns a {@link RecordedOutcome} of the chain into the {@link OutcomeRecord} the store writes, and back. */
final class OutcomeCodec {

    private OutcomeCodec() {
    }

    /**
     * The outcomes as the chain reads them, keyed by the SHA-256 each was given for: an outcome recorded for other
     * bytes never counts for these bytes.
     */
    static Map<OutcomeKey, RecordedOutcome> recorded(List<OutcomeRecord> records) {
        Map<OutcomeKey, RecordedOutcome> recorded = new HashMap<>();
        for (OutcomeRecord record : records) {
            recorded.put(new OutcomeKey(record.sha256(), record.checkName()), decode(record));
        }
        return recorded;
    }

    static RecordedOutcome decode(OutcomeRecord record) {
        return new RecordedOutcome(outcomeOf(record), record.ticket(), record.attempts());
    }

    private static Outcome outcomeOf(OutcomeRecord record) {
        String reason = record.reason() == null ? "" : record.reason();
        switch (record.outcome()) {
            case OutcomeRecord.PASS:
                return Outcome.pass(reason, null);
            case OutcomeRecord.FAIL:
                return Outcome.fail(reason, null);
            case OutcomeRecord.INCONCLUSIVE:
                return Outcome.inconclusive(reason, null);
            case OutcomeRecord.PENDING:
                return Outcome.PENDING;
            default:
                return new Outcome.Failed(record.retryable(), reason);
        }
    }

    static OutcomeRecord encode(String itemName, String checkName, String sha256, RecordedOutcome recorded,
                                Instant at) {
        Outcome outcome = recorded.outcome();
        if (outcome instanceof Outcome.Result result) {
            return new OutcomeRecord(itemName, checkName, sha256, result.verdict().name(), result.reasonCode(), false,
                    recorded.ticket(), recorded.attempts(), at);
        }
        if (outcome instanceof Outcome.Failed failed) {
            return new OutcomeRecord(itemName, checkName, sha256, OutcomeRecord.FAILED, failed.reason(),
                    failed.retryable(), recorded.ticket(), recorded.attempts(), at);
        }
        return new OutcomeRecord(itemName, checkName, sha256, OutcomeRecord.PENDING, null, false, recorded.ticket(),
                recorded.attempts(), at);
    }
}
