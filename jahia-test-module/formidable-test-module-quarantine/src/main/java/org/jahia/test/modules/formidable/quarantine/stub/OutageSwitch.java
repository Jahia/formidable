package org.jahia.test.modules.formidable.quarantine.stub;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The outage of the stub checks. While it is on, a file marked {@code DOWN} gets a retryable failure; once a spec
 * lifts it, the same file passes. It is on when the module starts.
 */
public final class OutageSwitch {

    private final AtomicBoolean down;

    /** An outage that is on. */
    public OutageSwitch() {
        this(true);
    }

    /** @param down true for an outage that is on */
    public OutageSwitch(boolean down) {
        this.down = new AtomicBoolean(down);
    }

    /** @return true while the outage is on */
    public boolean isDown() {
        return down.get();
    }

    /** Lifts the outage. */
    public void lift() {
        down.set(false);
    }

    /** Sets the outage again. */
    public void set() {
        down.set(true);
    }
}
