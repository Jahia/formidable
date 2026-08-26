package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The stray shape (a fieldKey stored on a jnt:translation node, where it has no
 * definition) cannot be produced through the wrapper API anymore, so the cleanup is
 * covered here with mocks; the content-integrity Cypress specs cover the end-to-end
 * absence of the value on freshly created forms.
 */
class TranslationFieldKeyCleanupTest {

    @Test
    void removesTheStrayKeyFromEveryTranslationThatCarriesOne() throws Exception {
        Node en = translation(true);
        Node fr = translation(true);
        JCRNodeWrapper element = elementWithTranslations(en, fr);

        assertEquals(2, TranslationFieldKeyCleanup.cleanNode(element));
        verify(en.getProperty("fieldKey")).remove();
        verify(fr.getProperty("fieldKey")).remove();
    }

    @Test
    void leavesCleanTranslationsAlone() throws Exception {
        Node en = translation(false);
        Node fr = translation(true);
        JCRNodeWrapper element = elementWithTranslations(en, fr);

        assertEquals(1, TranslationFieldKeyCleanup.cleanNode(element));
        verify(en, never()).getProperty("fieldKey");
    }

    @Test
    void isANoOpOnAnElementWithoutTranslations() throws Exception {
        assertEquals(0, TranslationFieldKeyCleanup.cleanNode(elementWithTranslations()));
    }

    private static Node translation(boolean carriesKey) throws Exception {
        Node translation = mock(Node.class);
        when(translation.hasProperty("fieldKey")).thenReturn(carriesKey);
        if (carriesKey) {
            when(translation.getProperty("fieldKey")).thenReturn(mock(Property.class));
        }
        return translation;
    }

    private static JCRNodeWrapper elementWithTranslations(Node... translations) throws Exception {
        JCRNodeWrapper element = mock(JCRNodeWrapper.class);
        Iterator<Node> iterator = List.of(translations).iterator();
        NodeIterator nodes = mock(NodeIterator.class);
        when(nodes.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(nodes.nextNode()).thenAnswer(invocation -> iterator.next());
        when(element.getI18Ns()).thenReturn(nodes);
        return element;
    }
}
