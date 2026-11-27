package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldDetails;
import org.jahia.modules.formidable.quarantine.store.HeldQueries;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public final class InMemoryQuarantine implements QuarantineStore, CheckStore, HeldQueries {

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
        /** The site the record names: the site of its path, unless a test makes them differ. */
        public String siteKey = SITE;
        /** The public values the visitor sent. */
        public Map<String, List<String>> values = Map.of("fullName", List.of("Ada"));
        /** True once the bytes were dropped so that the values go out alone. */
        public boolean valuesOnly;
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
    /** The paths whose release of the values alone the store fails to save, as a repository that refuses the save. */
    public final Set<String> failingValuesRelease = new HashSet<>();
    /** The paths whose audit records the store fails to write. */
    public final Set<String> failingAudit = new HashSet<>();
    private int count;

    /** Holds a submission of the form with one file per text, each a text file. */
    public Held hold(String formId, String... texts) {
        // the name of a held node as the JCR store writes it: held-<uuid>
        Held one = new Held(rootOf(SITE) + formId + "/2026/11/19/held-"
                + String.format("00000000-0000-4000-8000-%012d", count),
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
        Map<String, List<String>> values = one.valuesPresent ? one.values : Map.of();
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
        return Optional.of(new HeldRecord(one.path, one.formId, one.siteKey, one.state, one.createdAt, one.attempts,
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

    private Map<String, List<String>> publicValues(String path) {
        Held one = held.get(path);
        return one != null && one.valuesPresent ? Map.of("fullName", List.of("Ada")) : Map.of();
    }

    @Override
    public void startValuesRelease(String path, String expectedState, String state) throws QuarantineStoreException {
        Held one = get(path);
        if (failingValuesRelease.contains(path)) {
            throw new QuarantineStoreException("Could not start the release of the values of " + path);
        }
        if (!expectedState.equals(one.state)) {
            throw new QuarantineStoreException("The held submission at " + path + " is " + one.state);
        }
        for (int i = 0; i < one.bytes.size(); i++) {
            one.bytes.set(i, null);
        }
        one.valuesOnly = true;
        one.state = state;
        one.states.add(state);
        one.changedAt = changedAt;
    }

    @Override
    public boolean valuesOnly(String path) throws QuarantineStoreException {
        return get(path).valuesOnly;
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

    @Override
    public Map<String, HeldDetails> details(Collection<String> paths, Set<String> withValues)
            throws QuarantineStoreException {
        detailReads++;
        Map<String, HeldDetails> details = new LinkedHashMap<>();
        for (String path : paths) {
            if (held.containsKey(path) || broken.contains(path)) {
                Held one = get(path);
                details.put(path, new HeldDetails(List.copyOf(one.outcomes.values()), one.decision,
                        List.copyOf(one.audit), withValues.contains(path) ? publicValues(path) : Map.of()));
            }
        }
        return details;
    }

    /** The number of calls of {@link #details}: one per page, not one per submission. */
    public int detailReads;

    /**
     * The raw result of the query of the held submissions: below the root of the site, of the form, in the states, by
     * time of intake in the order that {@code newestFirst} asks for. The filter on the site that each record names is
     * the one of {@code JcrHeldQueries}, which its own test covers: this double does not repeat it.
     */
    @Override
    public List<HeldRecord> page(String siteKey, String formId, Set<String> states, int offset, int limit,
                                 boolean newestFirst) {
        List<HeldRecord> matching = new ArrayList<>(matching(siteKey, formId, states));
        if (newestFirst) {
            Collections.reverse(matching);
        }
        return matching.stream().skip(offset).limit(limit).toList();
    }

    @Override
    public long count(String siteKey, String formId, Set<String> states) {
        return matching(siteKey, formId, states).size();
    }

    /** The folders of the forms that a test made: the forms of its held submissions, and {@link #emptyForms}. */
    @Override
    public List<String> formIds(String siteKey) {
        Set<String> formIds = new LinkedHashSet<>(emptyForms);
        for (Held one : held.values()) {
            if (one.path.startsWith(rootOf(siteKey))) {
                formIds.add(one.formId);
            }
        }
        return List.copyOf(formIds);
    }

    /** The folders of forms that hold no submission, such as a form whose submissions the retention purged. */
    public final Set<String> emptyForms = new LinkedHashSet<>();

    /** The quarantine root of a site, with its trailing slash, as the JCR store writes it. */
    private static String rootOf(String siteKey) {
        return "/sites/" + siteKey + "/formidable-quarantine/";
    }

    private List<HeldRecord> matching(String siteKey, String formId, Set<String> states) {
        List<HeldRecord> matching = new ArrayList<>();
        for (Held one : held.values()) {
            boolean inSite = one.path.startsWith(rootOf(siteKey));
            boolean ofForm = formId == null || formId.equals(one.formId);
            if (inSite && ofForm && (states.isEmpty() || states.contains(one.state))) {
                matching.add(recordOf(one));
            }
        }
        matching.sort(Comparator.comparing(HeldRecord::createdAt));
        return matching;
    }

    private HeldRecord recordOf(Held one) {
        try {
            return recordEvenIfBroken(one);
        } catch (QuarantineStoreException e) {
            throw new IllegalStateException(e);
        }
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
