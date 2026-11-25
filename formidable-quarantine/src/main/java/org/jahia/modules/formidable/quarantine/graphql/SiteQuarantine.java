package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.formidable.quarantine.ops.Managers;
import org.jahia.modules.formidable.quarantine.ops.Operation;
import org.jahia.modules.formidable.quarantine.ops.OperationRefusedException;
import org.jahia.modules.formidable.quarantine.ops.OperationResult;
import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The quarantine of one site, for a user that the resolver found to be a manager of it: every read and every operation
 * of a GraphQL request goes through one instance, which never reads outside the quarantine root of its site. The
 * instance lives for one request: it keeps what it read of each form and of each held submission.
 *
 * <p>A failure of the store answers a GraphQL error with a fixed message; the log line names the path, never a
 * value.</p>
 */
final class SiteQuarantine {

    private static final Logger log = LoggerFactory.getLogger(SiteQuarantine.class);

    static final int DEFAULT_LIMIT = 25;
    static final int MAX_LIMIT = 100;
    static final String READ_FAILED = "The quarantine tree could not be read";
    static final String OPERATION_FAILED = "The operation failed";
    static final String NOT_FOUND = "No held submission %s that the user manages";
    static final String ADMINISTRATORS_ONLY = "Server administrators only";

    /** Runs an operation of a manager, as {@code QuarantineOperations.apply}. */
    @FunctionalInterface
    interface Operator {
        OperationResult apply(String path, Operation operation)
                throws OperationRefusedException, QuarantineStoreException;
    }

    /** Purges the old rejected submissions of a site, as {@code QuarantineRetention.purge}. */
    @FunctionalInterface
    interface Purger {
        QuarantineRetention.Report purge(String siteKey, Integer olderThanDays) throws QuarantineStoreException;
    }

    @FunctionalInterface
    private interface StoreCall<T> {
        T call() throws QuarantineStoreException;
    }

    private final String siteKey;
    private final QuarantineStore store;
    private final CheckStore checkStore;
    private final Operator operator;
    private final Purger purger;
    private final Managers managers;
    private final FormReader forms;
    private final Map<String, FormFacts> formFacts = new ConcurrentHashMap<>();
    private final Map<String, Optional<DecisionRecord>> decisions = new ConcurrentHashMap<>();
    private final Map<String, List<OutcomeRecord>> outcomes = new ConcurrentHashMap<>();

    SiteQuarantine(String siteKey, QuarantineStore store, CheckStore checkStore, Operator operator, Purger purger,
                   Managers managers, FormReader forms) {
        this.siteKey = siteKey;
        this.store = store;
        this.checkStore = checkStore;
        this.operator = operator;
        this.purger = purger;
        this.managers = managers;
        this.forms = forms;
    }

    String siteKey() {
        return siteKey;
    }

    /** The number of held submissions of the site in each state, every state listed. */
    List<GqlStateCount> counts() {
        Map<GqlState, Integer> counts = new EnumMap<>(GqlState.class);
        for (GqlState state : GqlState.values()) {
            counts.put(state, 0);
        }
        for (HeldRecord held : read(() -> records(null))) {
            counts.merge(GqlState.of(held.state()), 1, Integer::sum);
        }
        List<GqlStateCount> answer = new ArrayList<>();
        counts.forEach((state, count) -> answer.add(new GqlStateCount(state, count)));
        return answer;
    }

