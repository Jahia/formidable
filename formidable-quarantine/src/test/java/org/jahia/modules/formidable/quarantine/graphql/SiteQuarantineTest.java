package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.formidable.quarantine.ops.Managers;
import org.jahia.modules.formidable.quarantine.ops.Operation;
import org.jahia.modules.formidable.quarantine.ops.OperationRefusedException;
import org.jahia.modules.formidable.quarantine.ops.OperationResult;
import org.jahia.modules.formidable.quarantine.ops.QuarantineOperations;
import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine;
import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldQueries;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/**
 * The GraphQL API of the quarantine over the stores in memory: the permission on the site before any read, the scope
 * of the site, the page and its limit, the values a form shows, the allowed operations, the operations and the purge,
 * the records read once per request, and the errors and log lines that never quote a value.
 */
class SiteQuarantineTest {

    private static final String SITE = InMemoryQuarantine.SITE;
    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final String OTHER_FORM = "6a1f0c3e-0000-4000-8000-000000000002";
    private static final String SHA = "0".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    /** A double of {@link Managers}: who the user is, and the sites the user manages. */
    private static class FakeManagers implements Managers {
        boolean administrator;
        final List<String> managedSites = new ArrayList<>(List.of(SITE));

        @Override
        public String currentUserName() {
            return "quarManager";
        }

        @Override
        public boolean isServerAdministrator() {
            return administrator;
        }

        @Override
        public boolean mayManage(String siteKey) {
            return administrator || managedSites.contains(siteKey);
        }
    }

