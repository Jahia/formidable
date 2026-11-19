package org.jahia.test.modules.formidable.quarantine.run;

import org.jahia.test.modules.formidable.quarantine.check.CheckSettings;

import javax.jcr.RepositoryException;
import java.util.List;

/** The "Checks" list of a form, read into plain settings: no qualifier sees a node of the repository. */
@FunctionalInterface
public interface CheckList {

    /**
     * @param formId the identifier of the form, in live
     * @return the settings of each node of the list, in the order of the list; empty when the form has no list
     * @throws RepositoryException when the form or its list cannot be read
     */
    List<CheckSettings> of(String formId) throws RepositoryException;
}
