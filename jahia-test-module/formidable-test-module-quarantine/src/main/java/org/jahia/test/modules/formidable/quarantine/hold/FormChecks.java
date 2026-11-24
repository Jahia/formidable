package org.jahia.test.modules.formidable.quarantine.hold;

import javax.jcr.RepositoryException;

/** Whether the reference hold owns a form: the rule of ownership, read from the "Checks" list of the form. */
@FunctionalInterface
interface FormChecks {

    /**
     * @param formId the identifier of the form, in live
     * @return true when the "Checks" list of the form ({@code qualifiers}) holds at least one node of a check type
     *         of this module
     * @throws RepositoryException when the form or its list cannot be read: the hold then refuses the submission
     */
    boolean holdsACheckOfTheModule(String formId) throws RepositoryException;
}
