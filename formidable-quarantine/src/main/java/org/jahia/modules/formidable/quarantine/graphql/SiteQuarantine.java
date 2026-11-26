package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.formidable.quarantine.ops.Managers;
import org.jahia.modules.formidable.quarantine.ops.Operation;
import org.jahia.modules.formidable.quarantine.ops.OperationRefusedException;
import org.jahia.modules.formidable.quarantine.ops.OperationResult;
import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.HeldDetails;
import org.jahia.modules.formidable.quarantine.store.HeldQueries;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The quarantine of one site, for a user that the resolver found to be a manager of it: every read and every operation
 * of a GraphQL request goes through one instance, which never reads outside the quarantine root of its site. The
 * instance lives for one request: it keeps what it read of each form, and reads the records of the held submissions
 * it answered in one session, on the first field that needs them. An operation reads the records and the form of its
 * submission before it answers, so that a failure of that read says that the operation ran.
 *
 * <p>Any failure answers a GraphQL error with a fixed message; the log line names the site and the class of the
 * failure, or the path, never a value.</p>
 */
final class SiteQuarantine {

    static final int DEFAULT_LIMIT = 25;
    static final int MAX_LIMIT = 100;
    static final String READ_FAILED = "The quarantine tree could not be read";
    static final String OPERATION_FAILED = "The operation failed";
    static final String RAN_NOT_READ = "The operation ran, but the held submission could not be read again";
    static final String NOT_FOUND = "No held submission %s that the user manages";
    static final String ADMINISTRATORS_ONLY = "Server administrators only";
    static final String TOO_MANY_STATES = "states names each state once at most";

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

    /** The services of the module that a request reads and changes the quarantine through. */
    record Services(QuarantineStore store, CheckStore checkStore, HeldQueries queries, Operator operator,
                    Purger purger, Managers managers, FormReader forms) {
    }

    @FunctionalInterface
    private interface StoreCall<T> {
        T call() throws QuarantineStoreException;
    }

    private static final HeldDetails NO_DETAILS = new HeldDetails(List.of(), null, List.of(), Map.of());

    private final String siteKey;
    private final Services services;
    private final Logger log;
    private final Map<String, FormFacts> formFacts = new HashMap<>();
    /** The form of each held submission this request answered, by path. */
    private final Map<String, String> answered = new LinkedHashMap<>();
    private final Map<String, HeldDetails> details = new HashMap<>();

    SiteQuarantine(String siteKey, Services services) {
        this(siteKey, services, LoggerFactory.getLogger(SiteQuarantine.class));
    }

    SiteQuarantine(String siteKey, Services services, Logger log) {
        this.siteKey = siteKey;
        this.services = services;
        this.log = log;
    }

    String siteKey() {
        return siteKey;
    }

    /** The number of held submissions of the site in each state, every state listed: one query per state. */
    List<GqlStateCount> counts() {
        return guarded("count", () -> {
            List<GqlStateCount> counts = new ArrayList<>();
            for (GqlState state : GqlState.values()) {
                long count = read(() -> services.queries().count(siteKey, stored(state)));
                counts.add(new GqlStateCount(state, (int) Math.min(count, Integer.MAX_VALUE)));
            }
            return counts;
        });
    }

