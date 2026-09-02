package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The selection rule in isolation: a form's site is reactivated only when it lost the
 * elements module from its installed list — the state the 0.3 → 0.4 module-identity
 * change leaves behind. The remedy call itself is the documented step-4 gesture,
 * exercised on the real upgrade replays.
 */
class ElementsSiteReactivationTest {

    private static JCRNodeWrapper formOnSite(String sitePath, List<String> installedModules)
            throws RepositoryException {
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getPath()).thenReturn(sitePath);
        when(site.getInstalledModules()).thenReturn(installedModules);
        when(form.getResolveSite()).thenReturn(site);
        return form;
    }

    @Test
    void aFormBearingSiteThatLostTheModuleIsSelected() throws Exception {
        JCRNodeWrapper orphaned = formOnSite("/sites/upgraded", List.of("templateset", "default"));

        assertEquals("/sites/upgraded", ElementsSiteReactivation.orphanedSitePath(orphaned));
    }

    @Test
    void aSiteStillCarryingTheModuleIsLeftAlone() throws Exception {
        JCRNodeWrapper healthy = formOnSite("/sites/fine",
                List.of("templateset", "formidable-elements", "default"));

        assertNull(ElementsSiteReactivation.orphanedSitePath(healthy));
    }

    @Test
    void aSitelessFormIsLeftAlone() throws Exception {
        // None is expected under /sites, but the rule must not NPE on one.
        JCRNodeWrapper stray = mock(JCRNodeWrapper.class);
        when(stray.getResolveSite()).thenReturn(null);

        assertNull(ElementsSiteReactivation.orphanedSitePath(stray));
    }
}
