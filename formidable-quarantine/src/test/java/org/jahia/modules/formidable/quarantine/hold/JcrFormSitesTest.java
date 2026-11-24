package org.jahia.modules.formidable.quarantine.hold;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The rule of ownership as it reads the site of a form: the modules of the site, never its "Checks" list. */
class JcrFormSitesTest {

    private static JCRNodeWrapper formOfASiteWith(String... modules) throws RepositoryException {
        JCRSiteNode site = mock(JCRSiteNode.class);
        Set<String> installed = new LinkedHashSet<>(List.of(modules));
        when(site.getInstalledModulesWithAllDependencies()).thenReturn(installed);
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.getResolveSite()).thenReturn(site);
        return form;
    }

    @Test
    void aFormOfASiteThatHasTheModuleIsOwned() throws RepositoryException {
        assertTrue(JcrFormSites.hasTheModule(formOfASiteWith("formidable-engine", JcrFormSites.MODULE_ID)));
    }

    @Test
    void aFormOfASiteWhoseModuleDependsOnTheModuleIsOwned() throws RepositoryException {
        // the dependencies are resolved by the site: formidable-test-module-quarantine depends on the module
        assertTrue(JcrFormSites.hasTheModule(formOfASiteWith("formidable-test-module-quarantine",
                JcrFormSites.MODULE_ID)));
    }

    @Test
    void aFormOfASiteWithoutTheModuleIsNotOwned() throws RepositoryException {
        assertFalse(JcrFormSites.hasTheModule(formOfASiteWith("formidable-engine",
                "formidable-test-module-samples-java")));
    }

    @Test
    void aFormUnderNoSiteCannotBeRead() {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);

        assertThrows(RepositoryException.class, () -> JcrFormSites.hasTheModule(form));
    }
}
