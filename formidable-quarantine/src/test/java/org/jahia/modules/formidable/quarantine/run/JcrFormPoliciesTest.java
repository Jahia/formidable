package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How the run reads the quarantine policy of a form, and finds a field named like its scan status. */
class JcrFormPoliciesTest {

    private static final String STATUS = "documentScanStatus";

    private static JCRNodeWrapper node(String name, boolean formElement, JCRNodeWrapper... children)
            throws RepositoryException {
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.iterator()).thenReturn(List.of(children).iterator());
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getName()).thenReturn(name);
        when(node.isNodeType(FmdbMixin.FORM_ELEMENT)).thenReturn(formElement);
        when(node.getNodes()).thenReturn(iterator);
        return node;
    }

    private static void property(JCRNodeWrapper node, String name, String value) throws RepositoryException {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getString()).thenReturn(value);
        when(property.getBoolean()).thenReturn(Boolean.parseBoolean(value));
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
    }

    /** A form with the policy mixin, the scan status name and the fields given. */
    private static JCRNodeWrapper form(String onFail, JCRNodeWrapper fields) throws RepositoryException {
        JCRNodeWrapper form = node("contactForm", false);
        when(form.isNodeType(JcrFormPolicies.POLICY_MIXIN)).thenReturn(true);
        property(form, JcrFormPolicies.ON_FAIL, onFail);
        property(form, JcrFormPolicies.SCAN_STATUS_NAME, STATUS);
        property(form, JcrFormPolicies.SCAN_STATUS_CLEAN, "clean");
        property(form, JcrFormPolicies.SCAN_STATUS_FAILED, "failed");
        property(form, JcrFormPolicies.SHOW_VALUES, "true");
        when(form.hasNode(JcrFormPolicies.FIELDS)).thenReturn(true);
        when(form.getNode(JcrFormPolicies.FIELDS)).thenReturn(fields);
        return form;
    }

    @Test
    void aFormWithoutTheMixinHasTheDefaultPolicy() throws RepositoryException {
        assertSame(FormPolicy.DEFAULT, JcrFormPolicies.policyOf(node("contactForm", false)));
        assertFalse(FormPolicy.DEFAULT.releasesValues());
        assertNull(FormPolicy.DEFAULT.scanStatusName());
    }

    @Test
    void theMixinGivesEachSettingOfThePolicy() throws RepositoryException {
        FormPolicy policy = JcrFormPolicies.policyOf(form("releaseValues", node("fields", false,
                node("fullName", true))));

        assertEquals(new FormPolicy(OnFail.RELEASE_VALUES, STATUS, "clean", "failed", true, false), policy);
    }

    @Test
    void anUnknownOnFailRejects() throws RepositoryException {
        FormPolicy policy = JcrFormPolicies.policyOf(form("releaseAll", node("fields", false)));

        assertEquals(OnFail.REJECT, policy.onFail());
    }

    @Test
    void aFieldInAStepNamedLikeTheScanStatusIsFound() throws RepositoryException {
        JCRNodeWrapper fields = node("fields", false, node("step1", false, node("fieldset", false,
                node(STATUS, true))));

        assertTrue(JcrFormPolicies.policyOf(form("releaseValues", fields)).scanStatusTaken());
    }

    @Test
    void aFieldsetNamedLikeTheScanStatusIsNotAField() throws RepositoryException {
        // fmdb:fieldset carries fmdbmix:formElement and fmdbmix:nonSubmittable: it posts no value
        JCRNodeWrapper fieldset = node(STATUS, true, node("fullName", true));
        when(fieldset.isNodeType(FmdbMixin.NON_SUBMITTABLE)).thenReturn(true);
        JCRNodeWrapper fields = node("fields", false, fieldset);

        assertFalse(JcrFormPolicies.policyOf(form("releaseValues", fields)).scanStatusTaken());
    }

    @Test
    void aFieldInsideAFieldsetNamedLikeTheScanStatusIsFound() throws RepositoryException {
        JCRNodeWrapper fieldset = node("fieldset", true, node(STATUS, true));
        when(fieldset.isNodeType(FmdbMixin.NON_SUBMITTABLE)).thenReturn(true);

        assertTrue(JcrFormPolicies.policyOf(form("releaseValues", node("fields", false, fieldset))).scanStatusTaken());
    }

    @Test
    void aScanStatusNameThatIsNotValidCountsAsNone() throws RepositoryException {
        JCRNodeWrapper form = form("releaseValues", node("fields", false, node("jcr:primaryType", true)));
        property(form, JcrFormPolicies.SCAN_STATUS_NAME, "jcr:primaryType");

        FormPolicy policy = JcrFormPolicies.policyOf(form);

        assertNull(policy.scanStatusName());
        assertFalse(policy.scanStatusTaken());
        assertEquals(OnFail.RELEASE_VALUES, policy.onFail());
    }

    @Test
    void aFormGoneFromLiveHasTheDefaultPolicy() throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNodeByIdentifier("gone")).thenThrow(new ItemNotFoundException("gone"));

        assertSame(FormPolicy.DEFAULT, JcrFormPolicies.policyOf(session, "gone"));
    }
}
