package org.jahia.modules.formidable.quarantine.run;

import javax.jcr.RepositoryException;

/** The quarantine policy of a form, read into a plain record when the run or a manager concludes. */
@FunctionalInterface
public interface FormPolicies {

    /**
     * @param formId the identifier of the form, in live
     * @return the policy of the form; {@link FormPolicy#DEFAULT} when the form does not have the mixin
     * @throws RepositoryException when the form cannot be read
     */
    FormPolicy of(String formId) throws RepositoryException;
}
