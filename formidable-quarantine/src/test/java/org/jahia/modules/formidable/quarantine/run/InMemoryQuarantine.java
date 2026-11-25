package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.HeldStates;
import org.jahia.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.formidable.quarantine.store.ScopedStreams;
import org.jahia.modules.formidable.quarantine.store.Sha256;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
public final class InMemoryQuarantine implements QuarantineStore, CheckStore {

    public static final String SITE = "site";
    public static final String SECRET = "sensitive-value-of-the-test";

    /** One held submission. */
    public static final class Held {
        public final String path;
        public final String formId;
        public final Instant createdAt;
        public String state = HeldStates.HELD;
        public Instant changedAt;
        public int attempts;
        public Instant nextAttempt;
        public int completedActions;
        public boolean valuesPresent = true;
        final List<String> itemNames = new ArrayList<>();
        final List<String> mimeTypes = new ArrayList<>();
        final List<String> fileNames = new ArrayList<>();
        final List<String> sha256s = new ArrayList<>();
        public final List<byte[]> bytes = new ArrayList<>();
        public final Map<String, OutcomeRecord> outcomes = new LinkedHashMap<>();
        public DecisionRecord decision;
        final List<Integer> checkpoints = new ArrayList<>();
        public final List<String> states = new ArrayList<>();
        public final List<AuditRecord> audit = new ArrayList<>();

        Held(String path, String formId, Instant createdAt) {
            this.path = path;
            this.formId = formId;
            this.createdAt = createdAt;
        }

        public boolean anyBytes() {
            return bytes.stream().anyMatch(b -> b != null);
        }
    }

    public final Map<String, Held> held = new LinkedHashMap<>();
    public final Set<String> broken = new HashSet<>();
    /** The paths whose values and bytes the store fails to delete, as a repository that refuses the save. */
    public final Set<String> failingDeletion = new HashSet<>();
    /** The paths whose audit records the store fails to write. */
    public final Set<String> failingAudit = new HashSet<>();
    private int count;

    /** Holds a submission of the form with one file per text, each a text file. */
    public Held hold(String formId, String... texts) {
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
        addItem(one, "item" + one.itemNames.size() + ".txt", mimeType, data);
    }

