package org.jahia.test.modules.formidable.quarantine.store;

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
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

/**
 * The {@link CheckStore} of the module, on the live workspace, with a system session, under the {@code checks}
 * container of a held submission:
 * <pre>
 * checks                 fmdbquar:records
 *   ├─ item0             fmdbquar:itemOutcomes
 *   │    └─ remoteCheck  fmdbquar:outcome    outcome, reason, retryable, at, ticket, attempts, sha256
 *   └─ decision          fmdbquar:decision   reason, itemName, checkName, sha256, failedAction, at
 * </pre>
 */
@Component(service = CheckStore.class, immediate = true)
public class JcrCheckStore implements CheckStore {

    static final String ITEM_OUTCOMES_TYPE = "fmdbquar:itemOutcomes";
    static final String OUTCOME_TYPE = "fmdbquar:outcome";
    static final String DECISION_TYPE = "fmdbquar:decision";

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
        return inSession("read the outcomes of the held submission at " + path, session -> {
            JCRNodeWrapper checks = checksOrNull(held(session, path));
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
        });
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
            node.setProperty(ATTEMPTS, (long) outcome.attempts());
            node.setProperty(AT, calendar(outcome.at()));
        });
    }

    @Override
    public Optional<DecisionRecord> decision(String path) throws QuarantineStoreException {
        return inSession("read the decision of the held submission at " + path, session -> {
            JCRNodeWrapper checks = checksOrNull(held(session, path));
            if (checks == null || !checks.hasNode(DECISION)) {
                return Optional.empty();
            }
            JCRNodeWrapper node = checks.getNode(DECISION);
            Integer failedAction = node.hasProperty(FAILED_ACTION) ? (int) node.getProperty(FAILED_ACTION).getLong() : null;
            return Optional.of(new DecisionRecord(string(node, REASON), string(node, ITEM_NAME), string(node, CHECK_NAME),
                    string(node, QuarantineTree.SHA256), failedAction, instant(node, AT)));
        });
    }

    @Override
    public void writeDecision(String path, DecisionRecord decision) throws QuarantineStoreException {
        change(path, "write the decision " + decision.reason(), held -> {
            JCRNodeWrapper checks = checks(held);
            if (checks.hasNode(DECISION)) {
                checks.getNode(DECISION).remove();
            }
            JCRNodeWrapper node = checks.addNode(DECISION, DECISION_TYPE);
            node.setProperty(REASON, decision.reason());
            setOrRemove(node, ITEM_NAME, decision.itemName());
            setOrRemove(node, CHECK_NAME, decision.checkName());
            setOrRemove(node, QuarantineTree.SHA256, decision.sha256());
            if (decision.failedAction() != null) {
                node.setProperty(FAILED_ACTION, (long) decision.failedAction());
            }
            node.setProperty(AT, calendar(decision.at()));
        });
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
