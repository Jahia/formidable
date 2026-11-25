package org.jahia.modules.formidable.quarantine.chain;

import java.util.List;
import java.util.Objects;

/**
 * What the chain decides for a submission: the calls to make now, or a terminal decision.
 */
public sealed interface ChainDecision
        permits ChainDecision.Proceed, ChainDecision.Release, ChainDecision.Reject, ChainDecision.Review {

    /** Every check that applies passed every file. */
    Release RELEASE = new Release();

    /**
     * The calls to make now, at most one per file: the first check of that file that has not passed it yet.
     *
     * @param steps the calls, in the order of the files; never empty
     */
    record Proceed(List<Step> steps) implements ChainDecision {
        public Proceed {
            steps = List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("A decision to proceed needs a step");
            }
        }
    }

    /** Release the submission. */
    record Release() implements ChainDecision {
    }

    /**
     * Reject the submission. Nothing more runs.
     *
     * @param reason    why
     * @param sha256    the SHA-256 of the file the reason is about, or null for the whole list
     * @param checkName the name of the check the reason is about, or null for the whole list
     */
    record Reject(ChainReason reason, String sha256, String checkName) implements ChainDecision {
        public Reject {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Stop at {@code needsReview}: a manager decides. Nothing more runs.
     *
     * @param reason    why
     * @param sha256    the SHA-256 of the file the reason is about, or null for the whole list
     * @param checkName the name of the check the reason is about, or null for the whole list
     */
    record Review(ChainReason reason, String sha256, String checkName) implements ChainDecision {
        public Review {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