    /** Adds an item of that name and declared type. */
    void addItem(Held one, String fileName, String mimeType, byte[] data) {
        one.fileNames.add(fileName);
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
            if (one.bytes.get(i) != null && !lostOnRead.contains(one.path + "/" + one.itemNames.get(i))) {
                files.add(new SubmittedFile("document", one.fileNames.get(i), one.mimeTypes.get(i),
                        bytesOf(one, i)));
            }
        }
        Map<String, List<String>> values = one.valuesPresent ? Map.of("fullName", List.of("Ada")) : Map.of();
        Map<String, List<String>> sensitive = one.valuesPresent ? Map.of("password", List.of(SECRET)) : Map.of();
        return Optional.of(new HeldSubmission(one.formId, SITE, Locale.ENGLISH, values, sensitive, files,
                new SubmissionOrigin(null, null, null)));
    }

    /**
     * The items, by path and item name, whose bytes the record still says present and that {@link #read} leaves out:
     * a binary that went between the two reads of the release.
     */
    public final Set<String> lostOnRead = new HashSet<>();

    /** The bytes the release reads into memory for the engine: the generated bytes of a large item. */
    private byte[] bytesOf(Held one, int index) {
        LargeBytes generated = large.get(one.path + "/" + one.itemNames.get(index));
        return generated != null ? generated.toArray() : one.bytes.get(index);
    }

    @Override
    public Optional<HeldRecord> heldRecord(String path) throws QuarantineStoreException {
        if (!held.containsKey(path) && !broken.contains(path)) {
            // as the JCR store: a path that names no held submission answers nothing
            return Optional.empty();
        }
        Held one = get(path);
        List<ItemRecord> items = new ArrayList<>();
        for (int i = 0; i < one.itemNames.size(); i++) {
            items.add(new ItemRecord(one.itemNames.get(i), "document", one.fileNames.get(i),
                    one.mimeTypes.get(i), 10, one.sha256s.get(i), one.bytes.get(i) != null));
        }
        return Optional.of(new HeldRecord(one.path, one.formId, SITE, one.state, one.createdAt, one.attempts,
                one.nextAttempt, one.completedActions, List.of("fullName"), List.of("password"), one.valuesPresent,
                items, one.changedAt != null ? one.changedAt : one.createdAt));
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
            return heldRecord(one.path).orElseThrow();
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
        one.changedAt = changedAt;
    }

    /** The time setState writes as the last change. */
    public Instant changedAt = Instant.parse("2026-11-19T12:00:00Z");

    @Override
    public void purge(String path) throws QuarantineStoreException {
        get(path);
        held.remove(path);
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
        one.changedAt = changedAt;
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
    public void keepPassedOutcomesOnly(String path) throws QuarantineStoreException {
        get(path).outcomes.values().removeIf(outcome -> !OutcomeRecord.PASS.equals(outcome.outcome()));
    }

    @Override
    public List<AuditRecord> audit(String path) throws QuarantineStoreException {
        return List.copyOf(get(path).audit);
    }

    @Override
    public void writeAudit(String path, AuditRecord audit) throws QuarantineStoreException {
        if (failingAudit.contains(path)) {
            throw new QuarantineStoreException("Could not write the audit record at " + path);
        }
        get(path).audit.add(audit);
    }

    /** How many times a work ran in {@link #withItems}, and whether one runs now. */
    public int itemSessions;
    public boolean itemSessionOpen;

    @Override
    public <T> T withItems(String path, ItemWork<T> work) throws QuarantineStoreException {
        Held one = get(path);
        itemSessions++;
        itemSessionOpen = true;
        try (ScopedStreams items = new ScopedStreams(itemName -> streamOf(one, itemName))) {
            return work.run(items);
        } finally {
            itemSessionOpen = false;
        }
    }

    /** The stream of an item; a stream over large bytes, when the test gave them, that refuses readAllBytes. */
    private InputStream streamOf(Held one, String itemName) throws QuarantineStoreException {
        int index = one.itemNames.indexOf(itemName);
        if (index < 0 || one.bytes.get(index) == null) {
            throw new QuarantineStoreException("No bytes for " + itemName);
        }
        LargeBytes bytes = large.get(one.path + "/" + itemName);
        return bytes != null ? bytes.open() : new ByteArrayInputStream(one.bytes.get(index));
    }

    /** The items whose bytes are a large generated stream, by path and item name. */
    public final Map<String, LargeBytes> large = new LinkedHashMap<>();

    /**
     * An item of the given size whose stream generates its bytes as it is read and fails on
     * {@link InputStream#readAllBytes()}: a check that reads the stream whole fails. The release reads the item into
     * memory through {@link #read}, as the engine needs, and that read is not a stream.
     */
    public static final class LargeBytes {
        private final long size;
        private final byte fill;

        public LargeBytes(long size, byte fill) {
            this.size = size;
            this.fill = fill;
        }

        /** The SHA-256 of the bytes, computed as a stream. */
        public String sha256() {
            try (InputStream in = open()) {
                return Sha256.hex(in);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        /** The bytes in memory, as the release reads them for the engine. */
        byte[] toArray() {
            byte[] bytes = new byte[Math.toIntExact(size)];
            Arrays.fill(bytes, fill);
            return bytes;
        }

        InputStream open() {
            return new InputStream() {
                private long left = size;

                @Override
                public int read() {
                    if (left == 0) {
                        return -1;
                    }
                    left--;
                    return fill & 0xff;
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    if (left == 0) {
                        return -1;
                    }
                    int count = (int) Math.min(len, left);
                    Arrays.fill(b, off, off + count, fill);
                    left -= count;
                    return count;
                }

                @Override
                public byte[] readAllBytes() {
                    throw new AssertionError("The whole item was read into memory");
                }

                @Override
                public byte[] readNBytes(int len) {
                    throw new AssertionError("The whole item was read into memory");
                }

                @Override
                public long transferTo(OutputStream out) {
                    throw new AssertionError("The whole item was copied");
                }
            };
        }
    }

    /** Holds a submission of the form with one large item of the given size, whose stream refuses readAllBytes. */
    public Held holdLarge(String formId, LargeBytes bytes) {
        Held one = hold(formId);
        one.itemNames.add("item0");
        one.fileNames.add("item0.bin");
        one.mimeTypes.add("application/octet-stream");
        one.sha256s.add(bytes.sha256());
        // a marker: the bytes are present; the stream of the large item and read() serve them
        one.bytes.add(new byte[0]);
        large.put(one.path + "/item0", bytes);
        return one;
    }
}