    private InMemoryQuarantine stores;
    private FakeManagers managers;
    private FormFacts facts;
    private final List<String> operated = new ArrayList<>();
    private Exception refusal;
    private final List<String> formsRead = new ArrayList<>();
    /** Each call of the store and of the queries: the method and its arguments. */
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private Logger log;

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        managers = new FakeManagers();
        facts = new FormFacts("Claims", false, Map.of("remoteCheck", "fmdbquar:remoteStubCheck"));
        log = mock(Logger.class);
    }

    /** A double that records each call, then hands it to the stores in memory. */
    @SuppressWarnings("unchecked")
    private <T> T recording(Class<T> type) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            calls.add(method.getName() + " " + Arrays.toString(args));
            try {
                return method.invoke(stores, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    private SiteQuarantine site() {
        return new SiteQuarantine(SITE, new SiteQuarantine.Services(recording(QuarantineStore.class), stores,
                recording(HeldQueries.class), this::operate, this::purge, managers, (siteKey, formId) -> {
            formsRead.add(formId);
            return facts;
        }), log);
    }

    private OperationResult operate(String path, Operation operation)
            throws OperationRefusedException, QuarantineStoreException {
        operated.add(operation.value() + " " + path);
        if (refusal instanceof OperationRefusedException refused) {
            throw refused;
        }
        if (refusal instanceof QuarantineStoreException failed) {
            throw failed;
        }
        if (refusal instanceof RuntimeException failed) {
            throw failed;
        }
        Held held = stores.held.get(path);
        held.state = HeldState.REJECTED.value();
        held.audit.add(new AuditRecord(operation.value(), "quarManager", "needsReview", "rejected", NOW));
        return new OperationResult(path, operation, HeldState.NEEDS_REVIEW, HeldState.REJECTED);
    }

    private QuarantineRetention.Report purge(String siteKey, Integer olderThanDays) {
        List<String> purged = new ArrayList<>();
        for (Held held : List.copyOf(stores.held.values())) {
            if (HeldState.REJECTED.value().equals(held.state)) {
                purged.add(held.path);
                stores.held.remove(held.path);
            }
        }
        return new QuarantineRetention.Report(olderThanDays == null ? 30 : olderThanDays, purged, List.of());
    }

    private Held inState(String formId, HeldState state) {
        Held held = stores.hold(formId, "INCONCLUSIVE\nA note");
        held.state = state.value();
        return held;
    }

    private static String idOf(Held held) {
        return HeldIds.idOf(SITE, held.path);
    }

    /** Every argument of every call of the log, varargs flattened: what a log line would print. */
    private List<Object> loggedArguments() {
        List<Object> logged = new ArrayList<>();
        for (Invocation invocation : mockingDetails(log).getInvocations()) {
            for (Object argument : invocation.getArguments()) {
                if (argument instanceof Object[] many) {
                    logged.addAll(Arrays.asList(many));
                } else {
                    logged.add(argument);
                }
            }
        }
        return logged;
    }

    private List<String> callsOf(String method) {
        return calls.stream().filter(call -> call.startsWith(method + " ")).toList();
    }

    @Test
    void theApiOpensTheQuarantineOfASiteToItsManagersAlone() {
        ManagerApi api = new ManagerApi(managers, (siteKey, formId) -> facts);
        api.setStore(stores);
        api.setCheckStore(stores);
        api.setQueries(stores);
        api.setOperations(new QuarantineOperations());
        api.setRetention(new QuarantineRetention());

        assertNotNull(api.open(SITE), "a manager of the site");
        assertNull(api.open("otherSite"), "a site the user does not manage");
        managers.managedSites.add("../" + SITE);
        managers.managedSites.add("a/b");
        assertNull(api.open("../" + SITE), "a key with a path separator");
        assertNull(api.open("a/b"), "a key with a path separator");
        assertNull(api.open(null), "no key");
        managers.managedSites.clear();
        assertNull(api.open(SITE), "a user who manages no site");
    }

    @Test
    void aFailureOfThePermissionCheckAnswersTheFixedMessage() {
        Managers failing = new FakeManagers() {
            @Override
            public boolean mayManage(String siteKey) {
                throw new IllegalStateException("the property secretName is missing");
            }
        };
        ManagerApi api = new ManagerApi(failing, (siteKey, formId) -> facts);

        DataFetchingException error = assertThrows(DataFetchingException.class, () -> api.open(SITE));

        assertEquals(SiteQuarantine.READ_FAILED, error.getMessage());
    }

    @Test
    void theCountsListEveryStateOfTheSite() {
        inState(FORM, HeldState.NEEDS_REVIEW);
        inState(FORM, HeldState.NEEDS_REVIEW);
        inState(OTHER_FORM, HeldState.COMPLETED);

        List<GqlStateCount> counts = site().counts();

        assertEquals(GqlState.values().length, counts.size(), "every state listed");
        for (GqlStateCount count : counts) {
            int expected = switch (count.getState()) {
                case NEEDS_REVIEW -> 2;
                case COMPLETED -> 1;
                default -> 0;
            };
            assertEquals(expected, count.getCount(), count.getState().name());
        }
        assertEquals(GqlState.values().length, callsOf("count").size(), "one query per state");
    }

    @Test
    void aPageKeepsTheStatesAskedForOldestFirst() {
        Held first = inState(FORM, HeldState.NEEDS_REVIEW);
        inState(FORM, HeldState.COMPLETED);
        Held third = inState(OTHER_FORM, HeldState.NEEDS_REVIEW);

        GqlSubmissionPage page = site().page(null, List.of(GqlState.NEEDS_REVIEW, GqlState.NEEDS_REVIEW), 0, 25);

        assertEquals(2, page.getTotalCount());
        assertEquals(List.of(idOf(first), idOf(third)), page.getItems().stream().map(GqlHeldSubmission::getId).toList());
        assertEquals(List.of(idOf(first)),
                site().page(FORM, List.of(GqlState.NEEDS_REVIEW), null, null).getItems().stream()
                        .map(GqlHeldSubmission::getId).toList(), "one form");
    }

    @Test
    void aPageHoldsAtMostOneHundredItemsAndANegativeOffsetStartsAtTheFirst() {
        for (int i = 0; i < 105; i++) {
            inState(FORM, HeldState.NEEDS_REVIEW);
        }

        GqlSubmissionPage page = site().page(FORM, null, -3, 1000);

        assertEquals(105, page.getTotalCount());
        assertEquals(SiteQuarantine.MAX_LIMIT, page.getLimit());
        assertEquals(SiteQuarantine.MAX_LIMIT, page.getItems().size());
        assertEquals(0, page.getOffset());
        assertTrue(callsOf("page").get(0).endsWith(", 0, 100]"), callsOf("page").toString());
        assertEquals(5, site().page(FORM, List.of(), 100, 100).getItems().size(), "the last page");
        assertEquals(SiteQuarantine.DEFAULT_LIMIT, site().page(FORM, null, null, null).getItems().size());
    }

    @Test
    void aListOfStatesLongerThanTheStatesIsRefused() {
        List<GqlState> states = new ArrayList<>(List.of(GqlState.values()));
        states.add(GqlState.HELD);

        SiteQuarantine site = site();
        DataFetchingException error = assertThrows(DataFetchingException.class, () -> site.page(null, states, 0, 25));

        assertEquals(SiteQuarantine.TOO_MANY_STATES, error.getMessage());
        assertTrue(calls.isEmpty(), "no query");
    }

    /** f2: the guard of the form, not the double, keeps the query from running. */
    @Test
    void aFormIdentifierThatIsNotOneQueriesNothing() {
        inState(FORM, HeldState.NEEDS_REVIEW);

        GqlSubmissionPage page = site().page("../" + FORM, null, 0, 25);

        assertEquals(0, page.getTotalCount());
        assertTrue(page.getItems().isEmpty());
        assertTrue(calls.isEmpty(), calls.toString());
    }

    /** f3: the guard of the identifier, not the double, keeps the store from reading another path. */
    @Test
    void aSubmissionIsReadBelowTheRootOfTheSiteOnly() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);

        assertEquals(idOf(held), site().submission(idOf(held)).getId());
        assertEquals(1, callsOf("heldRecord").size());
        assertNull(site().submission(FORM + "/2026/11/19/held-00000000-0000-4000-8000-999999999999"), "unknown");
        calls.clear();
        assertNull(site().submission("../../otherSite/formidable-quarantine/" + idOf(held)), "relative segment");
        assertNull(site().submission(held.path), "an absolute path");
        assertNull(site().submission(idOf(held) + "/item0"), "a node below the submission");
        assertNull(site().submission(null), "no identifier");
        assertTrue(calls.isEmpty(), calls.toString());
    }

    /**
     * f24: a record that names another site than its path is not opened. JcrHeldQueriesTest proves that a page leaves
     * it out: the query, not this class, filters the page.
     */
    @Test
    void aRecordThatNamesAnotherSiteIsNotOpened() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);
        held.siteKey = "otherSite";
        Held none = inState(FORM, HeldState.NEEDS_REVIEW);
        none.siteKey = null;

        assertNull(site().submission(idOf(held)), "opened");
        assertNull(site().submission(idOf(none)), "opened with no site");
    }

    @Test
    void theFilesCarryTheOutcomesOfTheirChecksWithTheTypeOfEachCheck() {
        Held held = stores.hold(FORM, "VERDICT:PASS\nOne", "VERDICT:FAIL\nTwo");
        held.outcomes.put("item0/remoteCheck", new OutcomeRecord("item0", "remoteCheck", SHA, OutcomeRecord.PASS,
                "stub.marker", false, null, 0, NOW));
        held.outcomes.put("item1/goneCheck", new OutcomeRecord("item1", "goneCheck", SHA, OutcomeRecord.PENDING,
                null, false, "a-ticket", 0, NOW));

        List<GqlFile> files = site().submission(idOf(held)).getFiles();

        assertEquals(List.of("item0", "item1"), files.stream().map(GqlFile::getItem).toList());
        GqlCheckOutcome passed = files.get(0).getChecks().get(0);
        assertEquals(List.of("remoteCheck", "fmdbquar:remoteStubCheck", "PASS", "stub.marker"),
                List.of(passed.getName(), passed.getType(), passed.getVerdict(), passed.getReasonCode()));
        GqlCheckOutcome pending = files.get(1).getChecks().get(0);
        assertNull(pending.getType(), "a check the form no longer lists");
        assertTrue(pending.isTicketPending(), "a ticket is pending, and never answered");
    }

    @Test
    void thePublicValuesAreHiddenUnlessTheFormShowsThem() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);

        assertNull(site().submission(idOf(held)).getPublicValues(), "a form that does not show its values");

        facts = new FormFacts("Claims", true, Map.of());
        List<GqlValue> values = site().submission(idOf(held)).getPublicValues();
        assertEquals(List.of("fullName"), values.stream().map(GqlValue::getName).toList());
        assertEquals(List.of("Ada"), values.get(0).getValues());
        assertEquals(List.of("password"), site().submission(idOf(held)).getSensitiveFields(), "the name alone");

        held.valuesPresent = false;
        assertEquals(List.of(), site().submission(idOf(held)).getPublicValues(), "values deleted");
    }

    /** f21: the records of every submission of a page are read in one call, and the form once. */
    @Test
    void theRecordsOfAPageAreReadOnceAndTheFormOnce() {
        for (int i = 0; i < 5; i++) {
            inState(FORM, HeldState.NEEDS_REVIEW);
        }
        facts = new FormFacts("Claims", true, Map.of());
        SiteQuarantine site = site();

        for (GqlHeldSubmission submission : site.page(FORM, null, 0, 25).getItems()) {
            submission.getFiles();
            submission.getAudit();
            submission.getDecision();
            submission.getPublicValues();
            submission.getAllowedOperations();
            assertEquals("Claims", submission.getFormTitle());
        }

        assertEquals(1, stores.detailReads, "reads of the records");
        assertEquals(List.of(FORM), formsRead);
    }

    @Test
    void theAllowedOperationsFollowTheStateAndTheDecision() {
        Held review = inState(FORM, HeldState.NEEDS_REVIEW);
        review.decision = new DecisionRecord(DecisionRecord.ACTION_FAILED, null, null, null, 1, NOW);
        Held completed = inState(FORM, HeldState.COMPLETED);

        assertEquals(List.of(GqlOperation.RETRY, GqlOperation.RESUME, GqlOperation.REJECT),
                site().submission(idOf(review)).getAllowedOperations());
        assertEquals("ACTION_FAILED", site().submission(idOf(review)).getReason());
        assertEquals(List.of(), site().submission(idOf(completed)).getAllowedOperations());
    }

    @Test
    void anOperationRunsOnThePathBelowTheRootOfTheSiteAndAnswersTheSubmissionAfterIt() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);
        SiteQuarantine site = site();
        assertEquals(List.of(), site.submission(idOf(held)).getAudit(), "audit read before");

        GqlHeldSubmission after = site.operate(idOf(held), GqlOperation.REJECT);

        assertEquals(List.of("reject " + held.path), operated);
        assertEquals(GqlState.REJECTED, after.getState());
        assertEquals(List.of("quarManager"), after.getAudit().stream().map(GqlAuditRecord::getUser).toList());
    }

    @Test
    void anOperationOnAnIdentifierThatIsNotOneRunsNothing() {
        SiteQuarantine site = site();
        DataFetchingException error = assertThrows(DataFetchingException.class,
                () -> site.operate("../../otherSite/x", GqlOperation.REJECT));

        assertTrue(error.getMessage().contains("No held submission"), error.getMessage());
        assertTrue(operated.isEmpty());
    }

    @Test
    void aRefusedOperationAnswersNotFoundOrTheConflict() {
        String id = idOf(inState(FORM, HeldState.NEEDS_REVIEW));
        SiteQuarantine site = site();

        refusal = new OperationRefusedException(OperationRefusedException.Reason.NOT_FOUND, "secret path detail");
        DataFetchingException notFound = assertThrows(DataFetchingException.class,
                () -> site.operate(id, GqlOperation.RELEASE));
        assertEquals(String.format(SiteQuarantine.NOT_FOUND, id), notFound.getMessage());

        refusal = new OperationRefusedException(OperationRefusedException.Reason.CONFLICT,
                "The operation release is not allowed in state completed");
        DataFetchingException conflict = assertThrows(DataFetchingException.class,
                () -> site.operate(id, GqlOperation.RELEASE));
        assertTrue(conflict.getMessage().contains("is not allowed in state"), conflict.getMessage());
    }

    /** f1: neither the error nor the log line quotes the message of the failure, which may hold a value. */
    @Test
    void aFailedOperationNeverQuotesTheMessageOfTheFailure() {
        String id = idOf(inState(FORM, HeldState.NEEDS_REVIEW));
        refusal = new IllegalStateException("The target refused the value Ada");
        SiteQuarantine site = site();

        DataFetchingException error = assertThrows(DataFetchingException.class,
                () -> site.operate(id, GqlOperation.RELEASE));

        assertEquals(SiteQuarantine.OPERATION_FAILED, error.getMessage());
        List<Object> logged = loggedArguments();
        assertTrue(logged.contains(IllegalStateException.class.getName()), "the class of the failure in the log line");
        assertFalse(logged.toString().contains("Ada"), "the message of the failure in the log line");
    }

    /** f16: an operation that ran, and whose submission cannot be read again, says it ran. */
    @Test
    void anOperationThatRanAndCannotBeReadAgainSaysItRan() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);
        refusal = null;
        SiteQuarantine site = new SiteQuarantine(SITE, new SiteQuarantine.Services(stores, stores, stores,
                (path, operation) -> {
                    OperationResult result = operate(path, operation);
                    stores.broken.add(path);
                    return result;
                }, this::purge, managers, (siteKey, formId) -> facts), log);

        String id = idOf(held);
        DataFetchingException error = assertThrows(DataFetchingException.class,
                () -> site.operate(id, GqlOperation.REJECT));

        assertEquals(SiteQuarantine.RAN_NOT_READ, error.getMessage());
        assertEquals("rejected", held.state, "the operation ran");
    }

    /** Round 3: an operation that ran, whose audit, decision or outcomes cannot be read, says that it ran. */
    @Test
    void anOperationThatRanAndWhoseRecordsCannotBeReadSaysItRan() {
        String id = idOf(inState(FORM, HeldState.NEEDS_REVIEW));
        CheckStore failingDetails = (CheckStore) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CheckStore.class}, (proxy, method, args) -> {
                    if ("details".equals(method.getName())) {
                        throw new QuarantineStoreException("Could not read the records");
                    }
                    try {
                        return method.invoke(stores, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        SiteQuarantine site = new SiteQuarantine(SITE, new SiteQuarantine.Services(stores, failingDetails, stores,
                this::operate, this::purge, managers, (siteKey, formId) -> facts), log);

        DataFetchingException error = assertThrows(DataFetchingException.class,
                () -> site.operate(id, GqlOperation.REJECT));

        assertEquals(SiteQuarantine.RAN_NOT_READ, error.getMessage());
        assertEquals(List.of("reject " + stores.held.values().iterator().next().path), operated, "the operation ran");
    }

    /** f23: a failure that is not one of the store answers the fixed message, and logs its class alone. */
    @Test
    void anUnexpectedFailureOfAReadAnswersTheFixedMessage() {
        inState(FORM, HeldState.NEEDS_REVIEW);
        SiteQuarantine site = new SiteQuarantine(SITE, new SiteQuarantine.Services(stores, stores,
                new HeldQueries() {
                    @Override
                    public Page page(String siteKey, String formId, Set<String> states, int offset, int limit) {
                        throw new NullPointerException("the property fullName of Ada");
                    }

                    @Override
                    public long count(String siteKey, String state) {
                        throw new NullPointerException("the property fullName of Ada");
                    }
                }, this::operate, this::purge, managers, (siteKey, formId) -> facts), log);

        assertEquals(SiteQuarantine.READ_FAILED,
                assertThrows(DataFetchingException.class, () -> site.page(null, null, 0, 25)).getMessage());
        assertEquals(SiteQuarantine.READ_FAILED, assertThrows(DataFetchingException.class, site::counts).getMessage());
        List<Object> logged = loggedArguments();
        assertTrue(logged.contains(NullPointerException.class.getName()), "the class of the failure in the log line");
        assertFalse(logged.toString().contains("Ada"), "the message of the failure in the log line");
    }

    @Test
    void thePurgeIsForServerAdministratorsAlone() {
        Held rejected = inState(FORM, HeldState.REJECTED);
        inState(FORM, HeldState.COMPLETED);

        SiteQuarantine site = site();
        DataFetchingException error = assertThrows(DataFetchingException.class, () -> site.purge(0));
        assertEquals(SiteQuarantine.ADMINISTRATORS_ONLY, error.getMessage());
        assertEquals(2, stores.held.size(), "nothing purged");

        managers.administrator = true;
        assertThrows(DataFetchingException.class, () -> site.purge(-1), "a negative limit");
        GqlPurgeReport report = site().purge(0);
        assertEquals(List.of(idOf(rejected)), report.getPurged());
        assertEquals(0, report.getRetentionDays());
        assertEquals(1, stores.held.size());
    }

    @Test
    void aFailureOfTheStoreAnswersAFixedMessage() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);
        GqlHeldSubmission submission = site().submission(idOf(held));
        stores.broken.add(held.path);

        DataFetchingException error = assertThrows(DataFetchingException.class, submission::getAudit);

        assertEquals(SiteQuarantine.READ_FAILED, error.getMessage());
    }
}
