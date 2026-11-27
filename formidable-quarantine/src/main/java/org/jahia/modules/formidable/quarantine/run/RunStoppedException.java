package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;

/**
 * A holder of the lock of the run of a site lost it before a call: its lease ended, another owner took it, or the
 * module stopped. The holder stops before the call. A pass of the run stops before its next submission or call, and
 * turns it into a {@link PassStoppedException} with the report of what it already did. A release or a resume of a
 * manager stops before its actions after release, which do not run.
 */
public class RunStoppedException extends QuarantineStoreException {

    private static final long serialVersionUID = 1L;

    public RunStoppedException(String message) {
        super(message);
    }
}
