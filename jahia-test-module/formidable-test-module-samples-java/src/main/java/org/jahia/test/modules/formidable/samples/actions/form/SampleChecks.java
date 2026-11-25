package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;

import javax.jcr.RepositoryException;

/**
 * Whether the sample hold claims a form: its "Quarantine checks" list holds a {@value #SAMPLE_CHECK}. Read in live,
 * with a system session: the visitor who submits cannot read the form.
 */
@FunctionalInterface
interface SampleChecks {

    /** The check type of this module. */
    String SAMPLE_CHECK = "fmdbsample:sampleCheck";
    /** The child of a form that holds its checks, which the engine declares on {@code fmdbmix:quarantine}. */
    String QUALIFIERS = "qualifiers";

    /**
     * @param formId the identifier of the form, in live
     * @return true when the "Quarantine checks" list of the form holds at least one {@value #SAMPLE_CHECK}
     * @throws RepositoryException when the form or its list cannot be read: the hold then refuses the submission
     */
    boolean holdsASampleCheck(String formId) throws RepositoryException;

    /** The check of the platform, on the live form. */
    static SampleChecks ofThePlatform() {
        return formId -> JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, "live", null,
                session -> holdsASampleCheck(session.getNodeByIdentifier(formId)));
    }

    static boolean holdsASampleCheck(JCRNodeWrapper form) throws RepositoryException {
        if (!form.hasNode(QUALIFIERS)) {
            return false;
        }
        for (JCRNodeWrapper check : form.getNode(QUALIFIERS).getNodes()) {
            if (SAMPLE_CHECK.equals(check.getPrimaryNodeTypeName())) {
                return true;
            }
        }
        return false;
    }
}
