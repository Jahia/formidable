package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Binary;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

/**
 * The {@link CheckStore} of the module, on the live workspace, with a system session, under the {@code checks}
 * container of a held submission:
 * <pre>
 * checks                 fmdbq:records
 *   ├─ item0             fmdbq:itemOutcomes
 *   │    └─ remoteCheck  fmdbq:outcome    outcome, reason, retryable, at, ticket, attempts, sha256
 *   └─ decision          fmdbq:decision   reason, itemName, checkName, sha256, failedAction, at
 * audit                  fmdbq:records
 *   └─ record1, ...      fmdbq:auditRecord  operation, userName, fromState, toState, at
 * </pre>
 */
@Component(service = CheckStore.class, immediate = true)
public class JcrCheckStore implements CheckStore {

    static final String ITEM_OUTCOMES_TYPE = "fmdbq:itemOutcomes";
    static final String OUTCOME_TYPE = "fmdbq:outcome";
    static final String DECISION_TYPE = "fmdbq:decision";

    // the properties of an outcome
    static final String OUTCOME = "outcome";
    static final String REASON = "reason";
    static final String RETRYABLE = "retryable";
    static final String AT = "at";
    static final String TICKET = "ticket";
    static final String ATTEMPTS = "attempts";
    // the properties of a decision; reason, sha256 and at are shared with the outcome
    static final String ITEM_NAME = "itemName";
    static final String CHECK_NAME = "checkName";
    static final String FAILED_ACTION = "failedAction";

    static final String AUDIT_TYPE = "fmdbq:auditRecord";
    static final String AUDIT_PREFIX = "record";
    // the properties of an audit record; at is shared with the outcome
    static final String OPERATION = "operation";
    static final String USER_NAME = "userName";
    static final String FROM_STATE = "fromState";
    static final String TO_STATE = "toState";

