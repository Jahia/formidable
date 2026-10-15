package org.jahia.modules.formidable.content.store;

import org.jahia.modules.formidable.content.plan.ContentPlan;
import org.jahia.services.usermanager.JahiaUser;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * Writes a {@link ContentPlan} to the repository: the files, the content node, its translations, and, when asked, the
 * publication. The one place of the module that writes with a system session.
 *
 * <p>All or nothing, as far as the platform allows: when a step fails, what the earlier steps created is removed.
 * Implementations are stateless singletons; each call opens its own sessions and keeps no node.</p>
 */
public interface ContentWriter {

    /**
     * What was created.
     *
     * @param identifier the content node's UUID
     * @param path       the content node's path
     * @param filePaths  the paths of the stored files, in storage order
     */
    record Created(String identifier, String path, List<String> filePaths) {
        public Created {
            filePaths = List.copyOf(filePaths);
        }
    }

    /**
     * Creates the content and its files in the {@code default} workspace, then publishes them when the plan says so.
     * The caller has already checked that the submitter may use the folders of the plan: this method does not check it
     * again, since it writes with a system session.
     *
     * @param plan      the validated plan
     * @param submitter the signed-in user the writes are attributed to ({@code jcr:createdBy}); they hold no write
     *                  right in {@code default}, which is why a system session is used
     * @return what was created
     * @throws RepositoryException when the platform refuses a step (a mandatory property missing, a constraint, the
     *                             publication); nothing is left behind
     */
    Created create(ContentPlan plan, JahiaUser submitter) throws RepositoryException;
}
