package org.jahia.test.modules.formidable.quarantine.hold;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.test.modules.formidable.quarantine.QuarantineTypes;

import javax.jcr.RepositoryException;

/** Reads the "Checks" list of a form in live, with a system session: the visitor who submits cannot read it. */
final class JcrFormChecks implements FormChecks {

    /**
     * The child of a form that holds its uploads, which the engine declares on {@code fmdbmix:quarantine}. The
     * {@code api} package of the engine has no constant for it.
     */
    static final String QUALIFIERS = "qualifiers";
    private static final String LIVE_WORKSPACE = "live";

    @Override
    public boolean holdsACheckOfTheModule(String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> ownsForm(session.getNodeByIdentifier(formId)));
    }

    /** The rule of ownership: at least one node of the "Checks" list has a check type of this module. */
    static boolean ownsForm(JCRNodeWrapper form) throws RepositoryException {
        if (!form.hasNode(QUALIFIERS)) {
            return false;
        }
        for (JCRNodeWrapper check : form.getNode(QUALIFIERS).getNodes()) {
            if (QuarantineTypes.isCheckType(check.getPrimaryNodeTypeName())) {
                return true;
            }
        }
        return false;
    }
}
