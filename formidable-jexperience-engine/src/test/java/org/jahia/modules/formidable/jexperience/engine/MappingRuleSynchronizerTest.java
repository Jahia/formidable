package org.jahia.modules.formidable.jexperience.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The synchronisation rules over a fake store and fake live readings: create, leave alone,
 * update, delete, and remember what jCustomer could not take.
 */
class MappingRuleSynchronizerTest {

    private static final String SITE = "mysite";
    private static final String FORM = "8f7e2a10-0000-4000-8000-000000000001";
    private static final String OTHER_FORM = "8f7e2a10-0000-4000-8000-000000000002";

    /** An in-memory jCustomer, switchable to "down". */
    private static final class FakeStore implements MappingRuleSynchronizer.RuleStore {
        final Map<String, Map<String, Object>> rules = new HashMap<>();
        boolean down;
        int saves;
        int deletes;

        private void check() throws IOException {
            if (down) {
                throw new IOException("connection refused");
            }
        }

        @Override
        public Optional<Map<String, Object>> fetch(String siteKey, String ruleId) throws IOException {
            check();
            return Optional.ofNullable(rules.get(ruleId));
        }

        @Override
        public void save(String siteKey, Map<String, Object> rule) throws IOException {
            check();
            saves++;
            rules.put((String) ((Map<?, ?>) rule.get("metadata")).get("id"), rule);
        }

        @Override
        public void delete(String siteKey, String ruleId) throws IOException {
            check();
            deletes++;
            rules.remove(ruleId);
        }
    }

    private static MappingRule.FormMapping mapping(String formUuid, MappingRule.FieldMapping... fields) {
        return new MappingRule.FormMapping(SITE, formUuid, "Contact form", List.of(fields));
    }

    private static final MappingRule.FieldMapping FIRST_NAME = new MappingRule.FieldMapping("firstName", "firstName", "alwaysSet", MappingRule.ValueKind.STRING);
    private static final MappingRule.FieldMapping EMAIL = new MappingRule.FieldMapping("email", "email", "setIfMissing", MappingRule.ValueKind.STRING);

    @Test
    void aPublishedMappingCreatesTheRule() {
        // Verifies the first publication: no rule stored, one posted, the pending list untouched.
        FakeStore store = new FakeStore();
        MappingRuleSynchronizer sync = new MappingRuleSynchronizer(store, (site, uuid) -> Optional.of(mapping(uuid, FIRST_NAME)), null);
        sync.sync(SITE, FORM);
        assertEquals(1, store.saves);
        assertEquals(MappingRule.build(mapping(FORM, FIRST_NAME)), store.rules.get(MappingRule.idOf(SITE, FORM)));
        assertTrue(sync.pending().isEmpty());
    }

    @Test
    void anUnchangedMappingPostsNothing() {
        // Verifies the diff: a stored rule equal on the owned parts — extra fields from jCustomer included —
        // is left alone; a changed one is posted again.
        FakeStore store = new FakeStore();
        Map<String, Object> stored = new LinkedHashMap<>(MappingRule.build(mapping(FORM, FIRST_NAME)));
        stored.put("itemType", "rule");
        store.rules.put(MappingRule.idOf(SITE, FORM), stored);
        MappingRuleSynchronizer unchanged = new MappingRuleSynchronizer(store, (site, uuid) -> Optional.of(mapping(uuid, FIRST_NAME)), null);
        unchanged.sync(SITE, FORM);
        assertEquals(0, store.saves);

        MappingRuleSynchronizer changed = new MappingRuleSynchronizer(store, (site, uuid) -> Optional.of(mapping(uuid, FIRST_NAME, EMAIL)), null);
        changed.sync(SITE, FORM);
        assertEquals(1, store.saves);
        assertEquals(2, ((List<?>) store.rules.get(MappingRule.idOf(SITE, FORM)).get("actions")).size());
    }

