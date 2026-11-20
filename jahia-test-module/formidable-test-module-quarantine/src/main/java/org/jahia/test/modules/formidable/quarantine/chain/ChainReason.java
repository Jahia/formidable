package org.jahia.test.modules.formidable.quarantine.chain;

/**
 * Why the chain stopped with a rejection or a review. A log line or an audit record names it.
 */
public enum ChainReason {

    /** A check answered {@code FAIL} for a file. Rejects. */
    CHECK_FAILED,

    /** A check answered {@code INCONCLUSIVE}; its setting {@code onInconclusive} decides. */
    INCONCLUSIVE,

    /** A check failed and may not be retried, or its setting {@code onUnavailable} is {@code reject}. */
    UNAVAILABLE,

    /** A check failed with a retryable failure, and the attempts are exhausted. */
    ATTEMPTS_EXHAUSTED,

    /** An in-process check would read a file before a remote check passed it. The list does not run. */
    IN_PROCESS_FIRST,

    /** No check of the list applied to a file: the file is withheld. */
    FILE_WITHHELD,

    /** The list of checks is empty. */
    NO_CHECK,

    /** The submission holds no file. */
    NO_FILE,

    /** No registered qualifier serves the node type of a check. The list does not run. */
    QUALIFIER_MISSING
}
