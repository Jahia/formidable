package org.jahia.modules.formidable.quarantine.hold;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The rule of ownership as it reads the site of a form: the modules of the site and their dependencies, never its
 * "Checks" list. A site whose modules cannot be read refuses the form.
 */
class JcrFormSitesTest {

    /**
     * The registry of the platform: formidable-test-module-quarantine depends on the module, as its Jahia-Depends says.
     * A module it does not list is not deployed.
     */
    private static final Map<String, List<String>> DEPLOYED = Map.of(
            "templates-web", List.of(),
            "formidable-engine", List.of(),
            JcrFormSites.MODULE_ID, List.of("formidable-engine"),
            "formidable-test-module-samples-java", List.of("formidable-engine"),
            "formidable-test-module-quarantine", List.of("formidable-engine", JcrFormSites.MODULE_ID));

    /** The registry answers empty for a module it does not know, as {@code lookupById} answers null. */
    private static final JcrFormSites SITES = new JcrFormSites(moduleId -> Optional.ofNullable(DEPLOYED.get(moduleId)));

    private static JCRSiteNode site() {
        JCRSiteNode site = mock(JCRSiteNode.class);
        // what the swallowing getters of the site answer: a rule read from them would find no module and hand every
        // form down, instead of refusing it
        when(site.getInstalledModules()).thenReturn(List.of());
        when(site.getInstalledModulesWithAllDependencies()).thenReturn(Set.of());
        return site;
    }

    private static JCRNodeWrapper formOf(JCRSiteNode site) throws RepositoryException {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.getResolveSite()).thenReturn(site);
        return form;
    }

    private static JCRNodeWrapper formOfASiteWith(String... modules) throws RepositoryException {
        JCRValueWrapper[] values = new JCRValueWrapper[modules.length];
        for (int i = 0; i < modules.length; i++) {
            values[i] = mock(JCRValueWrapper.class);
            when(values[i].getString()).thenReturn(modules[i]);
        }
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getValues()).thenReturn(values);
        JCRSiteNode site = site();
        when(site.getProperty(JcrFormSites.INSTALLED_MODULES)).thenReturn(property);
        return formOf(site);
    }

    @Test
    void aFormOfASiteThatHasTheModuleIsOwned() throws RepositoryException {
        assertTrue(SITES.hasTheModule(formOfASiteWith("templates-web", "formidable-engine:0.7.0",
                JcrFormSites.MODULE_ID + ":0.7.0")));
    }

    @Test
    void aFormOfASiteWhoseModuleDependsOnTheModuleIsOwned() throws RepositoryException {
        assertTrue(SITES.hasTheModule(formOfASiteWith("templates-web", "formidable-test-module-quarantine")));
    }

    @Test
    void aFormOfASiteWithoutTheModuleIsNotOwned() throws RepositoryException {
        assertFalse(SITES.hasTheModule(formOfASiteWith("templates-web", "formidable-engine",
                "formidable-test-module-samples-java")));
    }

    @Test
    void aFormUnderNoSiteCannotBeRead() {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);

        assertThrows(RepositoryException.class, () -> SITES.hasTheModule(form));
    }

    @Test
    void aSiteWhoseModulesCannotBeReadRefusesTheForm() throws RepositoryException {
        JCRSiteNode site = site();
        when(site.getProperty(JcrFormSites.INSTALLED_MODULES)).thenThrow(new RepositoryException("unreadable"));
        JCRNodeWrapper form = formOf(site);

        assertThrows(RepositoryException.class, () -> SITES.hasTheModule(form));
    }

    @Test
    void aSiteThatListsAModuleThePlatformDoesNotKnowRefusesTheForm() throws RepositoryException {
        // the unknown module could depend on formidable-quarantine: reading it as "no dependency" would hand the form
        // on
        JCRNodeWrapper form = formOfASiteWith("templates-web", "acme-undeployed-check");

        assertThrows(RepositoryException.class, () -> SITES.hasTheModule(form));
    }

    @Test
    void aSiteThatListsTheModuleIsOwnedWhateverTheOtherModulesItLists() throws RepositoryException {
        assertTrue(SITES.hasTheModule(formOfASiteWith("acme-undeployed-check", JcrFormSites.MODULE_ID)));
    }

    @Test
    void aSiteWhoseKnownModuleBringsTheModuleIsOwnedWhateverAnUnknownModuleListedBeforeIt() throws RepositoryException {
        // the unknown module is listed first: the walk goes on through the known ones, whatever the order
        assertTrue(SITES.hasTheModule(formOfASiteWith("acme-undeployed-check", "formidable-test-module-quarantine")));
    }

    @Test
    void aSiteThatListsNoModuleRefusesTheForm() throws RepositoryException {
        JCRNodeWrapper form = formOfASiteWith();

        assertThrows(RepositoryException.class, () -> SITES.hasTheModule(form));
    }
}
