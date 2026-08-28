package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write rule of the migration in isolation: a language without a title gets the
 * type's default, a language with one is never overridden, blank counts as missing.
 * The Cypress spec covers the end-to-end path (restart, both workspaces, idempotence).
 */
class ListTitlesContentMigrationTest {

    private static final Locale EN = Locale.ENGLISH;
    private static final Locale FR = Locale.FRENCH;

    private static String defaults(String type, Locale locale) {
        return FR.equals(locale) ? "Champs du formulaire" : "Form fields";
    }

    @Test
    void writesTheDefaultTitleOnlyWhereALanguageHasNone() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        Node en = mock(Node.class);
        Node fr = mock(Node.class);
        Property enTitle = mock(Property.class);

        when(list.getPrimaryNodeTypeName()).thenReturn("fmdb:fieldList");
        when(list.isNodeType("mix:title")).thenReturn(true);
        // English already titled by a contributor, French never opened.
        when(list.hasI18N(EN)).thenReturn(true);
        when(list.getI18N(EN)).thenReturn(en);
        when(en.hasProperty("jcr:title")).thenReturn(true);
        when(en.getProperty("jcr:title")).thenReturn(enTitle);
        when(enTitle.getString()).thenReturn("My fields");
        when(list.hasI18N(FR)).thenReturn(false);
        when(list.getOrCreateI18N(FR)).thenReturn(fr);

        boolean touched = new ListTitlesContentMigration()
                .migrateList(session, list, List.of("en", "fr"), false, ListTitlesContentMigrationTest::defaults);

        assertTrue(touched);
        verify(session).checkout(list);
        verify(fr).setProperty("jcr:title", "Champs du formulaire");
        verify(en, never()).setProperty(anyString(), anyString());
        verify(list, never()).getOrCreateI18N(EN);
    }

    @Test
    void aBlankTitleCountsAsMissing() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        Node en = mock(Node.class);
        Property blank = mock(Property.class);

        when(list.getPrimaryNodeTypeName()).thenReturn("fmdb:fieldList");
        when(list.isNodeType("mix:title")).thenReturn(false);
        when(list.hasI18N(EN)).thenReturn(true);
        when(list.getI18N(EN)).thenReturn(en);
        when(en.hasProperty("jcr:title")).thenReturn(true);
        when(en.getProperty("jcr:title")).thenReturn(blank);
        when(blank.getString()).thenReturn("   ");
        when(list.getOrCreateI18N(EN)).thenReturn(en);

        boolean touched = new ListTitlesContentMigration()
                .migrateList(session, list, List.of("en"), false, ListTitlesContentMigrationTest::defaults);

        assertTrue(touched);
        // A list stored before the type carried mix:title gets the mixin stamped.
        verify(list).addMixin("mix:title");
        verify(en).setProperty("jcr:title", "Form fields");
    }

    @Test
    void aTitledListIsLeftAloneAndATypeWithoutDefaultToo() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper titled = mock(JCRNodeWrapper.class);
        Node en = mock(Node.class);
        Property title = mock(Property.class);
        when(titled.getPrimaryNodeTypeName()).thenReturn("fmdb:actionList");
        when(titled.hasI18N(EN)).thenReturn(true);
        when(titled.getI18N(EN)).thenReturn(en);
        when(en.hasProperty("jcr:title")).thenReturn(true);
        when(en.getProperty("jcr:title")).thenReturn(title);
        when(title.getString()).thenReturn("Mes actions");

        assertFalse(new ListTitlesContentMigration()
                .migrateList(session, titled, List.of("en"), false, ListTitlesContentMigrationTest::defaults));

        JCRNodeWrapper untitled = mock(JCRNodeWrapper.class);
        when(untitled.getPrimaryNodeTypeName()).thenReturn("fmdb:actionList");
        when(untitled.hasI18N(EN)).thenReturn(false);

        // No default to write: nothing is written, the node is not even checked out.
        assertFalse(new ListTitlesContentMigration()
                .migrateList(session, untitled, List.of("en"), false, (type, locale) -> null));
        verify(session, never()).checkout(any(JCRNodeWrapper.class));
        verify(untitled, never()).getOrCreateI18N(any(Locale.class));
    }

    @Test
    void inLiveALanguageNeverPublishedIsNotCreated() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        Node en = mock(Node.class);

        when(list.getPrimaryNodeTypeName()).thenReturn("fmdb:fieldList");
        when(list.isNodeType("mix:title")).thenReturn(true);
        // Only english was published: it gets its title, french stays unpublished.
        when(list.hasI18N(EN)).thenReturn(true);
        when(list.getI18N(EN)).thenReturn(en);
        when(en.hasProperty("jcr:title")).thenReturn(false);
        when(list.getOrCreateI18N(EN)).thenReturn(en);
        when(list.hasI18N(FR)).thenReturn(false);

        boolean touched = new ListTitlesContentMigration()
                .migrateList(session, list, List.of("en", "fr"), true, ListTitlesContentMigrationTest::defaults);

        assertTrue(touched);
        verify(en).setProperty("jcr:title", "Form fields");
        verify(list, never()).getOrCreateI18N(FR);
    }
}
