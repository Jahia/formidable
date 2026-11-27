package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.api.RunReport;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;

/**
 * A pass that lost the lock of the run of its site and stopped before the next submission or call. It carries the
 * report of the submissions the pass processed before the stop.
 */
public class PassStoppedException extends QuarantineStoreException {

    private static final long serialVersionUID = 1L;

    private final transient RunReport report;

    public PassStoppedException(String message, RunReport report) {
        super(message);
        this.report = report;
    }

    /** @return the submissions processed before the stop */
    public RunReport report() {
        return report;
    }
}
