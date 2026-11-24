package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How the run reads the "Checks" list of a form. */
class JcrCheckListTest {

    private static JCRNodeWrapper child(String type, String name, boolean qualifier) throws RepositoryException {
        JCRNodeWrapper child = mock(JCRNodeWrapper.class);
        when(child.getPrimaryNodeTypeName()).thenReturn(type);
        when(child.getName()).thenReturn(name);
        when(child.isNodeType(FmdbMixin.QUALIFIER)).thenReturn(qualifier);
        return child;
    }

    private static JCRNodeWrapper formWithChildren(List<JCRNodeWrapper> children) throws RepositoryException {
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.iterator()).thenReturn(children.iterator());
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        when(list.getNodes()).thenReturn(iterator);
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.hasNode(JcrCheckList.QUALIFIERS)).thenReturn(true);
        when(form.getNode(JcrCheckList.QUALIFIERS)).thenReturn(list);
        return form;
    }

    @Test
    void aFormWithNoListHasNoCheck() throws RepositoryException {
        assertEquals(List.of(), JcrCheckList.settingsOf(mock(JCRNodeWrapper.class)));
    }

    @Test
    void theTranslationChildOfTheListIsNeverACheck() throws RepositoryException {
        JCRNodeWrapper form = formWithChildren(List.of(
                child("jnt:translation", "j:translation_en", false),
                child("fmdbquar:remoteStubCheck", "scan", true)));

        assertEquals(List.of(CheckSettings.withDefaults("fmdbquar:remoteStubCheck", "scan")),
                JcrCheckList.settingsOf(form));
    }

    @Test
    void aCheckOfAnotherModuleStillCounts() throws RepositoryException {
        JCRNodeWrapper form = formWithChildren(List.of(
                child("acme:sandboxCheck", "sandbox", true),
                child("fmdbquar:inProcessStubCheck", "marker", true)));

        assertEquals(List.of(CheckSettings.withDefaults("acme:sandboxCheck", "sandbox"),
                        CheckSettings.withDefaults("fmdbquar:inProcessStubCheck", "marker")),
                JcrCheckList.settingsOf(form));
    }
}
