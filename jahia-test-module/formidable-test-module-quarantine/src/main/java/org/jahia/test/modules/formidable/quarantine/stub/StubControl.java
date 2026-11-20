package org.jahia.test.modules.formidable.quarantine.stub;

import org.osgi.service.component.annotations.Component;

/**
 * What the two stub checks share, an OSGi service: one log of their calls, and one outage, on when the module starts.
 * The stub servlet lifts and sets the outage, and answers the log.
 */
@Component(service = StubControl.class, immediate = true)
public class StubControl {

    private final StubCallLog log = new StubCallLog();
    private final OutageSwitch outage = new OutageSwitch();

    /** @return the calls the two stubs received */
    public StubCallLog log() {
        return log;
    }

    /** @return the outage of the two stubs */
    public OutageSwitch outage() {
        return outage;
    }
}