    /**
     * A page of the held submissions of the site, or of one of its forms, oldest first.
     *
     * @param formId the form, or {@code null} for every form; a value that is not a form identifier matches nothing
     * @param states the states to keep, or {@code null} or empty for every state
     * @param offset the index of the first item; a negative or missing value is 0
     * @param limit  the size of the page, {@value #DEFAULT_LIMIT} when missing, at most {@value #MAX_LIMIT}
     */
    GqlSubmissionPage page(String formId, List<GqlState> states, Integer offset, Integer limit) {
        int from = offset == null ? 0 : Math.max(0, offset);
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(0, limit), MAX_LIMIT);
        if (formId != null && !HeldIds.isFormId(formId)) {
            return new GqlSubmissionPage(0, from, size, List.of());
        }
        List<HeldRecord> matching = read(() -> records(formId)).stream()
                .filter(held -> states == null || states.isEmpty() || states.contains(GqlState.of(held.state())))
                .toList();
        List<GqlHeldSubmission> items = matching.stream().skip(from).limit(size)
                .map(held -> new GqlHeldSubmission(this, held)).toList();
        return new GqlSubmissionPage(matching.size(), from, size, items);
    }

    /** The held submission of the site with this identifier, or {@code null} when the site has none. */
    GqlHeldSubmission submission(String id) {
        String path = HeldIds.pathOf(siteKey, id);
        if (path == null) {
            return null;
        }
        return read(() -> store.heldRecord(path)).filter(held -> siteKey.equals(held.siteKey()))
                .map(held -> new GqlHeldSubmission(this, held)).orElse(null);
    }

    /**
     * Runs the operation as the user of the request, then answers the held submission as the operation left it. The
     * operations check the permission on the site again, and write the audit record with the name of the user.
     *
     * @throws DataFetchingException when the site has no such held submission, when the state does not allow the
     *                               operation, or when the operation failed
     */
    GqlHeldSubmission operate(String id, GqlOperation operation) {
        String path = HeldIds.pathOf(siteKey, id);
        if (path == null || operation == null) {
            throw new DataFetchingException(String.format(NOT_FOUND, id));
        }
        try {
            operator.apply(path, operation.operation());
        } catch (OperationRefusedException e) {
            throw new DataFetchingException(e.reason() == OperationRefusedException.Reason.CONFLICT
                    ? e.getMessage() : String.format(NOT_FOUND, id));
        } catch (QuarantineStoreException e) {
            log.warn("The operation {} on {} failed: {}", operation, path, e.getMessage());
            throw new DataFetchingException(OPERATION_FAILED);
        } catch (RuntimeException e) {
            // the class alone: the message of an action that a release or a resume ran may quote a held value
            log.warn("The operation {} on {} failed with {}", operation, path, e.getClass().getName());
            throw new DataFetchingException(OPERATION_FAILED);
        }
        decisions.remove(path);
        outcomes.remove(path);
        GqlHeldSubmission after = submission(id);
        if (after == null) {
            throw new DataFetchingException(String.format(NOT_FOUND, id));
        }
        return after;
    }

    /**
     * Purges the rejected held submissions of the site older than the limit. Server administrators only: the
     * retention is a setting of the platform.
     *
     * @param olderThanDays the limit for this call, or {@code null} for the configured one; never negative
     */
    GqlPurgeReport purge(Integer olderThanDays) {
        if (!managers.isServerAdministrator()) {
            throw new DataFetchingException(ADMINISTRATORS_ONLY);
        }
        if (olderThanDays != null && olderThanDays < 0) {
            throw new DataFetchingException("olderThanDays is a number of days");
        }
        QuarantineRetention.Report report = read(() -> purger.purge(siteKey, olderThanDays));
        return new GqlPurgeReport(report.retentionDays(), ids(report.purged()), ids(report.failed()));
    }

    FormFacts form(String formId) {
        FormFacts known = formFacts.get(formId);
        if (known != null) {
            return known;
        }
        FormFacts facts;
        try {
            facts = HeldIds.isFormId(formId) ? forms.read(siteKey, formId) : FormFacts.NONE;
        } catch (RepositoryException e) {
            log.warn("Could not read form {} of site {}: {}", formId, siteKey, e.getMessage());
            throw new DataFetchingException(READ_FAILED);
        }
        formFacts.put(formId, facts);
        return facts;
    }

    DecisionRecord decision(String path) {
        if (!decisions.containsKey(path)) {
            decisions.put(path, read(() -> checkStore.decision(path)));
        }
        return decisions.get(path).orElse(null);
    }

    List<OutcomeRecord> outcomes(String path) {
        List<OutcomeRecord> known = outcomes.get(path);
        if (known == null) {
            known = read(() -> checkStore.outcomes(path));
            outcomes.put(path, known);
        }
        return known;
    }

    List<AuditRecord> audit(String path) {
        return read(() -> checkStore.audit(path));
    }

    Map<String, List<String>> publicValues(String path) {
        return read(() -> store.publicValues(path));
    }

    /** The held submissions of the site, or of one form, oldest first. */
    private List<HeldRecord> records(String formId) throws QuarantineStoreException {
        if (formId != null) {
            return store.list(siteKey, formId);
        }
        List<HeldRecord> all = new ArrayList<>();
        for (String form : checkStore.forms(siteKey)) {
            all.addAll(store.list(siteKey, form));
        }
        all.sort(Comparator.comparing(HeldRecord::createdAt));
        return all;
    }

    private List<String> ids(List<String> paths) {
        return paths.stream().map(path -> HeldIds.idOf(siteKey, path)).toList();
    }

    private <T> T read(StoreCall<T> call) {
        try {
            return call.call();
        } catch (QuarantineStoreException e) {
            log.warn("Could not read the quarantine tree of site {}: {}", siteKey, e.getMessage());
            throw new DataFetchingException(READ_FAILED);
        }
    }
}
