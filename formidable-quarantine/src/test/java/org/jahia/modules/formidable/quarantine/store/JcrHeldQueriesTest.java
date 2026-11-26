package org.jahia.modules.formidable.quarantine.store;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queries of the held submissions: the statement, the values bound to it, and the page. The run of a statement on
 * the repository is covered by the Cypress specs alone (90 and the helpers of 97 to 99).
 */
class JcrHeldQueriesTest {

    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";

    /** A runner that records each statement with its values, and answers a total. */
    private static final class RecordingRunner implements JcrHeldQueries.Runner {
        long total = 3;
        final List<String> statements = new ArrayList<>();
        final List<Map<String, String>> values = new ArrayList<>();
        final List<int[]> pages = new ArrayList<>();
        /** What the repository answers for a page: below the root of the site, whatever site each record names. */
        List<HeldRecord> found = List.of();

        @Override
        public long count(String statement, Map<String, String> bound) {
            statements.add(statement);
            values.add(new LinkedHashMap<>(bound));
            return total;
        }

        @Override
        public List<HeldRecord> page(String statement, Map<String, String> bound, int offset, int limit) {
            statements.add(statement);
            values.add(new LinkedHashMap<>(bound));
            pages.add(new int[]{offset, limit});
            return found;
        }

        @Override
        public List<String> formIds(String statement) {
            statements.add(statement);
            return List.of(FORM);
        }
    }

    @Test
    void theFormsAreTheFoldersBelowTheRootOfTheSite() throws Exception {
        RecordingRunner runner = new RecordingRunner();

        assertEquals(List.of(FORM), new JcrHeldQueries(runner).formIds("siteA"));
        assertEquals(List.of("SELECT * FROM [" + QuarantineTree.FORM_TYPE + "] AS form"
                + " WHERE ISCHILDNODE(form, '/sites/siteA/formidable-quarantine')"), runner.statements);
        assertThrows(IllegalArgumentException.class, () -> JcrHeldQueries.formsStatement("a') OR (1=1"));
    }

    @Test
    void theStatementSelectsTheHeldSubmissionsBelowTheRootThatNameTheSiteOldestFirst() {
        String statement = JcrHeldQueries.statement("siteA", true, 2);

        assertEquals("SELECT * FROM [" + QuarantineTree.HELD_TYPE + "] AS held"
                + " WHERE ISDESCENDANTNODE(held, '/sites/siteA/formidable-quarantine')"
                + " AND held.[" + QuarantineTree.FORM_ID + "] = $form"
                + " AND (held.[" + QuarantineTree.STATE + "] = $state0 OR held.[" + QuarantineTree.STATE + "] = $state1)"
                + " ORDER BY held.[" + QuarantineTree.CREATED_AT + "] ASC", statement);
        assertTrue(JcrHeldQueries.statement("siteA", false, 0, true).endsWith(
                " ORDER BY held.[" + QuarantineTree.CREATED_AT + "] DESC"), "the newest first");
        assertFalse(JcrHeldQueries.statement("siteA", false, 0).contains("$form"), "no form");
        assertFalse(JcrHeldQueries.statement("siteA", false, 0).contains("$state"), "no state");
    }

    /** The CND declares the site key of a held submission indexed=no: a constraint on it would match nothing. */
    @Test
    void noStatementConstrainsTheSiteKeyThatTheIndexLeavesOut() {
        for (String statement : List.of(JcrHeldQueries.statement("siteA", true, 2),
                JcrHeldQueries.statement("siteA", false, 0), JcrHeldQueries.statement("siteA", false, 1))) {
            assertFalse(statement.contains("[" + QuarantineTree.SITE_KEY + "]"), statement);
        }
    }

    @Test
    void aPageKeepsTheRecordsThatNameTheSiteOnly() throws Exception {
        HeldRecord ofTheSite = heldOf("siteA");
        RecordingRunner runner = new RecordingRunner();
        runner.found = List.of(heldOf("siteB"), ofTheSite, heldOf(null));

        List<HeldRecord> page = new JcrHeldQueries(runner).page("siteA", null, Set.of(), 0, 25);

        assertEquals(List.of(ofTheSite), page);
    }

    private static HeldRecord heldOf(String siteKey) {
        return new HeldRecord("/sites/siteA/formidable-quarantine/" + FORM + "/2026/11/19/held-" + siteKey, FORM,
                siteKey, "held", Instant.parse("2026-11-19T10:00:00Z"), 0, null, 0, List.of(), List.of(), true,
                List.of(), null);
    }

    @Test
    void aSiteKeyThatCouldEndTheLiteralIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> JcrHeldQueries.statement("a') OR (1=1", false, 0));
        assertThrows(IllegalArgumentException.class, () -> JcrHeldQueries.statement("a/b", false, 0));
    }

    @Test
    void everyValueOfTheCallerIsBoundAndNeverWrittenInTheStatement() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        String form = FORM;

        JcrHeldQueries queries = new JcrHeldQueries(runner);
        queries.count("siteA", form, Set.of("rejected", "needsReview"));
        queries.page("siteA", form, Set.of("rejected", "needsReview"), 0, 25);

        Map<String, String> bound = Map.of("form", form, "state0", "needsReview", "state1", "rejected");
        assertEquals(List.of(bound, bound), runner.values);
        runner.statements.forEach(statement -> {
            assertFalse(statement.contains(form), "the form in the statement");
            assertFalse(statement.contains("needsReview"), "a state in the statement");
            assertFalse(statement.contains("= 'siteA'"), "the site key as a compared literal");
        });
    }

    @Test
    void thePageAsksForItsOffsetAndLimitAndAPageOfNoItemRunsNoQuery() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        JcrHeldQueries queries = new JcrHeldQueries(runner);

        queries.page("siteA", null, Set.of(), 1, 2);
        assertEquals(List.of(1, 2), List.of(runner.pages.get(0)[0], runner.pages.get(0)[1]));

        runner.pages.clear();
        assertTrue(queries.page("siteA", null, Set.of(), 0, 0).isEmpty());
        assertTrue(runner.pages.isEmpty(), "no page query for a limit of 0");
    }

    @Test
    void aCountBindsTheState() throws Exception {
        RecordingRunner runner = new RecordingRunner();

        assertEquals(3, new JcrHeldQueries(runner).count("siteA", null, Set.of("held")));

        assertEquals(Map.of("state0", "held"), runner.values.get(0));
    }
}
