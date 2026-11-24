package org.jahia.test.modules.formidable.quarantine.stub;

import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/** The service of the type {@code fmdbquar:inProcessStubCheck}: an {@link InProcessStubQualifier} on the shared control. */
@Component(service = Qualifier.class, immediate = true)
public class InProcessStubCheck extends StubCheckService {

    @Reference
    public void setControl(StubControl control) {
        bind(control);
    }

    @Override
    protected Qualifier stubOf(StubControl control) {
        return new InProcessStubQualifier(control.log(), control.outage());
    }
}
