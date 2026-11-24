package org.jahia.test.modules.formidable.quarantine.state;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.jahia.test.modules.formidable.quarantine.state.HeldState.CHECKING;
import static org.jahia.test.modules.formidable.quarantine.state.HeldState.COMPLETED;
import static org.jahia.test.modules.formidable.quarantine.state.HeldState.HELD;
import static org.jahia.test.modules.formidable.quarantine.state.HeldState.NEEDS_REVIEW;
import static org.jahia.test.modules.formidable.quarantine.state.HeldState.REJECTED;
import static org.jahia.test.modules.formidable.quarantine.state.HeldState.RELEASING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest {

    /** The diagram of the plan, written out apart from the code: (state, event) to the next state. */
    private static final Map<String, HeldState> LEGAL = new HashMap<>();

    static {
        legal(HELD, HeldEvent.START, CHECKING);
        legal(CHECKING, HeldEvent.CONTINUE, CHECKING);
        legal(CHECKING, HeldEvent.CHECK_FAILED, REJECTED);
        legal(CHECKING, HeldEvent.REVIEW, NEEDS_REVIEW);
        legal(CHECKING, HeldEvent.INTEGRITY_FAILED, NEEDS_REVIEW);
        legal(CHECKING, HeldEvent.ALL_PASSED, RELEASING);
        legal(RELEASING, HeldEvent.ACTIONS_DONE, COMPLETED);
        legal(RELEASING, HeldEvent.ACTION_FAILED, NEEDS_REVIEW);
        legal(RELEASING, HeldEvent.INTEGRITY_FAILED, NEEDS_REVIEW);
        // a release that stopped half way (a store failure, an error, a stop of the JVM): a manager resumes or rejects
        legal(RELEASING, HeldEvent.MANAGER_RESUME, RELEASING);
        legal(RELEASING, HeldEvent.MANAGER_REJECT, REJECTED);
        legal(NEEDS_REVIEW, HeldEvent.MANAGER_RETRY, CHECKING);
        legal(NEEDS_REVIEW, HeldEvent.MANAGER_RELEASE, RELEASING);
        legal(NEEDS_REVIEW, HeldEvent.MANAGER_RESUME, RELEASING);
        legal(NEEDS_REVIEW, HeldEvent.MANAGER_REJECT, REJECTED);
    }

    private static void legal(HeldState from, HeldEvent event, HeldState to) {
        LEGAL.put(from + "/" + event, to);
    }

    @Test
    void everyLegalTransitionLeadsToTheStateOfTheDiagram() {
        int legalCount = 0;
        for (HeldState from : HeldState.values()) {
            for (HeldEvent event : HeldEvent.values()) {
                HeldState expected = LEGAL.get(from + "/" + event);
                if (expected != null) {
                    assertTrue(StateMachine.allows(from, event), from + "/" + event);
                    assertEquals(expected, StateMachine.next(from, event), from + "/" + event);
                    legalCount++;
                }
            }
        }
        assertEquals(LEGAL.size(), legalCount);
    }

    @Test
    void everyOtherTransitionThrowsANamedException() {
        for (HeldState from : HeldState.values()) {
            for (HeldEvent event : HeldEvent.values()) {
                if (!LEGAL.containsKey(from + "/" + event)) {
                    assertFalse(StateMachine.allows(from, event), from + "/" + event);
                    IllegalTransitionException refused = assertThrows(IllegalTransitionException.class,
                            () -> StateMachine.next(from, event), from + "/" + event);
                    assertSame(from, refused.from());
                    assertSame(event, refused.event());
                }
            }
        }
    }

    @Test
    void nothingLeavesCompletedOrRejected() {
        for (HeldEvent event : HeldEvent.values()) {
            assertFalse(StateMachine.allows(COMPLETED, event), event.name());
            assertFalse(StateMachine.allows(REJECTED, event), event.name());
        }
        assertTrue(COMPLETED.isFinal());
        assertTrue(REJECTED.isFinal());
        assertFalse(NEEDS_REVIEW.isFinal());
    }

    @Test
    void everyArrowOfTheDiagramHasAnEvent() {
        Set<String> arrows = new HashSet<>();
        for (HeldState from : HeldState.values()) {
            for (HeldEvent event : HeldEvent.values()) {
                if (StateMachine.allows(from, event)) {
                    arrows.add(from.value() + ">" + event.target().value());
                }
            }
        }
        assertEquals(Set.of("held>checking", "checking>checking", "checking>rejected", "checking>needsReview",
                "checking>releasing", "releasing>completed", "releasing>needsReview", "releasing>releasing",
                "releasing>rejected", "needsReview>checking",
                "needsReview>releasing", "needsReview>rejected"), arrows);
    }

    @Test
    void theStoredValueOfEachStateReadsBack() {
        for (HeldState state : HeldState.values()) {
            assertSame(state, HeldState.fromValue(state.value()));
        }
        assertEquals("needsReview", NEEDS_REVIEW.value());
        assertThrows(IllegalArgumentException.class, () -> HeldState.fromValue("released"));
    }
}
