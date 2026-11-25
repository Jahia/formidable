package org.jahia.test.modules.formidable.quarantine.stub;

import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;

/**
 * A stub check as an OSGi service: it delegates to the stub that its subclass builds on the shared
 * {@link StubControl}. The control is a static reference, so it is bound before the service is registered.
 */
abstract class StubCheckService implements Qualifier {

    private Qualifier delegate;

    /** Builds the stub on the shared log and outage. */
    protected abstract Qualifier stubOf(StubControl control);

    void bind(StubControl control) {
        this.delegate = stubOf(control);
    }

    @Override
    public String getNodeType() {
        return delegate.getNodeType();
    }

    @Override
    public Scope scope() {
        return delegate.scope();
    }

    @Override
    public boolean appliesTo(QuarantinedFile file, CheckSettings settings) {
        return delegate.appliesTo(file, settings);
    }

    @Override
    public Submitted submit(QuarantinedFile file, CheckSettings settings) {
        return delegate.submit(file, settings);
    }

    @Override
    public Outcome poll(String ticket) {
        return delegate.poll(ticket);
    }
}
