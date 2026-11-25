package org.jahia.modules.formidable.quarantine.store;

import org.junit.jupiter.api.Test;

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
            return List.of();
        }
    }

    @Test
    void theStatementSelectsTheHeldSubmissionsBelowTheRootThatNameTheSiteOldestFirst() {
        String statement = JcrHeldQueries.statement("siteA", true, 2);

        assertEquals("SELECT * FROM [" + QuarantineTree.HELD_TYPE + "] AS held"
                + " WHERE ISDESCENDANTNODE(held, '/sites/siteA/formidable-quarantine')"
                + " AND held.[" + QuarantineTree.SITE_KEY + "] = $site"
                + " AND held.[" + QuarantineTree.FORM_ID + "] = $form"
                + " AND (held.[" + QuarantineTree.STATE + "] = $state0 OR held.[" + QuarantineTree.STATE + "] = $state1)"
                + " ORDER BY held.[" + QuarantineTree.CREATED_AT + "] ASC", statement);
        assertFalse(JcrHeldQueries.statement("siteA", false, 0).contains("$form"), "no form");
        assertFalse(JcrHeldQueries.statement("siteA", false, 0).contains("$state"), "no state");
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

        new JcrHeldQueries(runner).page("siteA", form, Set.of("rejected", "needsReview"), 0, 25);

        Map<String, String> bound = runner.values.get(0);
        assertEquals(Map.of("site", "siteA", "form", form, "state0", "needsReview", "state1", "rejected"), bound);
        runner.statements.forEach(statement -> {
            assertFalse(statement.contains(form), "the form in the statement");
            assertFalse(statement.contains("needsReview"), "a state in the statement");
            assertFalse(statement.contains("= 'siteA'"), "the site key as a compared literal");
        });
    }

    @Test
    void thePageAsksForItsOffsetAndLimitAndSkipsTheQueryWhenNothingMatches() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        JcrHeldQueries queries = new JcrHeldQueries(runner);

        HeldQueries.Page page = queries.page("siteA", null, Set.of(), 1, 2);
        assertEquals(3, page.total());
        assertEquals(List.of(1, 2), List.of(runner.pages.get(0)[0], runner.pages.get(0)[1]));

        runner.total = 0;
        runner.pages.clear();
        assertTrue(queries.page("siteA", null, Set.of(), 0, 25).records().isEmpty());
        assertTrue(runner.pages.isEmpty(), "no page query when nothing matches");
    }

    @Test
    void aCountBindsTheSiteAndTheState() throws Exception {
        RecordingRunner runner = new RecordingRunner();

        assertEquals(3, new JcrHeldQueries(runner).count("siteA", "held"));

        assertEquals(Map.of("site", "siteA", "state0", "held"), runner.values.get(0));
    }
}