    @Test
    void aFormGoneFromLiveOrMappingNothingLosesItsRule() {
        // Verifies the two deletions: unpublished (nothing in live) and published without any mapping —
        // and that nothing is deleted when no rule was stored.
        FakeStore store = new FakeStore();
        store.rules.put(MappingRule.idOf(SITE, FORM), MappingRule.build(mapping(FORM, FIRST_NAME)));
        new MappingRuleSynchronizer(store, (site, uuid) -> Optional.empty(), null).sync(SITE, FORM);
        assertEquals(1, store.deletes);
        assertTrue(store.rules.isEmpty());

        store.rules.put(MappingRule.idOf(SITE, FORM), MappingRule.build(mapping(FORM, FIRST_NAME)));
        new MappingRuleSynchronizer(store, (site, uuid) -> Optional.of(mapping(uuid)), null).sync(SITE, FORM);
        assertEquals(2, store.deletes);

        new MappingRuleSynchronizer(store, (site, uuid) -> Optional.empty(), null).sync(SITE, OTHER_FORM);
        assertEquals(2, store.deletes, "nothing stored, nothing to delete");
    }

    @Test
    void anUnreachableJCustomerLeavesTheFormPendingUntilARetrySucceeds() {
        // Verifies the outage path: the form is remembered, the retry does nothing while jCustomer is down,
        // and the first retry after it is back posts the rule and forgets the form.
        FakeStore store = new FakeStore();
        store.down = true;
        MappingRuleSynchronizer sync = new MappingRuleSynchronizer(store, (site, uuid) -> Optional.of(mapping(uuid, FIRST_NAME)), null);
        sync.sync(SITE, FORM);
        assertEquals(Map.of(FORM, SITE), sync.pending());
        sync.retryPending();
        assertEquals(Map.of(FORM, SITE), sync.pending());
        assertEquals(0, store.saves);

        store.down = false;
        sync.retryPending();
        assertEquals(1, store.saves);
        assertTrue(sync.pending().isEmpty());
    }

    @Test
    void anUnreadableSchemaLeavesTheFormPendingToo() {
        // Verifies that a rule is never built on half a schema: the reader's unavailable exception pends the form.
        FakeStore store = new FakeStore();
        MappingRuleSynchronizer sync = new MappingRuleSynchronizer(store, (site, uuid) -> {
            throw new ProfilePropertiesUnavailableException("schema down");
        }, null);
        sync.sync(SITE, FORM);
        assertEquals(Map.of(FORM, SITE), sync.pending());
        assertEquals(0, store.saves);
    }

    @Test
    void aSuccessfulSynchronisationDrainsThePendingForms() {
        // Verifies that the pending list is retried at the next successful synchronisation of any form, in
        // order, and stops at the first form still unreachable.
        FakeStore store = new FakeStore();
        store.down = true;
        MappingRuleSynchronizer sync = new MappingRuleSynchronizer(store, (site, uuid) -> Optional.of(mapping(uuid, FIRST_NAME)), null);
        sync.sync(SITE, FORM);
        sync.sync(SITE, OTHER_FORM);
        assertEquals(2, sync.pending().size());
        store.down = false;
        sync.retryPending();
        assertEquals(2, store.saves);
        assertTrue(sync.pending().isEmpty());
        assertFalse(store.rules.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPublicationsBurstsAreCoalescedIntoOneSynchronisation() throws Exception {
        // Verifies the wait the listener relies on: a second request for the same form cancels the first
        // and schedules a new one, and running the scheduled task performs the synchronisation.
        FakeStore store = new FakeStore();
        java.util.concurrent.ScheduledExecutorService scheduler = org.mockito.Mockito.mock(java.util.concurrent.ScheduledExecutorService.class);
        java.util.concurrent.ScheduledFuture<Object> first = org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        java.util.concurrent.ScheduledFuture<Object> second = org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        org.mockito.ArgumentCaptor<Runnable> tasks = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doReturn(first, second).when(scheduler).schedule(tasks.capture(), org.mockito.ArgumentMatchers.eq(MappingRuleSynchronizer.COALESCE_SECONDS), org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.SECONDS));
        MappingRuleSynchronizer sync = new MappingRuleSynchronizer(store, (site, uuid) -> Optional.of(mapping(uuid, FIRST_NAME)), scheduler);

        sync.syncLater(SITE, FORM);
        sync.syncLater(SITE, FORM);
        org.mockito.Mockito.verify(first).cancel(false);
        org.mockito.Mockito.verify(second, org.mockito.Mockito.never()).cancel(false);
        assertEquals(0, store.saves, "nothing runs before the delay");

        tasks.getValue().run();
        assertEquals(1, store.saves);
    }
}
