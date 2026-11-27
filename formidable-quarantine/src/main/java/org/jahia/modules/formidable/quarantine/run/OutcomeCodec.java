package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.chain.ChainCheck;
import org.jahia.modules.formidable.quarantine.chain.OutcomeKey;
import org.jahia.modules.formidable.quarantine.chain.RecordedOutcome;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns a {@link RecordedOutcome} of the chain into the {@link OutcomeRecord} the store writes, and back. */
final class OutcomeCodec {

    private OutcomeCodec() {
    }

    /**
     * The outcomes as the chain reads them, keyed as {@link OutcomeKey#of} keys them: by the SHA-256 each was given for,
     * and for a metadata check by the name and the type of the item it was recorded under too. An outcome recorded for
     * other bytes, or by a metadata check for another name or type, never counts for this file. An outcome of a
     * metadata check whose item has no bytes any more counts for no file.
     *
     * @param files  the files of the submission, by item
     * @param checks the checks of the list, whose scope decides the key
     */
    static Map<OutcomeKey, RecordedOutcome> recorded(List<OutcomeRecord> records, List<ItemFile> files,
                                                     List<ChainCheck> checks) {
        Map<OutcomeKey, RecordedOutcome> recorded = new HashMap<>();
        for (OutcomeRecord stored : records) {
            OutcomeKey key = keyOf(stored, files, checks);
            if (key != null) {
                recorded.put(key, decode(stored));
            }
        }
        return recorded;
    }

    /** @return the key of the stored outcome; null for an outcome of a metadata check whose item has no file */
    private static OutcomeKey keyOf(OutcomeRecord stored, List<ItemFile> files, List<ChainCheck> checks) {
        ChainCheck check = checks.stream().filter(c -> c.name().equals(stored.checkName())).findFirst().orElse(null);
        if (check == null) {
            // a check gone from the list: the chain asks for no outcome of it
            return new OutcomeKey(stored.sha256(), stored.checkName());
        }
        ItemFile file = files.stream().filter(f -> f.itemName().equals(stored.itemName())).findFirst().orElse(null);
        if (file == null) {
            return check.qualifier() != null && check.qualifier().scope() == Scope.METADATA ? null
                    : new OutcomeKey(stored.sha256(), stored.checkName());
        }
        return OutcomeKey.of(stored.sha256(), file, check);
    }

    static RecordedOutcome decode(OutcomeRecord stored) {
        return new RecordedOutcome(outcomeOf(stored), stored.ticket(), stored.attempts());
    }

    private static Outcome outcomeOf(OutcomeRecord stored) {
        String reason = stored.reason() == null ? "" : stored.reason();
        switch (stored.outcome()) {
            case OutcomeRecord.PASS:
                return Outcome.pass(reason, null);
            case OutcomeRecord.FAIL:
                return Outcome.fail(reason, null);
            case OutcomeRecord.INCONCLUSIVE:
                return Outcome.inconclusive(reason, null);
            case OutcomeRecord.PENDING:
                return Outcome.PENDING;
            default:
                return new Outcome.Failed(stored.retryable(), reason);
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
