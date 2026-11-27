package org.jahia.modules.formidable.quarantine.graphql;

import javax.jcr.RepositoryException;

/** Reads the {@link FormFacts} of a live form of a site. */
@FunctionalInterface
interface FormReader {

    /**
     * @return the facts of the form, or {@link FormFacts#NONE} when no form of the site has this identifier
     */
    FormFacts read(String siteKey, String formId) throws RepositoryException;
}
