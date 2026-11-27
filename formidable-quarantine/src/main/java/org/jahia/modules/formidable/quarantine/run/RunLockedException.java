package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;

/** Another owner holds the lock of the run of the site: the run read nothing and moved nothing. */
public class RunLockedException extends QuarantineStoreException {

    private static final long serialVersionUID = 1L;

    public RunLockedException(String message) {
        super(message);
    }
}
