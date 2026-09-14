package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.PathNotFoundException;
import javax.jcr.observation.Event;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which removals matter: the head of a removed subtree only, resolved to the form above it or,
 * failing that, taken for a form by its identifier.
 */
class MappingRuleRemovalListenerTest {

    private static final String FORM_PATH = "/sites/mysite/contents/contact";
    private static final String FORM_UUID = "8f7e2a10-0000-4000-8000-000000000001";

    private static Event removed(String path, String identifier) throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(Event.NODE_REMOVED);
        when(event.getPath()).thenReturn(path);
        when(event.getIdentifier()).thenReturn(identifier);
        return event;
    }

    /** A live tree: the contents folder, one form with its fields folder; everything else is gone. */
    private static JCRSessionWrapper live() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode(anyString())).thenThrow(new PathNotFoundException("gone"));
        for (String path : List.of("/sites/mysite/contents", FORM_PATH + "/fields")) {
            doReturn(mock(JCRNodeWrapper.class)).when(session).getNode(path);
        }
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.isNodeType(MappingRuleSyncListener.FORM_NODE_TYPE)).thenReturn(true);
        when(form.getIdentifier()).thenReturn(FORM_UUID);
        doReturn(form).when(session).getNode(FORM_PATH);
        return session;
    }

    @Test
    void aRemovedFormIsSynchronisedByItsIdentifier() throws Exception {
        // Verifies the published deletion of a form: its parent folder is still there, nothing above is a
        // form, so the event's identifier names the form whose rule must go.
        MappingRuleRemovalListener listener = new MappingRuleRemovalListener(mock(MappingRuleSynchronizer.class));
        assertEquals(Optional.of(new MappingRuleSyncListener.Form("mysite", "deleted-form-uuid")),
                listener.formOf(removed("/sites/mysite/contents/old-form", "deleted-form-uuid"), live()));
    }

    @Test
    void aRemovedFieldResynchronisesTheFormAboveIt() throws Exception {
        // Verifies a field removed from a still published form: the form above is the one to resynchronise.
        MappingRuleRemovalListener listener = new MappingRuleRemovalListener(mock(MappingRuleSynchronizer.class));
        assertEquals(Optional.of(new MappingRuleSyncListener.Form("mysite", FORM_UUID)),
                listener.formOf(removed(FORM_PATH + "/fields/email", "field-uuid"), live()));
    }

    @Test
    void aNodeGoneWithItsParentIsIgnored() throws Exception {
        // Verifies the cost bound: children of a removed subtree, whose parent is gone too, and nodes outside
        // a site trigger nothing — one lookup per removed subtree head at most.
        MappingRuleRemovalListener listener = new MappingRuleRemovalListener(mock(MappingRuleSynchronizer.class));
        assertTrue(listener.formOf(removed("/sites/mysite/contents/old-form/fields/email", "child-uuid"), live()).isEmpty());
        assertTrue(listener.formOf(removed("/modules/x/contents/form", "module-uuid"), live()).isEmpty());
        assertTrue(listener.formOf(removed("/sites", null), live()).isEmpty());
    }

    @Test
    void aBatchNamesEachFormOnce() throws Exception {
        // Verifies the deduplication over a deletion's burst of removals.
        MappingRuleRemovalListener listener = new MappingRuleRemovalListener(mock(MappingRuleSynchronizer.class));
        Map<String, String> forms = listener.formsOf(List.of(
                removed("/sites/mysite/contents/old-form", "deleted-form-uuid"),
                removed("/sites/mysite/contents/old-form/fields", "fields-uuid"),
                removed("/sites/mysite/contents/old-form/fields/email", "field-uuid"),
                removed(FORM_PATH + "/fields/phone", "phone-uuid")), live());
        assertEquals(Map.of("deleted-form-uuid", "mysite", FORM_UUID, "mysite"), forms);
    }

    @Test
    void theListenerWatchesRemovalsInLiveWithoutATypeFilter() {
        // Verifies the wiring that makes a deleted form visible: live, during publication, removals only,
        // no node types (Jahia cannot resolve a deleted node's types), scoped to the sites.
        MappingRuleRemovalListener listener = new MappingRuleRemovalListener(mock(MappingRuleSynchronizer.class));
        assertEquals("live", listener.getWorkspace());
        assertTrue(listener.isAvailableDuringPublish());
        assertEquals(Event.NODE_REMOVED, listener.getEventTypes());
        assertNull(listener.getNodeTypes());
        assertEquals("/sites", listener.getPath());
    }
}
