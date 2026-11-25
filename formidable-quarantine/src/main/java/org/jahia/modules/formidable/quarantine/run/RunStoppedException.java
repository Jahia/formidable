package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;

/**
 * The pass lost the lock of the run of its site before a call: its lease ended, another owner took it, or the module
 * stopped. The pass stops before the call, and keeps the report of what it already did.
 */
public class RunStoppedException extends QuarantineStoreException {

    private static final long serialVersionUID = 1L;

    public RunStoppedException(String message) {
        super(message);
    }
}
