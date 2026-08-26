package org.jahia.modules.formidable.engine.permissions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormPublicationAclSyncListenerTest {

    @Test
    void listenerReceivesPublicationBorneEvents() {
        // Every trigger of this listener is publication-borne (an ACE only reaches live
        // by publishing the form), and Jahia filters publication events out of listeners
        // by default. Losing this flag silently kills the whole ACL sync: grants stop
        // propagating to results, and revocations stop being removed from them.
        FormPublicationAclSyncListener listener = new FormPublicationAclSyncListener();

        assertTrue(listener.isAvailableDuringPublish());
        assertEquals("live", listener.getWorkspace());
    }
}
