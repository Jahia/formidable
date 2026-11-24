package org.jahia.modules.formidable.quarantine.hold;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;

/**
 * Reads the site of a form in live, with a system session: the visitor who submits cannot read it. Only the
 * repository decides, through the modules of the site and their dependencies, never the check services registered at
 * that moment.
 */
public final class JcrFormSites implements FormSites {

    /** The identifier of this module, as a site lists it among its modules. */
    public static final String MODULE_ID = "formidable-quarantine";
    private static final String LIVE_WORKSPACE = "live";

    @Override
    public boolean siteHasTheModule(String formId) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, LIVE_WORKSPACE, null,
                session -> hasTheModule(session.getNodeByIdentifier(formId)));
    }

    /**
     * The rule of ownership: the site of the form has the module, enabled on the site or through a module of the site
     * that depends on it, as Jahia resolves the modules of a site everywhere else.
     *
     * @throws RepositoryException when the form is under no site
     */
    static boolean hasTheModule(JCRNodeWrapper form) throws RepositoryException {
        JCRSiteNode site = form.getResolveSite();
        if (site == null) {
            throw new RepositoryException("The form " + form.getIdentifier() + " is under no site");
        }
        return site.getInstalledModulesWithAllDependencies().contains(MODULE_ID);
    }
}
