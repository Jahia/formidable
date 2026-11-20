package org.jahia.test.modules.formidable.quarantine.hold;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The rule of ownership as it reads the "Checks" list of a form. */
class JcrFormChecksTest {

    private static JCRNodeWrapper check(String type) throws RepositoryException {
        JCRNodeWrapper check = mock(JCRNodeWrapper.class);
        when(check.getPrimaryNodeTypeName()).thenReturn(type);
        return check;
    }

    private static JCRNodeWrapper formWithChecks(String... types) throws RepositoryException {
        List<JCRNodeWrapper> checks = new ArrayList<>();
        for (String type : types) {
            checks.add(check(type));
        }
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.iterator()).thenReturn(checks.iterator());
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        when(list.getNodes()).thenReturn(iterator);
        JCRNodeWrapper form = mock(JCRNodeWrapper.class);
        when(form.hasNode(JcrFormChecks.QUALIFIERS)).thenReturn(true);
        when(form.getNode(JcrFormChecks.QUALIFIERS)).thenReturn(list);
        return form;
    }

    @Test
    void aFormWithNoListIsNotOwned() throws RepositoryException {
        assertFalse(JcrFormChecks.ownsForm(mock(JCRNodeWrapper.class)));
    }

    @Test
    void aFormWithAnEmptyListIsNotOwned() throws RepositoryException {
        assertFalse(JcrFormChecks.ownsForm(formWithChecks()));
    }

    @Test
    void aFormWhoseListHoldsOnlyChecksOfAnotherModuleIsNotOwned() throws RepositoryException {
        assertFalse(JcrFormChecks.ownsForm(formWithChecks("acme:sandboxCheck", "acme:pdfCheck")));
    }

    @Test
    void aFormWhoseListHoldsTheRemoteStubIsOwned() throws RepositoryException {
        assertTrue(JcrFormChecks.ownsForm(formWithChecks("acme:sandboxCheck", "fmdbquar:remoteStubCheck")));
    }

    @Test
    void aFormWhoseListHoldsTheInProcessStubIsOwned() throws RepositoryException {
        assertTrue(JcrFormChecks.ownsForm(formWithChecks("fmdbquar:inProcessStubCheck")));
    }
}
