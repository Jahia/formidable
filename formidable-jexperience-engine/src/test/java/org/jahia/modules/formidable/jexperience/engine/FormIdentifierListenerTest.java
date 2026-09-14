package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The stamping rule in isolation, on mocked nodes: the identifier a form carries must be the one
 * its own UUID gives — a copy re-derives — and the start-up pass saves form by form.
 */
class FormIdentifierListenerTest {

    private static final String UUID_A = "e6b7c3ac-cc83-4457-97fc-97789e5478eb";
    private static final String UUID_B = "c33f43a1-12e1-44e4-a589-110a67c9b093";

    private static JCRNodeWrapper form(String uuid, String storedIdentifier, boolean hasMixin) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.isNodeType(FormIdentifierListener.FORM_NODE_TYPE)).thenReturn(true);
        when(node.isNodeType(FormIdentifier.FORM_MIXIN)).thenReturn(hasMixin);
        when(node.getIdentifier()).thenReturn(uuid);
        when(node.getPropertyAsString(FormIdentifier.PROPERTY)).thenReturn(storedIdentifier);
        when(node.getName()).thenReturn("form-" + uuid.substring(0, 8));
        when(node.getPath()).thenReturn("/sites/site/contents/" + uuid);
        return node;
    }

    private static NodeIterator formsOf(JCRNodeWrapper... forms) {
        NodeIterator iterator = mock(NodeIterator.class);
        Boolean[] next = new Boolean[forms.length + 1];
        Arrays.fill(next, 0, forms.length, true);
        next[forms.length] = false;
        when(iterator.hasNext()).thenReturn(next[0], Arrays.copyOfRange(next, 1, next.length));
        if (forms.length > 0) {
            when(iterator.nextNode()).thenReturn(forms[0], Arrays.copyOfRange(forms, 1, forms.length));
        }
        return iterator;
    }

    @Test
    void aFormWithoutTheIdentifierGetsTheMixinAndItsOwnIdentifier() throws Exception {
        // Verifies the nominal stamp: mixin added, identifier derived from the node's UUID.
        JCRNodeWrapper form = form(UUID_A, null, false);
        assertTrue(FormIdentifierListener.stamp(form));
        verify(form).addMixin(FormIdentifier.FORM_MIXIN);
        verify(form).setProperty(FormIdentifier.PROPERTY, FormIdentifier.of(UUID_A));
    }

    @Test
    void aFormCarryingItsOwnIdentifierIsLeftAlone() throws Exception {
        // Verifies idempotence: the write's own event, and every later edit, change nothing.
        JCRNodeWrapper form = form(UUID_A, FormIdentifier.of(UUID_A), true);
        assertFalse(FormIdentifierListener.stamp(form));
        verify(form, never()).addMixin(anyString());
        verify(form, never()).setProperty(anyString(), anyString());
    }

    @Test
    void aCopiedFormIsReStampedWithItsOwnUuid() throws Exception {
        // Verifies the copy case: a JCR copy keeps the source's mixin and identifier on a node with a new
        // UUID, and two forms must never share one jCustomer identity.
        JCRNodeWrapper copy = form(UUID_B, FormIdentifier.of(UUID_A), true);
        assertTrue(FormIdentifierListener.stamp(copy));
        verify(copy, never()).addMixin(anyString());
        verify(copy).setProperty(FormIdentifier.PROPERTY, FormIdentifier.of(UUID_B));
    }

    @Test
    void aNodeThatIsNotAFormIsNeverStamped() throws Exception {
        // Verifies the type guard, for the nodes the observation lets through.
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.isNodeType(FormIdentifierListener.FORM_NODE_TYPE)).thenReturn(false);
        assertFalse(FormIdentifierListener.stamp(node));
        verify(node, never()).setProperty(anyString(), anyString());
    }

    @Test
    void anEventOnATranslationSubnodeClimbsToTheForm() throws Exception {
        // Verifies that an i18n title edit, an event on j:translation_<lang>, stamps the form above it.
        JCRNodeWrapper form = form(UUID_A, null, false);
        JCRNodeWrapper translation = mock(JCRNodeWrapper.class);
        when(translation.getName()).thenReturn("j:translation_en");
        when(translation.getParent()).thenReturn(form);
        assertSame(form, FormIdentifierListener.formOf(translation));
        assertSame(form, FormIdentifierListener.formOf(form));
    }

    @Test
    void aPropertyEventNamesTheNodeOneLevelUp() throws Exception {
        // Verifies the path rule: a node event is about its path, a property event about the node above.
        Event added = mock(Event.class);
        when(added.getType()).thenReturn(Event.NODE_ADDED);
        when(added.getPath()).thenReturn("/sites/site/contents/form");
        assertEquals("/sites/site/contents/form", FormIdentifierListener.nodePathOf(added));
        Event changed = mock(Event.class);
        when(changed.getType()).thenReturn(Event.PROPERTY_CHANGED);
        when(changed.getPath()).thenReturn("/sites/site/contents/form/jcr:title");
        assertEquals("/sites/site/contents/form", FormIdentifierListener.nodePathOf(changed));
    }

    @Test
    void theStartUpPassSavesEachFormAndSkipsTheOneThatFails() throws Exception {
        // Verifies the per-form save: a form that cannot be saved is discarded and logged, the forms
        // before and after it keep their identifier, and the count says how many were stamped.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper first = form(UUID_A, null, false);
        JCRNodeWrapper locked = form(UUID_B, null, false);
        when(locked.setProperty(eq(FormIdentifier.PROPERTY), anyString())).thenThrow(new RepositoryException("locked"));
        JCRNodeWrapper alreadyStamped = form("4a1b2c3d-0000-4000-8000-000000000004", FormIdentifier.of("4a1b2c3d-0000-4000-8000-000000000004"), true);
        JCRNodeWrapper third = form("3f9c1d20-0000-4000-8000-000000000003", FormIdentifier.of(UUID_A), true);

        int stamped = new FormIdentifierListener().stampAll(session, formsOf(first, locked, alreadyStamped, third));

        // four forms: two stamped, one failed, one already right — the count says two, as the log line will
        assertEquals(2, stamped);
        verify(session, times(2)).save();
        verify(session).refresh(false);
        verify(alreadyStamped, never()).setProperty(anyString(), anyString());
        verify(third).setProperty(FormIdentifier.PROPERTY, FormIdentifier.of("3f9c1d20-0000-4000-8000-000000000003"));
    }

    @Test
    void theStartUpPassAndTheObservationAreScopedToTheSites() {
        // Verifies the scope: editorial content under /sites, never the module-bundled nodes under /modules.
        assertTrue(FormIdentifierListener.FORMS_QUERY.contains("ISDESCENDANTNODE('/sites')"), FormIdentifierListener.FORMS_QUERY);
        FormIdentifierListener listener = new FormIdentifierListener();
        assertEquals("/sites", listener.getPath());
        assertEquals("default", listener.getWorkspace());
    }
}
