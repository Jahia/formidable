package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Value;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The alignment contract on the i18n storage (j:translation_* subnodes): the
 * default language's values, order and count are the identity, a translation
 * only keeps its own label and selected flag for values it already carries.
 */
class ManualOptionsLanguageSyncTest {

    private static String option(String value, String label) {
        return option(value, label, false);
    }

    private static String option(String value, String label, boolean selected) {
        return "{\"value\":\"" + value + "\",\"label\":\"" + label + "\",\"selected\":" + selected + "}";
    }

    @Test
    void divergentTranslationIsRealignedOnTheMasterStructure() throws Exception {
        // Master (en): a, b. Translation (fr): b (own label), c (a value the master
        // does not know). Alignment: a copied from master, fr's own b kept, c dropped.
        Node master = translation("en", option("a", "Alpha"), option("b", "Bee"));
        String frB = option("b", "Bé");
        Node fr = translation("fr", frB, option("c", "Cé"));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertTrue(ManualOptionsLanguageSync.sync(field));
        verify(fr).setProperty(eq("fmdb:options"),
                eq(new String[]{option("a", "Alpha"), frB}));
    }

    @Test
    void alignedTranslationIsLeftAlone() throws Exception {
        // Same values in the same order: the translated labels are that language's
        // own business, nothing is rewritten (which also terminates the observation
        // loop the sync's writes re-enter).
        Node master = translation("en", option("a", "Alpha"), option("b", "Bee"));
        Node fr = translation("fr", option("a", "Alfa"), option("b", "Bé"));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertFalse(ManualOptionsLanguageSync.sync(field));
        verify(fr, never()).setProperty(eq("fmdb:options"), any(String[].class));
    }

    @Test
    void defaultSelectionFollowsTheMaster() throws Exception {
        // The default selection is form behavior, not content: it travels with the
        // value, so the master's flag wins while the language's label survives.
        Node master = translation("en", option("a", "Alpha", true), option("b", "Bee", false));
        Node fr = translation("fr", option("a", "Alfa", false), option("b", "B\u00e9", true));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertTrue(ManualOptionsLanguageSync.sync(field));
        verify(fr).setProperty(eq("fmdb:options"),
                eq(new String[]{option("a", "Alfa", true), option("b", "B\u00e9", false)}));
    }

    @Test
    void sameValueEntriesPairPositionally() throws Exception {
        // Two master rows sharing a value — the ordinary editing state of freshly
        // added rows whose value is still empty behaves the same — must each keep
        // their own translation, never collapse onto the first one.
        Node master = translation("en", option("a", "Alpha 1"), option("a", "Alpha 2"), option("b", "Bee"));
        String frA1 = option("a", "A-fr-1");
        String frA2 = option("a", "A-fr-2");
        Node fr = translation("fr", frA1, frA2);

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertTrue(ManualOptionsLanguageSync.sync(field));
        verify(fr).setProperty(eq("fmdb:options"),
                eq(new String[]{frA1, frA2, option("b", "Bee")}));
    }

    @Test
    void anEmptyMasterIsSeededFromTheFirstAuthoredLanguage() throws Exception {
        // A field authored in a non-default language only: its entries become the
        // master right away (values as the identity, labels as the translation
        // starting point), so a later default-language edit never opens on an empty
        // mandatory list whose improvised values would erase this authoring.
        Node master = translation("en");
        String frA = option("a", "Alfa");
        Node fr = translation("fr", frA);

        JCRNodeWrapper field = fieldNode("en", master, fr);
        when(field.getOrCreateI18N(org.jahia.utils.LanguageCodeConverters.languageCodeToLocale("en")))
                .thenReturn(master);

        assertTrue(ManualOptionsLanguageSync.sync(field));
        verify(master).setProperty(eq("fmdb:options"), eq(new String[]{frA}));
        verify(fr, never()).setProperty(eq("fmdb:options"), any(String[].class));
    }

    @Test
    void aLanguageNobodyTranslatedIsLeftAlone() throws Exception {
        // A site language with no j:translation_* subnode stays untouched: starting
        // a translation is the contributor's gesture (authoring, or Content Editor's
        // "Copy a language"), never a server-side side effect of someone else's save.
        Node master = translation("en", option("a", "Alpha"), option("b", "Bee", true));

        JCRNodeWrapper field = fieldNode("en", java.util.Set.of("en", "fr"), master);

        assertFalse(ManualOptionsLanguageSync.sync(field));
        verify(field, never()).getOrCreateI18N(any());
    }

