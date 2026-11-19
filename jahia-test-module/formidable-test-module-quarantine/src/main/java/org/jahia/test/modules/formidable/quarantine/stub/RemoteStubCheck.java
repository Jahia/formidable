package org.jahia.test.modules.formidable.quarantine.stub;

import org.jahia.test.modules.formidable.quarantine.check.Qualifier;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/** The service of the type {@code fmdbquar:remoteStubCheck}: a {@link RemoteStubQualifier} on the shared control. */
@Component(service = Qualifier.class, immediate = true)
public class RemoteStubCheck extends StubCheckService {

    @Reference
    public void setControl(StubControl control) {
        bind(control);
    }

    @Override
    protected Qualifier stubOf(StubControl control) {
        return new RemoteStubQualifier(control.log(), control.outage());
    }
}
