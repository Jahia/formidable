package org.jahia.test.modules.formidable.quarantine.chain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jahia.test.modules.formidable.quarantine.check.OnInconclusive;
import org.jahia.test.modules.formidable.quarantine.check.OnUnavailable;
import org.jahia.test.modules.formidable.quarantine.check.Outcome;
import org.jahia.test.modules.formidable.quarantine.check.Scope;
import org.junit.jupiter.api.Test;

import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.TEXT;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.broken;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.check;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.down;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.failed;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.inconclusive;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.key;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.passed;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.pending;
import static org.jahia.test.modules.formidable.quarantine.chain.ChainFixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every combination of recorded outcomes, for two files and two checks, under every pair of settings: no path gives
 * a release without a {@code PASS} of every check on every file, a check never receives a file before the earlier
 * check passed it, and a {@code FAIL} of the first check always rejects.
 */
class CheckChainFailsClosedTest {

    private static final int MAX_ATTEMPTS = 2;

    /** null stands for "no outcome recorded yet". */
    private static final List<RecordedOutcome> OUTCOMES = new ArrayList<>(List.of(
            pending("t"), passed(), failed(), inconclusive(), down(0, null), down(MAX_ATTEMPTS, null), broken()));

    static {
        OUTCOMES.add(null);
    }

    private final CheckChain chain = new CheckChain(false, MAX_ATTEMPTS);
    private final List<ChainFixtures.TestFile> files = List.of(text("f1"), text("f2"));

    @Test
    void noPathReleasesWithoutAPassOfEveryCheckOnEveryFile() {
        int decisions = 0;
        for (OnInconclusive onInconclusive : OnInconclusive.values()) {
            for (OnUnavailable onUnavailable : OnUnavailable.values()) {
                List<ChainCheck> checks = List.of(
                        check("a", Scope.REMOTE, TEXT, onInconclusive, onUnavailable),
                        check("b", Scope.REMOTE, TEXT, onInconclusive, onUnavailable));
                decisions += everyCombination(checks);
            }
        }
        assertEquals(4 * pow(OUTCOMES.size(), 4), decisions);
    }

    private int everyCombination(List<ChainCheck> checks) {
        int count = 0;
        int size = OUTCOMES.size();
        for (int combination = 0; combination < pow(size, 4); combination++) {
            Map<OutcomeKey, RecordedOutcome> recorded = new HashMap<>();
            int rest = combination;
            for (ChainFixtures.TestFile file : files) {
                for (ChainCheck check : checks) {
                    RecordedOutcome outcome = OUTCOMES.get(rest % size);
                    rest /= size;
                    if (outcome != null) {
                        recorded.put(key(file, check), outcome);
                    }
                }
            }
            verify(checks, recorded, chain.decide(checks, files, recorded));
            count++;
        }
        return count;
    }

    private void verify(List<ChainCheck> checks, Map<OutcomeKey, RecordedOutcome> recorded, ChainDecision decision) {
        String context = recorded + " -> " + decision;
        if (decision instanceof ChainDecision.Release) {
            for (ChainFixtures.TestFile file : files) {
                for (ChainCheck check : checks) {
                    assertTrue(isPass(recorded.get(key(file, check))), "release without a pass: " + context);
                }
            }
        }
        if (decision instanceof ChainDecision.Proceed proceed) {
            for (Step step : proceed.steps()) {
                if (step.check() == checks.get(1)) {
                    assertTrue(isPass(recorded.get(key(step.file(), checks.get(0)))),
                            "the second check before the first passed: " + context);
                }
            }
        }
        for (ChainFixtures.TestFile file : files) {
            if (isFail(recorded.get(key(file, checks.get(0))))) {
                assertInstanceOf(ChainDecision.Reject.class, decision, "a fail that does not reject: " + context);
            }
        }
    }

    private static boolean isPass(RecordedOutcome recorded) {
        return recorded != null && recorded.outcome() instanceof Outcome.Result result
                && result.verdict() == Outcome.Verdict.PASS;
    }

    private static boolean isFail(RecordedOutcome recorded) {
        return recorded != null && recorded.outcome() instanceof Outcome.Result result
                && result.verdict() == Outcome.Verdict.FAIL;
    }

    private static int pow(int base, int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }
}
