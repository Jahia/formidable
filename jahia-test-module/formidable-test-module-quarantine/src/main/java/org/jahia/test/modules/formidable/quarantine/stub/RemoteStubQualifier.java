package org.jahia.test.modules.formidable.quarantine.stub;

import org.jahia.test.modules.formidable.quarantine.QuarantineTypes;
import org.jahia.modules.formidable.quarantine.api.Scope;

/**
 * The stub of a check that asks a remote service, for the type {@code fmdbquar:remoteStubCheck}.
 */
public final class RemoteStubQualifier extends StubQualifier {

    public RemoteStubQualifier(StubCallLog log, OutageSwitch outage) {
        super(QuarantineTypes.REMOTE_STUB_CHECK, Scope.REMOTE, log, outage);
    }
}
