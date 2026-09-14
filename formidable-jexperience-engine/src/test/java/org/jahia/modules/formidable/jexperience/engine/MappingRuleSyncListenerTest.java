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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How a publication event finds its form: by climbing the live tree to the nearest form, or,
 * for a removed form, by the identifier the event carries.
 */
class MappingRuleSyncListenerTest {

    private static final String FORM_PATH = "/sites/mysite/contents/contact";
    private static final String FORM_UUID = "8f7e2a10-0000-4000-8000-000000000001";

    private static Event event(int type, String path, String identifier) throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        when(event.getPath()).thenReturn(path);
        when(event.getIdentifier()).thenReturn(identifier);
        return event;
    }

    /** A live tree holding one form with a fields subtree; every other path is missing. */
    private static JCRSessionWrapper liveWith(String formPath, String formUuid) throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNode(anyString())).thenThrow(new PathNotFoundException("gone"));
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.isNodeType(MappingRuleSyncListener.FORM_NODE_TYPE)).thenReturn(true);
        when(form.getIdentifier()).thenReturn(formUuid);
        org.mockito.Mockito.doReturn(form).when(session).getNode(formPath);
        for (String child : List.of(formPath + "/fields", formPath + "/fields/firstName")) {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            org.mockito.Mockito.doReturn(node).when(session).getNode(child);
        }
        return session;
    }

    @Test
    void theSiteKeyIsTheSecondSegmentUnderSites() {
        // Verifies the scope of the rule and of jExperience's client.
        assertEquals("mysite", MappingRuleSyncListener.siteKeyOf("/sites/mysite/contents/contact/fields/f"));
        assertEquals("mysite", MappingRuleSyncListener.siteKeyOf("/sites/mysite"));
        assertNull(MappingRuleSyncListener.siteKeyOf("/modules/formidable-elements/0.5.0/contents/form"));
        assertNull(MappingRuleSyncListener.siteKeyOf("/sites/"));
    }

    @Test
    void aPropertyEventNamesTheNodeAboveIt() throws Exception {
        // Verifies the path rule shared with the identifier listener.
        assertEquals(FORM_PATH, MappingRuleSyncListener.nodePathOf(event(Event.PROPERTY_CHANGED, FORM_PATH + "/jcr:title", null)));
        assertEquals(FORM_PATH, MappingRuleSyncListener.nodePathOf(event(Event.NODE_ADDED, FORM_PATH, null)));
    }

    @Test
    void aFieldEventClimbsToItsFormAndAFormEventIsItself() throws Exception {
        // Verifies the climb: a mapping changed on a field resolves to the form above; the form's own events
        // resolve to it; a path outside a site is ignored.
        MappingRuleSyncListener listener = new MappingRuleSyncListener(mock(MappingRuleSynchronizer.class));
        JCRSessionWrapper live = liveWith(FORM_PATH, FORM_UUID);
        assertEquals(Optional.of(new MappingRuleSyncListener.Form("mysite", FORM_UUID)),
                listener.formOf(event(Event.PROPERTY_CHANGED, FORM_PATH + "/fields/firstName/jExperienceProfileProperty", null), live));
        assertEquals(Optional.of(new MappingRuleSyncListener.Form("mysite", FORM_UUID)),
                listener.formOf(event(Event.NODE_ADDED, FORM_PATH, null), live));
        assertTrue(listener.formOf(event(Event.NODE_ADDED, "/modules/x/contents/form", null), live).isEmpty());
    }

    @Test
    void aRemovedFieldResyncsItsFormAndARemovedFormIsKnownByItsIdentifier() throws Exception {
        // Verifies the two removals: a field gone with a publication still has its form above it; a form gone
        // from live (unpublished, deleted) has nothing above but the event's identifier.
        MappingRuleSyncListener listener = new MappingRuleSyncListener(mock(MappingRuleSynchronizer.class));
        JCRSessionWrapper live = liveWith(FORM_PATH, FORM_UUID);
        assertEquals(Optional.of(new MappingRuleSyncListener.Form("mysite", FORM_UUID)),
                listener.formOf(event(Event.NODE_REMOVED, FORM_PATH + "/fields/email", "field-uuid"), live));
        assertEquals(Optional.of(new MappingRuleSyncListener.Form("mysite", "removed-form-uuid")),
                listener.formOf(event(Event.NODE_REMOVED, "/sites/mysite/contents/old-form", "removed-form-uuid"), live));
    }

    @Test
    void aBatchNamesEachFormOnce() throws Exception {
        // Verifies the deduplication of a publication's burst of events: one synchronisation per form.
        MappingRuleSyncListener listener = new MappingRuleSyncListener(mock(MappingRuleSynchronizer.class));
        JCRSessionWrapper live = liveWith(FORM_PATH, FORM_UUID);
        Map<String, String> forms = listener.formsOf(List.of(
                event(Event.NODE_ADDED, FORM_PATH, null),
                event(Event.PROPERTY_ADDED, FORM_PATH + "/fields/firstName/jExperienceProfileProperty", null),
                event(Event.PROPERTY_CHANGED, FORM_PATH + "/fields/firstName/jExperienceSetStrategy", null),
                event(Event.NODE_REMOVED, "/sites/mysite/contents/old-form", "removed-form-uuid")), live);
        assertEquals(Map.of(FORM_UUID, "mysite", "removed-form-uuid", "mysite"), forms);
    }

    @Test
    void theListenerWatchesLiveDuringPublication() {
        // Verifies the wiring a publication listener needs: live workspace, available during publication,
        // both node types, scoped to the sites.
        MappingRuleSyncListener listener = new MappingRuleSyncListener(mock(MappingRuleSynchronizer.class));
        assertEquals("live", listener.getWorkspace());
        assertTrue(listener.isAvailableDuringPublish());
        assertEquals(List.of("fmdb:form", "fmdbmix:jExperienceProfileMapping"), List.of(listener.getNodeTypes()));
        assertEquals("/sites", listener.getPath());
    }
}
