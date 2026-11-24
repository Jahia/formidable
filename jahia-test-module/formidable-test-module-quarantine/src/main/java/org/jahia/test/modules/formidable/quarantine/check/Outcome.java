package org.jahia.test.modules.formidable.quarantine.check;

import java.util.Objects;

/**
 * What a check answers for one file: still {@link Pending}, a {@link Result}, or {@link Failed}.
 */
public sealed interface Outcome permits Outcome.Pending, Outcome.Result, Outcome.Failed {

    /** The verdict of a check on a file. */
    enum Verdict {
        PASS,
        FAIL,
        INCONCLUSIVE
    }

    /** The engine has not answered yet: the ticket is polled later. */
    Pending PENDING = new Pending();

    /** @return the result {@code PASS} with the reason code and the detail given */
    static Result pass(String reasonCode, String detail) {
        return new Result(Verdict.PASS, reasonCode, detail);
    }

    /** @return the result {@code FAIL} with the reason code and the detail given */
    static Result fail(String reasonCode, String detail) {
        return new Result(Verdict.FAIL, reasonCode, detail);
    }

    /** @return the result {@code INCONCLUSIVE} with the reason code and the detail given */
    static Result inconclusive(String reasonCode, String detail) {
        return new Result(Verdict.INCONCLUSIVE, reasonCode, detail);
    }

    /** The engine has not answered yet. */
    record Pending() implements Outcome {
    }

    /**
     * The answer of the engine.
     *
     * @param verdict    the verdict
     * @param reasonCode a short code that names the reason, such as {@code stub.marker}; never a value of the file
     * @param detail     a short text for the audit, or null
     */
    record Result(Verdict verdict, String reasonCode, String detail) implements Outcome {
        public Result {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
    }

    /**
     * The check could not answer.
     *
     * @param retryable true when a later attempt may succeed, such as an engine that is down
     * @param reason    a short code that names the reason
     */
    record Failed(boolean retryable, String reason) implements Outcome {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
