package org.jahia.modules.formidable.engine.options;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;

import static org.jahia.modules.formidable.engine.options.ManualOptionsFixtures.fieldNode;
import static org.jahia.modules.formidable.engine.options.ManualOptionsFixtures.markMigrated;
import static org.jahia.modules.formidable.engine.options.ManualOptionsFixtures.option;
import static org.jahia.modules.formidable.engine.options.ManualOptionsFixtures.translation;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a translated form RENDERS: the default language's values, order and default
 * selections with the rendered language's own labels — the read-side counterpart of
 * the save-time re-alignment, so a rendered option can never be a value the
 * submission validation rejects. An entry nobody translated follows the SITE's rule
 * for untranslated content, hence the isMixLanguagesActive stubbing below.
 */
class ManualOptionsDisplayServiceTest {

    private final ManualOptionsDisplayService service = new ManualOptionsDisplayService();

    @Test
    void aTranslationRendersTheMasterIdentityWithItsOwnLabels() throws Exception {
        // Live can hold 'fr' at an older generation than 'en' (publication is per
        // language): 'mint' is gone from the identity, 'chocolate' keeps its French
        // label, 'vanilla' arrives from the master.
        Node master = translation("en", option("vanilla", "Vanilla"), option("chocolate", "Chocolate"));
        Node fr = translation("fr", option("mint", "Menthe"), option("chocolate", "Chocolat"));

        JCRNodeWrapper field = fieldNode("en", master, fr);
        when(field.getResolveSite().isMixLanguagesActive()).thenReturn(true);

        assertArrayEquals(
                new String[]{option("vanilla", "Vanilla"), option("chocolate", "Chocolat")},
                service.forDisplay(field, "fr"));
    }

    @Test
    void aMigratedDivergentTranslationRendersItsOwnLabelsByPosition() throws Exception {
        // Pre-realign 0.3-migrated state: the French list still translates the VALUES
        // (rouge/vert facing red/green). The identity values render — validation only
        // accepts those — but labelled with the language's own translations, row for
        // row. Without the pairing, French rendered the master's words, or NOTHING at
        // all on a site that does not replace untranslated content: a dead choice
        // field, unsubmittable when required.
        Node master = translation("en", option("red", "Red"), option("green", "Green"));
        Node fr = translation("fr", option("rouge", "Rouge"), option("vert", "Vert"));

        JCRNodeWrapper field = markMigrated(fieldNode("en", master, fr));
        when(field.getResolveSite().isMixLanguagesActive()).thenReturn(false);

        assertArrayEquals(
                new String[]{option("red", "Rouge"), option("green", "Vert")},
                service.forDisplay(field, "fr"));
    }

    @Test
    void aNativeReplacedFieldIsNotRenderedWithPositionalLabels() throws Exception {
        // No marker: a native field whose values were replaced renders the identity
        // with blank/dropped labels (untranslated), never the old French words mapped
        // onto the new values. Site does not replace untranslated content, so the
        // unlabelled entries drop.
        Node master = translation("en", option("blue", "Blue"), option("yellow", "Yellow"));
        Node fr = translation("fr", option("red", "Rouge"), option("green", "Vert"));

        JCRNodeWrapper field = fieldNode("en", master, fr);
        when(field.getResolveSite().isMixLanguagesActive()).thenReturn(false);

        assertArrayEquals(new String[]{}, service.forDisplay(field, "fr"));
    }

    @Test
    void theDefaultSelectionRenderedIsTheMasters() throws Exception {
        // Which option comes pre-selected is form behavior, not editorial content.
        Node master = translation("en", option("a", "Alpha", true), option("b", "Bee", false));
        Node fr = translation("fr", option("a", "Alfa", false), option("b", "Bé", true));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertArrayEquals(
                new String[]{option("a", "Alfa", true), option("b", "Bé", false)},
                service.forDisplay(field, "fr"));
    }

    @Test
    void anUntranslatedLanguageRendersTheMasterEntries() throws Exception {
        // Nothing translated here yet, and the site replaces untranslated content with
        // the default language's: the master's entries are rendered as they stand.
        Node master = translation("en", option("a", "Alpha"));

        JCRNodeWrapper field = fieldNode("en", master);
        when(field.getResolveSite().isMixLanguagesActive()).thenReturn(true);

        assertArrayEquals(new String[]{option("a", "Alpha")}, service.forDisplay(field, "fr"));
    }

    @Test
    void anUntranslatedLanguageRendersNothingWhenTheSiteHidesUntranslatedContent() throws Exception {
        // Replacing is off: the site asked for untranslated content to stay invisible.
        // A field nobody translated then offers no choice at all — the same verdict
        // the site pronounces on any other untranslated content.
        Node master = translation("en", option("a", "Alpha"));

        JCRNodeWrapper field = fieldNode("en", master);

        assertArrayEquals(new String[0], service.forDisplay(field, "fr"));
    }

    @Test
    void anEntryLeftBlankRendersTheMasterLabel() throws Exception {
        // What the save-time feeding stores for an entry nobody translated yet: the
        // identity with an empty label. A form must never offer a blank choice, so the
        // fallback is per entry — 'a' shows its French label, 'b' the master's.
        Node master = translation("en", option("a", "Alpha"), option("b", "Bee"));
        Node fr = translation("fr", option("a", "Alfa"), option("b", ""));

        JCRNodeWrapper field = fieldNode("en", master, fr);
        when(field.getResolveSite().isMixLanguagesActive()).thenReturn(true);

        assertArrayEquals(
                new String[]{option("a", "Alfa"), option("b", "Bee")},
                service.forDisplay(field, "fr"));
    }

    @Test
    void anEntryLeftBlankIsDroppedWhenTheSiteHidesUntranslatedContent() throws Exception {
        // Per entry, not per field: the translated choice is offered, the untranslated
        // one is withheld. The visitor never reads a blank line, and never reads a word
        // in a language the site said not to serve here.
        Node master = translation("en", option("a", "Alpha"), option("b", "Bee"));
        Node fr = translation("fr", option("a", "Alfa"), option("b", ""));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertArrayEquals(new String[]{option("a", "Alfa")}, service.forDisplay(field, "fr"));
    }

    @Test
    void theDefaultLanguageRendersWhatItStores() throws Exception {
        // It IS the identity: nothing to align, the caller keeps the list it read.
        Node master = translation("en", option("a", "Alpha"));

        JCRNodeWrapper field = fieldNode("en", master);

        assertNull(service.forDisplay(field, "en"));
    }

    @Test
    void aFieldWithoutAMasterListRendersWhatItStores() throws Exception {
        // Authored in another language only, or its options cleared: no identity to
        // align on, so the stored list stands.
        Node master = translation("en");
        Node fr = translation("fr", option("a", "Alfa"));

        JCRNodeWrapper field = fieldNode("en", master, fr);

        assertNull(service.forDisplay(field, "fr"));
    }

    @Test
    void aFieldWhoseOptionsAreNotManualIsLeftToItsResolver() throws Exception {
        // Sourced, category and content modes resolve their options elsewhere.
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.isNodeType("fmdbmix:manualOptions")).thenReturn(false);

        assertNull(service.forDisplay(field, "fr"));
    }
}