    /** Runs the callback in a system session of the live workspace. A unit test overrides it with a mocked session. */
    <T> T inLiveSystemSession(JCRCallback<T> callback) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, JcrQuarantineStore.LIVE_WORKSPACE, null,
                callback);
    }

    @Override
    public List<String> forms(String siteKey) throws QuarantineStoreException {
        return inSession("list the forms of the quarantine tree of site " + siteKey, session -> {
            String root = QuarantineTree.rootPath(siteKey);
            List<String> forms = new ArrayList<>();
            if (!session.nodeExists(root)) {
                return forms;
            }
            for (JCRNodeWrapper folder : session.getNode(root).getNodes()) {
                if (folder.isNodeType(QuarantineTree.FORM_TYPE)) {
                    forms.add(folder.getName());
                }
            }
            return forms;
        });
    }

    @Override
    public List<OutcomeRecord> outcomes(String path) throws QuarantineStoreException {
        return inSession("read the outcomes of the held submission at " + path,
                session -> outcomesOf(held(session, path)));
    }

    private static List<OutcomeRecord> outcomesOf(JCRNodeWrapper held) throws RepositoryException {
        JCRNodeWrapper checks = checksOrNull(held);
        List<OutcomeRecord> outcomes = new ArrayList<>();
        if (checks == null) {
            return outcomes;
        }
        for (JCRNodeWrapper item : checks.getNodes()) {
            if (item.isNodeType(ITEM_OUTCOMES_TYPE)) {
                collect(item, outcomes);
            }
        }
        return outcomes;
    }

    private static void collect(JCRNodeWrapper item, List<OutcomeRecord> outcomes) throws RepositoryException {
        for (JCRNodeWrapper check : item.getNodes()) {
            if (check.isNodeType(OUTCOME_TYPE)) {
                outcomes.add(new OutcomeRecord(item.getName(), check.getName(),
                        string(check, QuarantineTree.SHA256), string(check, OUTCOME), string(check, REASON),
                        check.hasProperty(RETRYABLE) && check.getProperty(RETRYABLE).getBoolean(),
                        string(check, TICKET), (int) number(check, ATTEMPTS), instant(check, AT)));
            }
        }
    }

    @Override
    public void writeOutcome(String path, OutcomeRecord outcome) throws QuarantineStoreException {
        change(path, "write the outcome of check " + outcome.checkName() + " for item " + outcome.itemName(), held -> {
            JCRNodeWrapper checks = checks(held);
            JCRNodeWrapper item = checks.hasNode(outcome.itemName()) ? checks.getNode(outcome.itemName())
                    : checks.addNode(outcome.itemName(), ITEM_OUTCOMES_TYPE);
            JCRNodeWrapper node = item.hasNode(outcome.checkName()) ? item.getNode(outcome.checkName())
                    : item.addNode(outcome.checkName(), OUTCOME_TYPE);
            node.setProperty(OUTCOME, outcome.outcome());
            node.setProperty(QuarantineTree.SHA256, outcome.sha256());
            setOrRemove(node, REASON, outcome.reason());
            node.setProperty(RETRYABLE, outcome.retryable());
            setOrRemove(node, TICKET, outcome.ticket());
            node.setProperty(ATTEMPTS, outcome.attempts());
            node.setProperty(AT, calendar(outcome.at()));
        });
    }

    @Override
    public Optional<DecisionRecord> decision(String path) throws QuarantineStoreException {
        return inSession("read the decision of the held submission at " + path,
                session -> Optional.ofNullable(decisionOf(held(session, path))));
    }

    /** The decision under the held submission, or {@code null}. */
    private static DecisionRecord decisionOf(JCRNodeWrapper held) throws RepositoryException {
        JCRNodeWrapper checks = checksOrNull(held);
        if (checks == null || !checks.hasNode(DECISION_NODE)) {
            return null;
        }
        JCRNodeWrapper node = checks.getNode(DECISION_NODE);
        Integer failedAction = node.hasProperty(FAILED_ACTION) ? (int) node.getProperty(FAILED_ACTION).getLong() : null;
        return new DecisionRecord(string(node, REASON), string(node, ITEM_NAME), string(node, CHECK_NAME),
                string(node, QuarantineTree.SHA256), failedAction, instant(node, AT));
    }

    @Override
    public void writeDecision(String path, DecisionRecord decision) throws QuarantineStoreException {
        change(path, "write the decision " + decision.reason(), held -> {
            JCRNodeWrapper checks = checks(held);
            if (checks.hasNode(DECISION_NODE)) {
                checks.getNode(DECISION_NODE).remove();
            }
            JCRNodeWrapper node = checks.addNode(DECISION_NODE, DECISION_TYPE);
            node.setProperty(REASON, decision.reason());
            setOrRemove(node, ITEM_NAME, decision.itemName());
            setOrRemove(node, CHECK_NAME, decision.checkName());
            setOrRemove(node, QuarantineTree.SHA256, decision.sha256());
            if (decision.failedAction() != null) {
                node.setProperty(FAILED_ACTION, decision.failedAction());
            }
            node.setProperty(AT, calendar(decision.at()));
        });
    }

    @Override
    public void keepPassedOutcomesOnly(String path) throws QuarantineStoreException {
        change(path, "drop the outcomes that are not a PASS", held -> {
            JCRNodeWrapper checks = checksOrNull(held);
            if (checks == null) {
                return;
            }
            List<JCRNodeWrapper> dropped = new ArrayList<>();
            for (JCRNodeWrapper item : checks.getNodes()) {
                if (item.isNodeType(ITEM_OUTCOMES_TYPE)) {
                    collectUnpassed(item, dropped);
                }
            }
            // removed once the walk ends: removing while iterating is undefined
            for (JCRNodeWrapper outcome : dropped) {
                outcome.remove();
            }
        });
    }

    private static void collectUnpassed(JCRNodeWrapper item, List<JCRNodeWrapper> dropped) throws RepositoryException {
        for (JCRNodeWrapper check : item.getNodes()) {
            if (check.isNodeType(OUTCOME_TYPE) && !OutcomeRecord.PASS.equals(string(check, OUTCOME))) {
                dropped.add(check);
            }
        }
    }

    @Override
    public List<AuditRecord> audit(String path) throws QuarantineStoreException {
        return inSession("read the audit of the held submission at " + path, session -> auditOf(held(session, path)));
    }

    private static List<AuditRecord> auditOf(JCRNodeWrapper held) throws RepositoryException {
        List<AuditRecord> records = new ArrayList<>();
        if (!held.hasNode(QuarantineTree.AUDIT)) {
            return records;
        }
        for (JCRNodeWrapper node : held.getNode(QuarantineTree.AUDIT).getNodes()) {
            if (node.isNodeType(AUDIT_TYPE)) {
                records.add(new AuditRecord(string(node, OPERATION), string(node, USER_NAME),
                        string(node, FROM_STATE), string(node, TO_STATE), instant(node, AT)));
            }
        }
        return records;
    }

    @Override
    public Map<String, HeldDetails> details(Collection<String> paths, Set<String> withValues)
            throws QuarantineStoreException {
        return inSession("read the records of " + paths.size() + " held submissions", session -> {
            Map<String, HeldDetails> details = new LinkedHashMap<>();
            for (String path : paths) {
                JCRNodeWrapper held = JcrQuarantineStore.heldOrNull(session, path);
                if (held != null) {
                    details.put(path, new HeldDetails(outcomesOf(held), decisionOf(held), auditOf(held),
                            withValues.contains(path) ? JcrQuarantineStore.publicValuesOf(held) : Map.of()));
                }
            }
            return details;
        });
    }

    @Override
    public void writeAudit(String path, AuditRecord audit) throws QuarantineStoreException {
        change(path, "write the audit record of " + audit.operation(), held -> {
            JCRNodeWrapper container = held.hasNode(QuarantineTree.AUDIT) ? held.getNode(QuarantineTree.AUDIT)
                    : held.addNode(QuarantineTree.AUDIT, QuarantineTree.RECORDS_TYPE);
            JCRNodeWrapper node = container.addNode(nextAuditName(container), AUDIT_TYPE);
            node.setProperty(OPERATION, audit.operation());
            setOrRemove(node, USER_NAME, audit.userName());
            node.setProperty(FROM_STATE, audit.fromState());
            node.setProperty(TO_STATE, audit.toState());
            node.setProperty(AT, calendar(audit.at()));
        });
    }

    /** {@code record1}, {@code record2}, and so on: the first name that no record holds. */
    static String nextAuditName(JCRNodeWrapper container) throws RepositoryException {
        int next = 1;
        while (container.hasNode(AUDIT_PREFIX + next)) {
            next++;
        }
        return AUDIT_PREFIX + next;
    }

    @Override
    public InputStream openItem(String path, String itemName) throws QuarantineStoreException {
        byte[] bytes = inSession("read the bytes of item " + itemName + " of the held submission at " + path, session -> {
            JCRNodeWrapper held = held(session, path);
            if (!held.hasNode(itemName) || !held.getNode(itemName).hasProperty(QuarantineTree.DATA)) {
                throw new PathNotFoundException("No bytes for item " + itemName + " of " + path);
            }
            return bytesOf(held.getNode(itemName));
        });
        return new ByteArrayInputStream(bytes);
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

    /** A change of one held submission, saved at once. */
    @FunctionalInterface
    private interface Change {
        void apply(JCRNodeWrapper held) throws RepositoryException;
    }

    private void change(String path, String what, Change change) throws QuarantineStoreException {
        inSession(what + " of the held submission at " + path, session -> {
            change.apply(held(session, path));
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

    /** The held submission at the path; a path that names no held submission is refused. */
    private static JCRNodeWrapper held(JCRSessionWrapper session, String path) throws RepositoryException {
        if (path == null || !path.startsWith("/sites/") || !session.nodeExists(path)) {
            throw new PathNotFoundException("No held submission at " + path);
        }
        JCRNodeWrapper node = session.getNode(path);
        if (!node.isNodeType(QuarantineTree.HELD_TYPE)) {
            throw new PathNotFoundException("No held submission at " + path);
        }
        return node;
    }

    private static JCRNodeWrapper checksOrNull(JCRNodeWrapper held) throws RepositoryException {
        return held.hasNode(QuarantineTree.CHECKS) ? held.getNode(QuarantineTree.CHECKS) : null;
    }

    private static JCRNodeWrapper checks(JCRNodeWrapper held) throws RepositoryException {
        JCRNodeWrapper checks = checksOrNull(held);
        return checks != null ? checks : held.addNode(QuarantineTree.CHECKS, QuarantineTree.RECORDS_TYPE);
    }

    /** A null value removes the property: an outcome written again keeps no ticket or reason of the one before. */
    private static void setOrRemove(JCRNodeWrapper node, String property, String value) throws RepositoryException {
        if (value != null) {
            node.setProperty(property, value);
        } else if (node.hasProperty(property)) {
            node.getProperty(property).remove();
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

    private static Calendar calendar(Instant at) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(at.toEpochMilli());
        return calendar;
    }
}
