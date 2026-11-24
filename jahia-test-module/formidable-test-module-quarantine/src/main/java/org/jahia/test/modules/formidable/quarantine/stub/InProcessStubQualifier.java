package org.jahia.test.modules.formidable.quarantine.stub;

import org.jahia.test.modules.formidable.quarantine.QuarantineTypes;
import org.jahia.modules.formidable.quarantine.api.Scope;

/**
 * The stub of a check that reads the file inside the JVM, for the type {@code fmdbquar:inProcessStubCheck}. The
 * chain refuses a list where it would read a file before a remote check passed it.
 */
public final class InProcessStubQualifier extends StubQualifier {

    public InProcessStubQualifier(StubCallLog log, OutageSwitch outage) {
        super(QuarantineTypes.IN_PROCESS_STUB_CHECK, Scope.IN_PROCESS, log, outage);
    }
}
