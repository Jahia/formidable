package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.run.InMemoryQuarantine.Held;
import org.jahia.modules.formidable.quarantine.state.HeldEvent;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.HeldStates;
import org.jahia.modules.formidable.quarantine.store.StaleStateException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each state move is a compare-and-set: of two movers from the same state, one moves it, the other is refused. */
class TransitionsTest {

    private static final String FORM = "6a1f0c3e-0000-4000-8000-000000000001";

    @Test
    void ofTwoMoversFromTheSameStateOnlyTheFirstMovesIt() throws Exception {
        InMemoryQuarantine stores = new InMemoryQuarantine();
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldStates.CHECKING;
        Transitions first = new Transitions(stores);
        Transitions second = new Transitions(stores);
        String path = held.path;

        assertEquals(HeldState.RELEASING, first.move(path, HeldState.CHECKING, HeldEvent.ALL_PASSED));
        assertThrows(StaleStateException.class, () -> second.move(path, HeldState.CHECKING, HeldEvent.ALL_PASSED));

        assertEquals(List.of(HeldStates.RELEASING), held.states);
    }

    @Test
    void aFinishFromAStateThatIsNoLongerStoredDeletesNothing() throws Exception {
        InMemoryQuarantine stores = new InMemoryQuarantine();
        Held held = stores.hold(FORM, "VERDICT:PASS\nA note");
        held.state = HeldStates.NEEDS_REVIEW;
        Transitions transitions = new Transitions(stores);
        String path = held.path;

        assertThrows(StaleStateException.class,
                () -> transitions.finish(path, HeldState.CHECKING, HeldEvent.CHECK_FAILED));

        assertEquals(HeldStates.NEEDS_REVIEW, held.state);
        assertTrue(held.valuesPresent, "values kept");
        assertTrue(held.anyBytes(), "bytes kept");
    }
}
