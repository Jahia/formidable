package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

/**
 * Reads the quarantine policy of a form in live, with a system session, each time the run or a manager concludes: an
 * edit of the form counts from its next publication. A form without the mixin {@code fmdbqmix:quarantinePolicy} has
 * the default policy.
 */
public final class JcrFormPolicies implements FormPolicies {

    private static final Logger log = LoggerFactory.getLogger(JcrFormPolicies.class);

    /** The mixin of the module that carries the policy of a form, read while the quarantine switch of the form is on. */
    public static final String POLICY_MIXIN = "fmdbqmix:quarantinePolicy";
    static final String ON_FAIL = "onFail";
    static final String SCAN_STATUS_NAME = "scanStatusName";
    static final String SCAN_STATUS_CLEAN = "scanStatusClean";
    static final String SCAN_STATUS_FAILED = "scanStatusFailed";
    static final String SHOW_VALUES = "showValues";
    /** The child of a form that holds its fields, which the engine declares on its form types. */
    static final String FIELDS = "fields";
    private static final String LIVE_WORKSPACE = "live";

    @Override
    public FormPolicy of(String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> policyOf(session, formId));
    }

    /** A form that is gone from live has the default policy: a manager can still reject its submissions. */
    static FormPolicy policyOf(JCRSessionWrapper session, String formId) throws RepositoryException {
        try {
            return policyOf(session.getNodeByIdentifier(formId));
        } catch (ItemNotFoundException gone) {
            return FormPolicy.DEFAULT;
        }
    }

    /**
     * The policy of the form, and whether one of its fields has the name of the scan status. A name that is not
     * {@link FormPolicy#isValidName valid} counts as none, with an error: no release writes it.
     */
    static FormPolicy policyOf(JCRNodeWrapper form) throws RepositoryException {
        if (!form.isNodeType(POLICY_MIXIN)) {
            return FormPolicy.DEFAULT;
        }
        String name = string(form, SCAN_STATUS_NAME);
        if (name != null && !name.isBlank() && !FormPolicy.isValidName(name)) {
            log.error("Form {}: the name of its scan status is not valid, the releases of the form add none",
                    form.getIdentifier());
        }
        FormPolicy policy = new FormPolicy(OnFail.fromValue(string(form, ON_FAIL)), name,
                string(form, SCAN_STATUS_CLEAN), string(form, SCAN_STATUS_FAILED),
                form.hasProperty(SHOW_VALUES) && form.getProperty(SHOW_VALUES).getBoolean(), false);
        if (policy.scanStatusName() == null || !form.hasNode(FIELDS)
                || !hasField(form.getNode(FIELDS), policy.scanStatusName())) {
            return policy;
        }
        return new FormPolicy(policy.onFail(), policy.scanStatusName(), policy.scanStatusClean(),
                policy.scanStatusFailed(), policy.showValues(), true);
    }

    /**
     * @return true when a field below the node, in a step or a fieldset too, has the name: a field that posts a
     *         value, as the field whitelist of the engine counts it, not a container or a non-submittable element
     */
    private static boolean hasField(JCRNodeWrapper node, String name) throws RepositoryException {
        for (JCRNodeWrapper child : node.getNodes()) {
            if ((isSubmittableField(child) && name.equals(child.getName())) || hasField(child, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSubmittableField(JCRNodeWrapper node) throws RepositoryException {
        return node.isNodeType(FmdbMixin.FORM_ELEMENT) && !node.isNodeType(FmdbMixin.NON_SUBMITTABLE);
    }

    private static String string(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.hasProperty(property) ? node.getProperty(property).getString() : null;
    }
}
