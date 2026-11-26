package org.jahia.modules.formidable.quarantine.api;

/**
 * A pass of the run that stopped before its end, because it lost the lock of the run of its site: its lease ended,
 * another run took the lock, or the module stopped. The submissions it processed keep their new state, and the report
 * names them; the next pass takes the others.
 */
public class QuarantineRunStoppedException extends QuarantineRunException {

    private static final long serialVersionUID = 1L;

    private final transient RunReport report;

    public QuarantineRunStoppedException(String message, RunReport report, Throwable cause) {
        super(message, cause);
        this.report = report;
    }

    /** @return the submissions the pass processed before it stopped */
    public RunReport report() {
        return report;
    }
}
