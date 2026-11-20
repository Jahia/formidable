package org.jahia.test.modules.formidable.quarantine.store;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

/**
 * The {@link QuarantineStore} of the module, on the live workspace, with a system session: no visitor and no
 * contributor writes or reads the tree. The root of a site cuts its ACL inheritance and holds no access control
 * entry, so a role granted on the site gives nothing on the tree.
 *
 * <p>No node of the tree is a file: an item holds its bytes in a binary property of its own type, never in
 * {@code jcr:data} on a {@code jnt:resource}, so no core listener, no {@code /files} servlet and no text extraction
 * reads it.</p>
 */
@Component(service = QuarantineStore.class, immediate = true)
public class JcrQuarantineStore implements QuarantineStore {

    private static final Logger log = LoggerFactory.getLogger(JcrQuarantineStore.class);

    static final String LIVE_WORKSPACE = "live";
    private static final String HELD_PREFIX = "held-";
    private static final String ACL = "j:acl";
    private static final String ACL_INHERIT = "j:inherit";

    private final Clock clock;

    public JcrQuarantineStore() {
        this(Clock.systemUTC());
    }

    JcrQuarantineStore(Clock clock) {
        this.clock = clock;
    }

    /** Runs the callback in a system session of the live workspace. A unit test overrides it with a mocked session. */
    <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null, callback);
    }

    @Override
    public String hold(HeldSubmission submission) throws QuarantineStoreException {
        if (submission.siteKey().isBlank()) {
            throw new QuarantineStoreException("The submission of form " + submission.formId()
                    + " names no site: the quarantine tree of a site cannot hold it");
        }
        List<IntakeFile> files = new ArrayList<>();
        for (SubmittedFile file : submission.files()) {
            files.add(IntakeFile.of(file));
        }
        Instant at = clock.instant();
        String path;
        try {
            path = inLiveSystemSession(session -> write(session, submission, files, at));
        } catch (RefusedRootException refused) {
            throw new QuarantineStoreException("Refused to hold the submission of form " + submission.formId()
                    + " in site " + submission.siteKey() + ": " + refused.getMessage(), refused);
        } catch (RepositoryException first) {
            // two intakes of one form on one day may create the same folder at once: the second write sees it
            log.debug("First write of a held submission of form {} failed, written again", submission.formId(), first);
            path = writeAgain(submission, files, at);
        }
        log.info("Held a submission of form {} at {}: fields {}, sensitive fields {}, files {}", submission.formId(),
                path, submission.parameters().keySet(), submission.sensitiveParameters().keySet(), files);
        return path;
    }

    private String writeAgain(HeldSubmission submission, List<IntakeFile> files, Instant at)
            throws QuarantineStoreException {
        try {
            return inLiveSystemSession(session -> write(session, submission, files, at));
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("Could not hold the submission of form " + submission.formId()
                    + " in site " + submission.siteKey(), e);
        }
    }

    private static String write(JCRSessionWrapper session, HeldSubmission submission, List<IntakeFile> files,
                                Instant at) throws RepositoryException {
        JCRNodeWrapper root = rootOf(session, submission.siteKey());
        JCRNodeWrapper folder = formFolder(root, submission.formId());
        for (String name : QuarantineTree.dateFolders(at)) {
            folder = child(folder, name, QuarantineTree.DATE_FOLDER_TYPE);
        }
        JCRNodeWrapper held = folder.addNode(HELD_PREFIX + UUID.randomUUID(), QuarantineTree.HELD_TYPE);
        writeHeader(held, submission, at);
        writeValues(held.addNode(QuarantineTree.VALUES, QuarantineTree.VALUES_TYPE), submission.parameters());
        writeValues(held.addNode(QuarantineTree.SENSITIVE, QuarantineTree.SENSITIVE_VALUES_TYPE),
                submission.sensitiveParameters());
        for (int index = 0; index < files.size(); index++) {
            writeItem(session, held.addNode(QuarantineTree.itemName(index), QuarantineTree.ITEM_TYPE), files.get(index));
        }
        session.save();
        return held.getPath();
    }

    /**
     * The root of the tree of a site, created on first use: its ACL inheritance is cut, and it holds no access
     * control entry. A concurrent first use may create it first: the root is then read again. A node that exists
     * at the path of the root is used only when it is a root of the module with the same ACL; any other node is
     * refused with {@link RefusedRootException}, since its readers would read the held values and bytes.
     */
    static JCRNodeWrapper rootOf(JCRSessionWrapper session, String siteKey) throws RepositoryException {
        JCRNodeWrapper site = session.getNode(QuarantineTree.sitePath(siteKey));
        if (site.hasNode(QuarantineTree.ROOT_NAME)) {
            return closed(site.getNode(QuarantineTree.ROOT_NAME));
        }
        try {
            session.checkout(site);
            JCRNodeWrapper root = site.addNode(QuarantineTree.ROOT_NAME, QuarantineTree.ROOT_TYPE);
            root.setAclInheritanceBreak(true);
            session.save();
            return root;
        } catch (RepositoryException e) {
            session.refresh(false);
            if (site.hasNode(QuarantineTree.ROOT_NAME)) {
                return closed(site.getNode(QuarantineTree.ROOT_NAME));
            }
            throw e;
        }
    }

    /** The existing root, when it is of the root type, cuts its ACL inheritance and holds no access control entry. */
    private static JCRNodeWrapper closed(JCRNodeWrapper root) throws RepositoryException {
        if (!root.isNodeType(QuarantineTree.ROOT_TYPE) || !aclIsClosed(root)) {
            throw new RefusedRootException(root.getPath());
        }
        return root;
    }

    /** {@code j:acl} exists, its {@code j:inherit} is false, and it holds no child: no ACE of any kind. */
    private static boolean aclIsClosed(JCRNodeWrapper root) throws RepositoryException {
        if (!root.hasNode(ACL)) {
            return false;
        }
        JCRNodeWrapper acl = root.getNode(ACL);
        if (!acl.hasProperty(ACL_INHERIT) || acl.getProperty(ACL_INHERIT).getBoolean()) {
            return false;
        }
        return !acl.getNodes().iterator().hasNext();
    }

    /** A node at the path of the quarantine root, or of a folder below it, that the store does not use. */
    static final class RefusedRootException extends RepositoryException {

        private static final long serialVersionUID = 1L;

        RefusedRootException(String path) {
            super("The node at " + path + " is not a node of the quarantine tree that keeps its access closed:"
                    + " the store refuses to write under it");
        }
    }

    private static JCRNodeWrapper formFolder(JCRNodeWrapper root, String formId) throws RepositoryException {
        if (root.hasNode(formId)) {
            return reused(root.getNode(formId), QuarantineTree.FORM_TYPE);
        }
        JCRNodeWrapper folder = root.addNode(formId, QuarantineTree.FORM_TYPE);
        folder.setProperty(QuarantineTree.FORM_ID, formId);
        return folder;
    }

    private static JCRNodeWrapper child(JCRNodeWrapper parent, String name, String type) throws RepositoryException {
        return parent.hasNode(name) ? reused(parent.getNode(name), type) : parent.addNode(name, type);
    }

    /**
     * A folder of the tree that exists already. The store never gives a folder an ACL of its own, so a folder of
     * another type or with its own {@code j:acl} was not written by the store, and could open the held values to its
     * readers: it is refused with {@link RefusedRootException}.
     */
    private static JCRNodeWrapper reused(JCRNodeWrapper folder, String type) throws RepositoryException {
        if (!folder.isNodeType(type) || folder.hasNode(ACL)) {
            throw new RefusedRootException(folder.getPath());
        }
        return folder;
    }

    private static void writeHeader(JCRNodeWrapper held, HeldSubmission submission, Instant at)
            throws RepositoryException {
        held.setProperty(QuarantineTree.STATE, HeldStates.HELD);
        held.setProperty(QuarantineTree.CREATED_AT, calendar(at));
        held.setProperty(QuarantineTree.ATTEMPTS, 0L);
        held.setProperty(QuarantineTree.COMPLETED_ACTIONS, 0L);
        held.setProperty(QuarantineTree.FORM_ID, submission.formId());
        held.setProperty(QuarantineTree.SITE_KEY, submission.siteKey());
        held.setProperty(QuarantineTree.LOCALE, submission.locale().toLanguageTag());
        SubmissionOrigin origin = submission.origin();
        setIfPresent(held, QuarantineTree.ORIGIN_LANGUAGE, origin.language());
        setIfPresent(held, QuarantineTree.ORIGIN_REFERER, origin.referer());
        setIfPresent(held, QuarantineTree.ORIGIN_TIME_ZONE, origin.timeZone());
        held.setProperty(QuarantineTree.VALUE_FIELDS, submission.parameters().keySet().toArray(String[]::new));
        held.setProperty(QuarantineTree.SENSITIVE_FIELDS,
                submission.sensitiveParameters().keySet().toArray(String[]::new));
    }

    private static void setIfPresent(JCRNodeWrapper node, String name, String value) throws RepositoryException {
        if (value != null) {
            node.setProperty(name, value);
        }
    }

    private static void writeValues(JCRNodeWrapper node, Map<String, List<String>> values) throws RepositoryException {
        for (Map.Entry<String, List<String>> field : values.entrySet()) {
            node.setProperty(field.getKey(), field.getValue().toArray(String[]::new));
        }
    }

    private static void writeItem(JCRSessionWrapper session, JCRNodeWrapper item, IntakeFile file)
            throws RepositoryException {
        Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(file.bytes()));
        try {
            item.setProperty(QuarantineTree.DATA, binary);
        } finally {
            binary.dispose();
        }
        item.setProperty(QuarantineTree.SHA256, file.sha256());
        item.setProperty(QuarantineTree.SIZE, file.bytes().length);
        setIfPresent(item, QuarantineTree.FILE_NAME, file.fileName());
        setIfPresent(item, QuarantineTree.FIELD_NAME, file.fieldName());
        setIfPresent(item, QuarantineTree.MIME_TYPE, file.mimeType());
    }

    @Override
    public Optional<HeldSubmission> read(String path) throws QuarantineStoreException {
        return inSession("read the held submission at " + path, session -> {
            JCRNodeWrapper held = heldOrNull(session, path);
            return held == null ? Optional.empty() : Optional.of(submissionOf(held));
        });
    }

    @Override
    public Optional<HeldRecord> heldRecord(String path) throws QuarantineStoreException {
        return inSession("read the record of the held submission at " + path, session -> {
            JCRNodeWrapper held = heldOrNull(session, path);
            return held == null ? Optional.empty() : Optional.of(recordOf(held));
        });
    }

    @Override
    public List<HeldRecord> list(String siteKey, String formId) throws QuarantineStoreException {
        return inSession("list the held submissions of form " + formId, session -> {
            String path = QuarantineTree.formPath(siteKey, formId);
            List<HeldRecord> records = new ArrayList<>();
            if (session.nodeExists(path)) {
                collect(session.getNode(path), records);
            }
            records.sort(Comparator.comparing(HeldRecord::createdAt));
            return records;
        });
    }

    private static void collect(JCRNodeWrapper folder, List<HeldRecord> records) throws RepositoryException {
        for (JCRNodeWrapper child : folder.getNodes()) {
            if (child.isNodeType(QuarantineTree.HELD_TYPE)) {
                records.add(recordOf(child));
            } else if (child.isNodeType(QuarantineTree.DATE_FOLDER_TYPE)) {
                collect(child, records);
            }
        }
    }

    @Override
    public void setState(String path, String state) throws QuarantineStoreException {
        if (!HeldStates.isState(state)) {
            throw new QuarantineStoreException("Not a state of a held submission: " + state);
        }
        change(path, "set the state " + state, held -> held.setProperty(QuarantineTree.STATE, state));
    }

    @Override
    public void setAttempts(String path, int attempts, Instant nextAttempt) throws QuarantineStoreException {
        change(path, "set the attempts", held -> {
            held.setProperty(QuarantineTree.ATTEMPTS, attempts);
            if (nextAttempt == null) {
                removeIfPresent(held, QuarantineTree.NEXT_ATTEMPT);
            } else {
                held.setProperty(QuarantineTree.NEXT_ATTEMPT, calendar(nextAttempt));
            }
        });
    }

    @Override
    public void setCompletedActions(String path, int completedActions) throws QuarantineStoreException {
        change(path, "set the completed actions",
                held -> held.setProperty(QuarantineTree.COMPLETED_ACTIONS, completedActions));
    }

    @Override
    public void deleteValues(String path) throws QuarantineStoreException {
        change(path, "delete the values and the bytes", held -> {
            removeChildIfPresent(held, QuarantineTree.VALUES);
            removeChildIfPresent(held, QuarantineTree.SENSITIVE);
            for (JCRNodeWrapper item : items(held)) {
                removeIfPresent(item, QuarantineTree.DATA);
            }
        });
        log.info("Deleted the values and the bytes of the held submission at {}", path);
    }

    /** A change of one held submission, saved at once. */
    @FunctionalInterface
    private interface Change {
        void apply(JCRNodeWrapper held) throws RepositoryException;
    }

    private void change(String path, String what, Change change) throws QuarantineStoreException {
        inSession(what + " of the held submission at " + path, session -> {
            JCRNodeWrapper held = heldOrNull(session, path);
            if (held == null) {
                throw new PathNotFoundException("No held submission at " + path);
            }
            change.apply(held);
            session.save();
            return null;
        });
    }

    private <T> T inSession(String what, JCRCallback<T> callback) throws QuarantineStoreException {
        try {
            return inLiveSystemSession(callback);
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("Could not " + what, e);
        }
    }

    /** The held submission at the path, or null when the path names no node, or a node of another type. */
    private static JCRNodeWrapper heldOrNull(JCRSessionWrapper session, String path) throws RepositoryException {
        if (path == null || !path.startsWith("/sites/") || !session.nodeExists(path)) {
            return null;
        }
        JCRNodeWrapper node = session.getNode(path);
        return node.isNodeType(QuarantineTree.HELD_TYPE) ? node : null;
    }

    static HeldRecord recordOf(JCRNodeWrapper held) throws RepositoryException {
        List<ItemRecord> items = new ArrayList<>();
        for (JCRNodeWrapper item : items(held)) {
            items.add(new ItemRecord(item.getName(), string(item, QuarantineTree.FIELD_NAME),
                    string(item, QuarantineTree.FILE_NAME), string(item, QuarantineTree.MIME_TYPE),
                    number(item, QuarantineTree.SIZE), string(item, QuarantineTree.SHA256),
                    item.hasProperty(QuarantineTree.DATA)));
        }
        return new HeldRecord(held.getPath(), string(held, QuarantineTree.FORM_ID), string(held, QuarantineTree.SITE_KEY),
                string(held, QuarantineTree.STATE), instant(held, QuarantineTree.CREATED_AT),
                (int) number(held, QuarantineTree.ATTEMPTS), instant(held, QuarantineTree.NEXT_ATTEMPT),
                (int) number(held, QuarantineTree.COMPLETED_ACTIONS), strings(held, QuarantineTree.VALUE_FIELDS),
                strings(held, QuarantineTree.SENSITIVE_FIELDS), held.hasNode(QuarantineTree.VALUES), items);
    }

    private static HeldSubmission submissionOf(JCRNodeWrapper held) throws RepositoryException {
        String locale = string(held, QuarantineTree.LOCALE);
        SubmissionOrigin origin = new SubmissionOrigin(string(held, QuarantineTree.ORIGIN_LANGUAGE),
                string(held, QuarantineTree.ORIGIN_REFERER), string(held, QuarantineTree.ORIGIN_TIME_ZONE));
        return new HeldSubmission(string(held, QuarantineTree.FORM_ID), string(held, QuarantineTree.SITE_KEY),
                locale == null ? Locale.ROOT : Locale.forLanguageTag(locale),
                valuesOf(held, QuarantineTree.VALUES, strings(held, QuarantineTree.VALUE_FIELDS)),
                valuesOf(held, QuarantineTree.SENSITIVE, strings(held, QuarantineTree.SENSITIVE_FIELDS)),
                filesOf(held), origin);
    }

    /** The values of one of the two value nodes, in the order of the field names the held submission keeps. */
    private static Map<String, List<String>> valuesOf(JCRNodeWrapper held, String child, List<String> fields)
            throws RepositoryException {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (!held.hasNode(child)) {
            return values;
        }
        JCRNodeWrapper node = held.getNode(child);
        for (String field : fields) {
            if (node.hasProperty(field)) {
                values.put(field, strings(node, field));
            }
        }
        return values;
    }

    private static List<SubmittedFile> filesOf(JCRNodeWrapper held) throws RepositoryException {
        List<SubmittedFile> files = new ArrayList<>();
        for (JCRNodeWrapper item : items(held)) {
            if (item.hasProperty(QuarantineTree.DATA)) {
                files.add(new SubmittedFile(string(item, QuarantineTree.FIELD_NAME), string(item, QuarantineTree.FILE_NAME),
                        string(item, QuarantineTree.MIME_TYPE), bytesOf(item)));
            }
        }
        return files;
    }

    private static byte[] bytesOf(JCRNodeWrapper item) throws RepositoryException {
        Binary binary = item.getProperty(QuarantineTree.DATA).getBinary();
        try (InputStream in = binary.getStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RepositoryException("Could not read the bytes of the item " + item.getPath(), e);
        } finally {
            binary.dispose();
        }
    }

    /** The items of a held submission, in the order of the files of the submission. */
    static List<JCRNodeWrapper> items(JCRNodeWrapper held) throws RepositoryException {
        List<JCRNodeWrapper> items = new ArrayList<>();
        for (JCRNodeWrapper child : held.getNodes()) {
            if (child.isNodeType(QuarantineTree.ITEM_TYPE)) {
                items.add(child);
            }
        }
        items.sort(Comparator.comparingInt(JcrQuarantineStore::itemIndex));
        return items;
    }

    private static int itemIndex(JCRNodeWrapper item) {
        String name = item.getName();
        try {
            return Integer.parseInt(name.substring(QuarantineTree.ITEM_PREFIX.length()));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static void removeIfPresent(JCRNodeWrapper node, String property) throws RepositoryException {
        if (node.hasProperty(property)) {
            node.getProperty(property).remove();
        }
    }

    private static void removeChildIfPresent(JCRNodeWrapper node, String child) throws RepositoryException {
        if (node.hasNode(child)) {
            node.getNode(child).remove();
        }
    }

    private static String string(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.hasProperty(property) ? node.getProperty(property).getString() : null;
    }

    private static long number(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.hasProperty(property) ? node.getProperty(property).getLong() : 0L;
    }

    private static Instant instant(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.hasProperty(property) ? node.getProperty(property).getDate().toInstant() : null;
    }

    private static List<String> strings(JCRNodeWrapper node, String property) throws RepositoryException {
        if (!node.hasProperty(property)) {
            return List.of();
        }
        List<String> strings = new ArrayList<>();
        for (Value value : node.getProperty(property).getValues()) {
            strings.add(value.getString());
        }
        return strings;
    }

    private static Calendar calendar(Instant at) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(at.toEpochMilli());
        return calendar;
    }
}
