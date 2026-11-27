package org.jahia.modules.formidable.quarantine.graphql;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The facts of a live form: a form of the site alone, its title, its setting showValues, and its checks. */
class JcrFormReaderTest {

    private static final String SITE = "siteA";
    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";

    private JCRSessionWrapper session;
    private JCRNodeWrapper form;

    @BeforeEach
    void setUp() throws RepositoryException {
        session = mock(JCRSessionWrapper.class);
        form = mock(JCRNodeWrapper.class);
        when(session.getNodeByIdentifier(FORM_ID)).thenReturn(form);
        when(form.getPath()).thenReturn("/sites/" + SITE + "/contents/claims");
        when(form.getName()).thenReturn("claims");
        when(form.isNodeType(FmdbMixin.FORM_ROOT)).thenReturn(true);
        JCRNodeWrapper site = mock(JCRNodeWrapper.class);
        when(session.getNode("/sites/" + SITE)).thenReturn(site);
        property(site, "j:defaultLanguage", "fr");
    }

    private static JCRPropertyWrapper property(JCRNodeWrapper node, String name, String value)
            throws RepositoryException {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getString()).thenReturn(value);
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
        return property;
    }

    @Test
    void aFormOfTheSiteGivesItsTitleItsSettingAndItsChecks() throws Exception {
        JCRNodeWrapper translation = mock(JCRNodeWrapper.class);
        property(translation, "jcr:title", "Réclamations");
        when(form.hasNode("j:translation_fr")).thenReturn(true);
        when(form.getNode("j:translation_fr")).thenReturn(translation);
        when(property(form, FormFacts.SHOW_VALUES, "true").getBoolean()).thenReturn(true);
        JCRNodeWrapper qualifiers = mock(JCRNodeWrapper.class);
        JCRNodeWrapper check = mock(JCRNodeWrapper.class);
        when(check.isNodeType(FmdbMixin.QUALIFIER)).thenReturn(true);
        when(check.getName()).thenReturn("remoteCheck");
        when(check.getPrimaryNodeTypeName()).thenReturn("fmdbquar:remoteStubCheck");
        JCRNodeWrapper notACheck = mock(JCRNodeWrapper.class);
        JCRNodeIteratorWrapper children = mock(JCRNodeIteratorWrapper.class);
        when(children.iterator()).thenReturn(List.of(check, notACheck).iterator());
        when(qualifiers.getNodes()).thenReturn(children);
        when(form.hasNode("qualifiers")).thenReturn(true);
        when(form.getNode("qualifiers")).thenReturn(qualifiers);

        FormFacts facts = JcrFormReader.facts(session, SITE, FORM_ID);

        assertEquals("Réclamations", facts.title());
        assertTrue(facts.showValues());
        assertEquals(Map.of("remoteCheck", "fmdbquar:remoteStubCheck"), facts.checkTypes());
    }

    @Test
    void aFormWithNoSettingAndNoTitleShowsNoValueAndIsNamedByItsNode() throws Exception {
        FormFacts facts = JcrFormReader.facts(session, SITE, FORM_ID);

        assertEquals("claims", facts.title());
        assertFalse(facts.showValues());
        assertEquals(Map.of(), facts.checkTypes());
    }

    @Test
    void aNodeOfAnotherSiteOrNoFormGivesNothing() throws Exception {
        when(form.getPath()).thenReturn("/sites/siteAB/contents/claims");
        assertSame(FormFacts.NONE, JcrFormReader.facts(session, SITE, FORM_ID), "a site whose key starts alike");

        when(form.getPath()).thenReturn("/sites/" + SITE + "/contents/claims");
        when(form.isNodeType(FmdbMixin.FORM_ROOT)).thenReturn(false);
        assertSame(FormFacts.NONE, JcrFormReader.facts(session, SITE, FORM_ID), "not a form");

        when(session.getNodeByIdentifier("gone")).thenThrow(new ItemNotFoundException("gone"));
        assertSame(FormFacts.NONE, JcrFormReader.facts(session, SITE, "gone"), "a form that is gone");
    }
}
