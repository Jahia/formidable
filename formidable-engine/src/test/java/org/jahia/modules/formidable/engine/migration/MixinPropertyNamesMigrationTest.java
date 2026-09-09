package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Value;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rename rule in isolation: a prefixed property is copied under its unprefixed name
 * with its value(s) and type, then removed — on the node for the single-valued ones, on
 * the translation subnodes for the i18n option list. The Cypress spec covers the
 * end-to-end path (restart, both workspaces, idempotence, later publication).
 */
class MixinPropertyNamesMigrationTest {

    private static JCRNodeIteratorWrapper translationsOf(Node... translations) {
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        Boolean[] next = new Boolean[translations.length + 1];
        for (int i = 0; i < translations.length; i++) {
            next[i] = true;
        }
        next[translations.length] = false;
        when(iterator.hasNext()).thenReturn(next[0], java.util.Arrays.copyOfRange(next, 1, next.length));
        if (translations.length > 0) {
            when(iterator.nextNode()).thenReturn(translations[0], java.util.Arrays.copyOfRange(translations, 1, translations.length));
        }
        return iterator;
    }

    @Test
    void aPrefixedModeAndOffsetMoveUnderTheirUnprefixedNames() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper mode = mock(JCRPropertyWrapper.class);
        JCRPropertyWrapper amount = mock(JCRPropertyWrapper.class);
        JCRValueWrapper modeValue = mock(JCRValueWrapper.class);
        JCRValueWrapper amountValue = mock(JCRValueWrapper.class);
        when(field.hasProperty("fmdb:minBoundMode")).thenReturn(true);
        when(field.getProperty("fmdb:minBoundMode")).thenReturn(mode);
        when(mode.isMultiple()).thenReturn(false);
        when(mode.getValue()).thenReturn(modeValue);
        when(field.hasProperty("fmdb:minRelativeAmount")).thenReturn(true);
        when(field.getProperty("fmdb:minRelativeAmount")).thenReturn(amount);
        when(amount.isMultiple()).thenReturn(false);
        when(amount.getValue()).thenReturn(amountValue);
        JCRNodeIteratorWrapper noTranslation = translationsOf();
        when(field.getNodes("j:translation_*")).thenReturn(noTranslation);

        assertTrue(new MixinPropertyNamesMigration().migrateNode(session, field));

        verify(session).checkout(field);
        // The value object travels as is: the type (string, long, weakreference) is kept.
        verify(field).setProperty("minBoundMode", modeValue);
        verify(field).setProperty("minRelativeAmount", amountValue);
        verify(mode).remove();
        verify(amount).remove();
        verify(field, never()).setProperty(eq("maxBoundMode"), any(Value.class));
    }

    @Test
    void theTranslatedOptionListMovesOnEachTranslationNode() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        Node en = mock(Node.class);
        Node fr = mock(Node.class);
        Property enOptions = mock(Property.class);
        Property frOptions = mock(Property.class);
        Value[] english = {mock(Value.class)};
        Value[] french = {mock(Value.class), mock(Value.class)};
        when(en.hasProperty("fmdb:options")).thenReturn(true);
        when(en.getProperty("fmdb:options")).thenReturn(enOptions);
        when(enOptions.isMultiple()).thenReturn(true);
        when(enOptions.getValues()).thenReturn(english);
        when(fr.hasProperty("fmdb:options")).thenReturn(true);
        when(fr.getProperty("fmdb:options")).thenReturn(frOptions);
        when(frOptions.isMultiple()).thenReturn(true);
        when(frOptions.getValues()).thenReturn(french);
        JCRNodeIteratorWrapper translations = translationsOf(en, fr);
        when(field.getNodes("j:translation_*")).thenReturn(translations);

        assertTrue(new MixinPropertyNamesMigration().migrateNode(session, field));

        verify(session).checkout(field);
        verify(en).setProperty("options", english);
        verify(fr).setProperty("options", french);
        verify(enOptions).remove();
        verify(frOptions).remove();
    }

    @Test
    void aFieldWithoutPrefixedPropertiesIsLeftAlone() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        Node translation = mock(Node.class);
        JCRNodeIteratorWrapper translations = translationsOf(translation);
        when(field.getNodes("j:translation_*")).thenReturn(translations);

        assertFalse(new MixinPropertyNamesMigration().migrateNode(session, field));

        verify(session, never()).checkout(any(JCRNodeWrapper.class));
        verify(field, never()).setProperty(anyString(), any(Value.class));
        verify(translation, never()).setProperty(anyString(), any(Value[].class));
    }
}
