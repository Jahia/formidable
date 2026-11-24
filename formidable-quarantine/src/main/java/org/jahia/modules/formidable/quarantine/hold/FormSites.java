package org.jahia.modules.formidable.quarantine.hold;

import javax.jcr.RepositoryException;

/** Whether the hold of the module owns a form: the rule of ownership, read from the site of the form. */
@FunctionalInterface
interface FormSites {

    /**
     * @param formId the identifier of the form, in live
     * @return true when the site of the form has the module
     * @throws RepositoryException when the form or its site cannot be read: the hold then refuses the submission
     */
    boolean siteHasTheModule(String formId) throws RepositoryException;
}