    /**
     * A page of the held submissions of the site, or of one of its forms, oldest first, by a query of the repository.
     *
     * @param formId the form, or {@code null} for every form; a value that is not a form identifier matches nothing
     * @param states the states to keep, each once at most, or {@code null} or empty for every state
     * @param offset the index of the first item; a negative or missing value is 0
     * @param limit  the size of the page, {@value #DEFAULT_LIMIT} when missing, at most {@value #MAX_LIMIT}
     */
    GqlSubmissionPage page(String formId, List<GqlState> states, Integer offset, Integer limit) {
        int from = offset == null ? 0 : Math.max(0, offset);
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(0, limit), MAX_LIMIT);
        if (states != null && states.size() > GqlState.values().length) {
            throw new DataFetchingException(TOO_MANY_STATES);
        }
        if (formId != null && !HeldIds.isFormId(formId)) {
            return new GqlSubmissionPage(0, from, size, List.of());
        }
        return guarded("list", () -> {
            Set<String> stored = new HashSet<>();
            if (states != null && !states.isEmpty()) {
                EnumSet.copyOf(states).forEach(state -> stored.add(stored(state)));
            }
            HeldQueries.Page page = read(() -> services.queries().page(siteKey, formId, stored, from, size));
            List<GqlHeldSubmission> items = page.records().stream().map(this::answer).toList();
            return new GqlSubmissionPage((int) Math.min(page.total(), Integer.MAX_VALUE), from, size, items);
        });
    }

    /**
     * The held submission of the site with this identifier, or {@code null} when the site has none: the identifier
     * names a path below the root of the site, and the record names the site, as the query of a page requires.
     */
    GqlHeldSubmission submission(String id) {
        String path = HeldIds.pathOf(siteKey, id);
        if (path == null) {
            return null;
        }
        return guarded("read", () -> read(() -> services.store().heldRecord(path))
                .filter(held -> siteKey.equals(held.siteKey())).map(this::answer).orElse(null));
    }

    /**
     * Runs the operation as the user of the request, then answers the held submission as the operation left it. The
     * operations check the permission on the site again, and write the audit record with the name of the user.
     *
     * @throws DataFetchingException when the site has no such held submission, when the state does not allow the
     *                               operation, when the operation failed, or, with its own message, when it ran
     *                               but the submission could not be read again
     */
    GqlHeldSubmission operate(String id, GqlOperation operation) {
        String path = HeldIds.pathOf(siteKey, id);
        if (path == null || operation == null) {
            throw new DataFetchingException(String.format(NOT_FOUND, id));
        }
        try {
            services.operator().apply(path, operation.operation());
        } catch (OperationRefusedException e) {
            throw new DataFetchingException(e.reason() == OperationRefusedException.Reason.CONFLICT
                    ? e.getMessage() : String.format(NOT_FOUND, id));
        } catch (QuarantineStoreException e) {
            if (log.isWarnEnabled()) {
                log.warn("The operation {} on {} failed: {}", operation, path, e.getMessage());
            }
            throw new DataFetchingException(OPERATION_FAILED);
        } catch (RuntimeException e) {
            // the class alone: the message of an action that a release or a resume ran may quote a held value
            if (log.isWarnEnabled()) {
                log.warn("The operation {} on {} failed with {}", operation, path, e.getClass().getName());
            }
            throw new DataFetchingException(OPERATION_FAILED);
        }
        details.remove(path);
        GqlHeldSubmission after;
        try {
            after = submission(id);
            if (after != null) {
                // read now what the fields of the answer read, so a failure says that the operation ran
                details(path);
                form(after.getFormId());
            }
        } catch (RuntimeException e) {
            log.warn("The operation {} on {} ran, and the submission could not be read again", operation, path);
            throw new DataFetchingException(RAN_NOT_READ);
        }
        if (after == null) {
            throw new DataFetchingException(RAN_NOT_READ);
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
        if (!services.managers().isServerAdministrator()) {
            throw new DataFetchingException(ADMINISTRATORS_ONLY);
        }
        if (olderThanDays != null && olderThanDays < 0) {
            throw new DataFetchingException("olderThanDays is a number of days");
        }
        return guarded("purge", () -> {
            QuarantineRetention.Report report = read(() -> services.purger().purge(siteKey, olderThanDays));
            return new GqlPurgeReport(report.retentionDays(), ids(report.purged()), ids(report.failed()));
        });
    }

    FormFacts form(String formId) {
        return guarded("read a form", () -> formFacts.computeIfAbsent(formId, this::readForm));
    }

    private FormFacts readForm(String formId) {
        try {
            return HeldIds.isFormId(formId) ? services.forms().read(siteKey, formId) : FormFacts.NONE;
        } catch (RepositoryException e) {
            if (log.isWarnEnabled()) {
                log.warn("Could not read form {} of site {}: {}", formId, siteKey, e.getMessage());
            }
            throw new DataFetchingException(READ_FAILED);
        }
    }

    /**
     * The records of the held submission: read with those of every submission this request answered and has not read
     * yet, in one session, and the public values of those whose form shows them.
     */
    synchronized HeldDetails details(String path) {
        HeldDetails known = details.get(path);
        if (known != null) {
            return known;
        }
        return guarded("read the records", () -> {
            List<String> batch = new ArrayList<>();
            Set<String> withValues = new HashSet<>();
            for (Map.Entry<String, String> held : answered.entrySet()) {
                if (!details.containsKey(held.getKey())) {
                    batch.add(held.getKey());
                    if (form(held.getValue()).showValues()) {
                        withValues.add(held.getKey());
                    }
                }
            }
            if (!batch.contains(path)) {
                batch.add(path);
            }
            Map<String, HeldDetails> read = read(() -> services.checkStore().details(batch, withValues));
            batch.forEach(one -> details.put(one, read.getOrDefault(one, NO_DETAILS)));
            return details.get(path);
        });
    }

    /** Runs a read of a field, and answers any failure but a GraphQL error with the fixed message. */
    <T> T guarded(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataFetchingException e) {
            throw e;
        } catch (RuntimeException e) {
            // the class alone: a message may name a property or quote a value
            if (log.isWarnEnabled()) {
                log.warn("Could not {} in the quarantine of site {}: {}", what, siteKey, e.getClass().getName());
            }
            throw new DataFetchingException(READ_FAILED);
        }
    }

    private GqlHeldSubmission answer(HeldRecord held) {
        answered.put(held.path(), held.formId());
        return new GqlHeldSubmission(this, held);
    }

    private static String stored(GqlState state) {
        return HeldState.valueOf(state.name()).value();
    }

    private List<String> ids(List<String> paths) {
        return paths.stream().map(path -> HeldIds.idOf(siteKey, path)).toList();
    }

    private <T> T read(StoreCall<T> call) {
        try {
            return call.call();
        } catch (QuarantineStoreException e) {
            if (log.isWarnEnabled()) {
                log.warn("Could not read the quarantine tree of site {}: {}", siteKey, e.getMessage());
            }
            throw new DataFetchingException(READ_FAILED);
        }
    }
}
