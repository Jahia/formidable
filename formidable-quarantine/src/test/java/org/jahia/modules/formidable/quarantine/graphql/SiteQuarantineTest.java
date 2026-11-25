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
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GraphQL API of the quarantine over the two stores in memory: the permission on the site before any read, the
 * scope of the site, the page and its limit, the values a form shows, the allowed operations, the operations and the
 * purge, and the errors that never quote a value.
 */
class SiteQuarantineTest {

    private static final String SITE = InMemoryQuarantine.SITE;
    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final String OTHER_FORM = "6a1f0c3e-0000-4000-8000-000000000002";
    private static final String SHA = "0".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    /** A double of {@link Managers}: who the user is, and the sites the user manages. */
    private static final class FakeManagers implements Managers {
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

    @BeforeEach
    void setUp() {
        stores = new InMemoryQuarantine();
        managers = new FakeManagers();
        facts = new FormFacts("Claims", false, Map.of("remoteCheck", "fmdbquar:remoteStubCheck"));
    }

    private SiteQuarantine site() {
        return new SiteQuarantine(SITE, stores, stores, this::operate, this::purge, managers, (siteKey, formId) -> {
            formsRead.add(formId);
            return facts;
        });
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

    @Test
    void theApiOpensTheQuarantineOfASiteToItsManagersAlone() {
        ManagerApi api = new ManagerApi(managers, (siteKey, formId) -> facts);
        api.setStore(stores);
        api.setCheckStore(stores);
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
    }

    @Test
    void aPageKeepsTheStatesAskedForOldestFirst() {
        Held first = inState(FORM, HeldState.NEEDS_REVIEW);
        inState(FORM, HeldState.COMPLETED);
        Held third = inState(OTHER_FORM, HeldState.NEEDS_REVIEW);

        GqlSubmissionPage page = site().page(null, List.of(GqlState.NEEDS_REVIEW), 0, 25);

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
        assertEquals(5, site().page(FORM, List.of(), 100, 100).getItems().size(), "the last page");
        assertEquals(SiteQuarantine.DEFAULT_LIMIT, site().page(FORM, null, null, null).getItems().size());
    }

    @Test
    void aFormIdentifierThatIsNotOneMatchesNothing() {
        inState(FORM, HeldState.NEEDS_REVIEW);

        GqlSubmissionPage page = site().page("../" + FORM, null, 0, 25);

        assertEquals(0, page.getTotalCount());
        assertTrue(page.getItems().isEmpty());
    }

    @Test
    void aSubmissionIsReadBelowTheRootOfTheSiteOnly() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);

        assertEquals(idOf(held), site().submission(idOf(held)).getId());
        assertNull(site().submission(FORM + "/2026/11/19/held-00000000-0000-4000-8000-999999999999"), "unknown");
        assertNull(site().submission("../../otherSite/formidable-quarantine/" + idOf(held)), "relative segment");
        assertNull(site().submission(held.path), "an absolute path");
        assertNull(site().submission(null), "no identifier");
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
    void thePublicValuesAreHiddenUnlessTheFormShowsThemAndTheSensitiveValuesNeverAppear() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);

        assertNull(site().submission(idOf(held)).getPublicValues(), "a form that does not show its values");

        facts = new FormFacts("Claims", true, Map.of());
        List<GqlValue> values = site().submission(idOf(held)).getPublicValues();
        assertEquals(List.of("fullName"), values.stream().map(GqlValue::getName).toList());
        assertEquals(List.of("Ada"), values.get(0).getValues());
        assertEquals(List.of("password"), site().submission(idOf(held)).getSensitiveFields(), "the name alone");
        assertFalse(values.toString().contains(InMemoryQuarantine.SECRET));

        held.valuesPresent = false;
        assertEquals(List.of(), site().submission(idOf(held)).getPublicValues(), "values deleted");
    }

    @Test
    void theFormIsReadOncePerRequest() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);
        SiteQuarantine site = site();
        GqlHeldSubmission submission = site.submission(idOf(held));

        assertEquals("Claims", submission.getFormTitle());
        submission.getFiles();
        submission.getPublicValues();

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

        GqlHeldSubmission after = site().operate(idOf(held), GqlOperation.REJECT);

        assertEquals(List.of("reject " + held.path), operated);
        assertEquals(GqlState.REJECTED, after.getState());
        assertEquals(List.of("quarManager"), after.getAudit().stream().map(GqlAuditRecord::getUser).toList());
    }

    @Test
    void anOperationOnAnIdentifierThatIsNotOneRunsNothing() {
        DataFetchingException error = assertThrows(DataFetchingException.class,
                () -> site().operate("../../otherSite/x", GqlOperation.REJECT));

        assertTrue(error.getMessage().contains("No held submission"), error.getMessage());
        assertTrue(operated.isEmpty());
    }

    @Test
    void aRefusedOperationAnswersNotFoundOrTheConflict() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);

        refusal = new OperationRefusedException(OperationRefusedException.Reason.NOT_FOUND, "secret path detail");
        DataFetchingException notFound = assertThrows(DataFetchingException.class,
                () -> site().operate(idOf(held), GqlOperation.RELEASE));
        assertEquals(String.format(SiteQuarantine.NOT_FOUND, idOf(held)), notFound.getMessage());

        refusal = new OperationRefusedException(OperationRefusedException.Reason.CONFLICT,
                "The operation release is not allowed in state completed");
        DataFetchingException conflict = assertThrows(DataFetchingException.class,
                () -> site().operate(idOf(held), GqlOperation.RELEASE));
        assertTrue(conflict.getMessage().contains("is not allowed in state"), conflict.getMessage());
    }

    @Test
    void aFailedOperationNeverQuotesTheMessageOfTheFailure() {
        Held held = inState(FORM, HeldState.NEEDS_REVIEW);
        refusal = new IllegalStateException("The target refused the value Ada");

        DataFetchingException error = assertThrows(DataFetchingException.class,
                () -> site().operate(idOf(held), GqlOperation.RELEASE));

        assertEquals(SiteQuarantine.OPERATION_FAILED, error.getMessage());
    }

    @Test
    void thePurgeIsForServerAdministratorsAlone() {
        Held rejected = inState(FORM, HeldState.REJECTED);
        inState(FORM, HeldState.COMPLETED);

        DataFetchingException error = assertThrows(DataFetchingException.class, () -> site().purge(0));
        assertEquals(SiteQuarantine.ADMINISTRATORS_ONLY, error.getMessage());
        assertEquals(2, stores.held.size(), "nothing purged");

        managers.administrator = true;
        assertThrows(DataFetchingException.class, () -> site().purge(-1), "a negative limit");
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
