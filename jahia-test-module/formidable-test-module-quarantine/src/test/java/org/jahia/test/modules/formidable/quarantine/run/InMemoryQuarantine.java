package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.test.modules.formidable.quarantine.store.CheckStore;
import org.jahia.test.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldStates;
import org.jahia.test.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.test.modules.formidable.quarantine.store.OutcomeRecord;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.test.modules.formidable.quarantine.store.Sha256;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The two stores of the module in memory, for the tests of the run: the state, the attempts, the checkpoints, the
 * values, the bytes, the outcomes and the decision of each held submission. A path listed in {@link #broken} refuses
 * every call, as a repository that cannot read the node.
 */
final class InMemoryQuarantine implements QuarantineStore, CheckStore {

    static final String SITE = "site";
    static final String SECRET = "sensitive-value-of-the-test";

    /** One held submission. */
    static final class Held {
        final String path;
        final String formId;
        final Instant createdAt;
        String state = HeldStates.HELD;
        int attempts;
        Instant nextAttempt;
        int completedActions;
        boolean valuesPresent = true;
        final List<String> itemNames = new ArrayList<>();
        final List<String> mimeTypes = new ArrayList<>();
        final List<String> sha256s = new ArrayList<>();
        final List<byte[]> bytes = new ArrayList<>();
        final Map<String, OutcomeRecord> outcomes = new LinkedHashMap<>();
        DecisionRecord decision;
        final List<Integer> checkpoints = new ArrayList<>();
        final List<String> states = new ArrayList<>();

        Held(String path, String formId, Instant createdAt) {
            this.path = path;
            this.formId = formId;
            this.createdAt = createdAt;
        }

        boolean anyBytes() {
            return bytes.stream().anyMatch(b -> b != null);
        }
    }

    final Map<String, Held> held = new LinkedHashMap<>();
    final Set<String> broken = new HashSet<>();
    /** The paths whose values and bytes the store fails to delete, as a repository that refuses the save. */
    final Set<String> failingDeletion = new HashSet<>();
    private int count;

    /** Holds a submission of the form with one file per text, each a text file. */
    Held hold(String formId, String... texts) {
        Held one = new Held("/sites/" + SITE + "/formidable-quarantine/" + formId + "/2026/11/19/held-" + count,
                formId, Instant.parse("2026-11-19T10:00:00Z").plusSeconds(count));
        count++;
        for (String text : texts) {
            addItem(one, "text/plain", text.getBytes(StandardCharsets.UTF_8));
        }
        held.put(one.path, one);
        return one;
    }

    void addItem(Held one, String mimeType, byte[] data) {
        one.itemNames.add("item" + one.itemNames.size());
        one.mimeTypes.add(mimeType);
        one.sha256s.add(Sha256.hex(data));
        one.bytes.add(data);
    }

    private Held get(String path) throws QuarantineStoreException {
        if (broken.contains(path)) {
            throw new QuarantineStoreException("Could not read the held submission at " + path);
        }
        Held one = held.get(path);
        if (one == null) {
            throw new QuarantineStoreException("No held submission at " + path);
        }
        return one;
    }

    @Override
    public String hold(HeldSubmission submission) {
        throw new UnsupportedOperationException("The run never holds");
    }

    @Override
    public Optional<HeldSubmission> read(String path) throws QuarantineStoreException {
        Held one = get(path);
        List<SubmittedFile> files = new ArrayList<>();
        for (int i = 0; i < one.bytes.size(); i++) {
            if (one.bytes.get(i) != null) {
                files.add(new SubmittedFile("document", one.itemNames.get(i) + ".txt", one.mimeTypes.get(i),
                        one.bytes.get(i)));
            }
        }
        Map<String, List<String>> values = one.valuesPresent ? Map.of("fullName", List.of("Ada")) : Map.of();
        Map<String, List<String>> sensitive = one.valuesPresent ? Map.of("password", List.of(SECRET)) : Map.of();
        return Optional.of(new HeldSubmission(one.formId, SITE, Locale.ENGLISH, values, sensitive, files,
                new SubmissionOrigin(null, null, null)));
    }

    @Override
    public Optional<HeldRecord> record(String path) throws QuarantineStoreException {
        Held one = get(path);
        List<ItemRecord> items = new ArrayList<>();
        for (int i = 0; i < one.itemNames.size(); i++) {
            items.add(new ItemRecord(one.itemNames.get(i), "document", one.itemNames.get(i) + ".txt",
                    one.mimeTypes.get(i), 10, one.sha256s.get(i), one.bytes.get(i) != null));
        }
        return Optional.of(new HeldRecord(one.path, one.formId, SITE, one.state, one.createdAt, one.attempts,
                one.nextAttempt, one.completedActions, List.of("fullName"), List.of("password"), one.valuesPresent,
                items));
    }

    @Override
    public List<HeldRecord> list(String siteKey, String formId) throws QuarantineStoreException {
        List<HeldRecord> records = new ArrayList<>();
        for (Held one : held.values()) {
            if (one.formId.equals(formId)) {
                records.add(recordEvenIfBroken(one));
            }
        }
        return records;
    }

    /** A broken path is still listed: the run fails on it, not the listing. */
    private HeldRecord recordEvenIfBroken(Held one) throws QuarantineStoreException {
        boolean wasBroken = broken.remove(one.path);
        try {
            return record(one.path).orElseThrow();
        } finally {
            if (wasBroken) {
                broken.add(one.path);
            }
        }
    }

    @Override
    public void setState(String path, String state) throws QuarantineStoreException {
        Held one = get(path);
        one.state = state;
        one.states.add(state);
    }

    @Override
    public void setAttempts(String path, int attempts, Instant nextAttempt) throws QuarantineStoreException {
        Held one = get(path);
        one.attempts = attempts;
        one.nextAttempt = nextAttempt;
    }

    @Override
    public void setCompletedActions(String path, int completedActions) throws QuarantineStoreException {
        Held one = get(path);
        one.completedActions = completedActions;
        one.checkpoints.add(completedActions);
    }

    @Override
    public void finish(String path, String state) throws QuarantineStoreException {
        Held one = get(path);
        if (failingDeletion.contains(path)) {
            throw new QuarantineStoreException("Could not delete the values of the held submission at " + path);
        }
        one.state = state;
        one.states.add(state);
        one.valuesPresent = false;
        for (int i = 0; i < one.bytes.size(); i++) {
            one.bytes.set(i, null);
        }
    }

    @Override
    public List<String> forms(String siteKey) {
        List<String> forms = new ArrayList<>();
        for (Held one : held.values()) {
            if (!forms.contains(one.formId)) {
                forms.add(one.formId);
            }
        }
        return forms;
    }

    @Override
    public List<OutcomeRecord> outcomes(String path) throws QuarantineStoreException {
        return List.copyOf(get(path).outcomes.values());
    }

    @Override
    public void writeOutcome(String path, OutcomeRecord outcome) throws QuarantineStoreException {
        get(path).outcomes.put(outcome.itemName() + "/" + outcome.checkName(), outcome);
    }

    @Override
    public Optional<DecisionRecord> decision(String path) throws QuarantineStoreException {
        return Optional.ofNullable(get(path).decision);
    }

    @Override
    public void writeDecision(String path, DecisionRecord decision) throws QuarantineStoreException {
        get(path).decision = decision;
    }

    @Override
    public InputStream openItem(String path, String itemName) throws QuarantineStoreException {
        Held one = get(path);
        byte[] data = one.bytes.get(one.itemNames.indexOf(itemName));
        if (data == null) {
            throw new QuarantineStoreException("No bytes for " + itemName);
        }
        return new ByteArrayInputStream(data);
    }
}