    @Test
    void valuelessRowsAreCleanedNotAdopted() throws Exception {
        // An "add" clicked outside the default language can only save valueless
        // rows (no value is typable there): noise, not a translation. The sync
        // removes the property so the language stays untranslated, instead of
        // treating it as an existing translation and feeding it the master.
        Node master = translation("en", option("a", "Alpha"), option("b", "Bee"));
        Node fr = translation("fr", option("", ""));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertTrue(ManualOptionsLanguageSync.sync(field));
        verify(fr).setProperty("fmdb:options", (String[]) null);
        verify(fr, never()).setProperty(eq("fmdb:options"), any(String[].class));
    }

    @Test
    void valuelessRowsNeverSeedTheMaster() throws Exception {
        // A field whose only entries are valueless rows has no identity anywhere:
        // nothing seeds, nothing aligns.
        Node master = translation("en");
        Node fr = translation("fr", option("", "Junk label"));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertFalse(ManualOptionsLanguageSync.sync(field));
        verify(master, never()).setProperty(eq("fmdb:options"), any(String[].class));
    }

    @Test
    void aFieldWithoutAnyOptionsAlignsNothing() throws Exception {
        // Nothing authored anywhere: no identity exists yet, nothing to seed or align.
        Node master = translation("en");
        Node fr = translation("fr");

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertFalse(ManualOptionsLanguageSync.sync(field));
        verify(fr, never()).setProperty(eq("fmdb:options"), any(String[].class));
        verify(master, never()).setProperty(eq("fmdb:options"), any(String[].class));
    }

    private static JCRNodeWrapper fieldNode(String defaultLanguage, Node... translations) throws Exception {
        java.util.Set<String> siteLanguages = new java.util.HashSet<>();
        siteLanguages.add(defaultLanguage);
        for (Node translation : translations) {
            siteLanguages.add(translation.getProperty("jcr:language").getString());
        }

        return fieldNode(defaultLanguage, siteLanguages, translations);
    }

    private static JCRNodeWrapper fieldNode(String defaultLanguage, java.util.Set<String> siteLanguages,
            Node... translations) throws Exception {
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.isNodeType("fmdbmix:manualOptions")).thenReturn(true);
        when(field.getPath()).thenReturn("/sites/test/contents/form/fields/choice");

        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getDefaultLanguage()).thenReturn(defaultLanguage);
        when(site.getLanguages()).thenReturn(siteLanguages);
        when(field.getResolveSite()).thenReturn(site);

        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        Boolean[] remaining = new Boolean[translations.length];
        for (int i = 0; i < translations.length; i++) {
            remaining[i] = i < translations.length - 1;
        }

        when(iterator.hasNext()).thenReturn(translations.length > 0,
                java.util.Arrays.copyOf(remaining, remaining.length));
        if (translations.length > 0) {
            when(iterator.nextNode()).thenReturn(translations[0],
                    java.util.Arrays.copyOfRange(translations, 1, translations.length));
        }

        when(field.getNodes("j:translation_*")).thenReturn(iterator);
        return field;
    }

    private static Node translation(String language, String... options) throws Exception {
        Node translation = mock(Node.class);
        when(translation.getName()).thenReturn("j:translation_" + language);
        Property languageProperty = mock(Property.class);
        when(languageProperty.getString()).thenReturn(language);
        when(translation.hasProperty("jcr:language")).thenReturn(true);
        when(translation.getProperty("jcr:language")).thenReturn(languageProperty);

        if (options.length == 0) {
            when(translation.hasProperty("fmdb:options")).thenReturn(false);
            return translation;
        }

        Value[] values = new Value[options.length];
        for (int i = 0; i < options.length; i++) {
            Value value = mock(Value.class);
            when(value.getString()).thenReturn(options[i]);
            values[i] = value;
        }

        Property optionsProperty = mock(Property.class);
        when(optionsProperty.getValues()).thenReturn(values);
        when(translation.hasProperty("fmdb:options")).thenReturn(true);
        when(translation.getProperty("fmdb:options")).thenReturn(optionsProperty);
        return translation;
    }
}
