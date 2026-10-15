package org.jahia.modules.formidable.content.platform;

import org.jahia.services.usermanager.JahiaUser;

import java.util.List;

/**
 * What the action asks the platform about the person who submitted: the groups they belong to and their organization.
 * A seam over Jahia's user and group services, so the rules built on it can be tested without a platform.
 *
 * <p>Implementations are stateless singletons and thread-safe.</p>
 */
public interface Submitters {

    /**
     * @param user a signed-in user
     * @return the paths of the groups the user is a member of, nested groups included ({@code /groups/x},
     *         {@code /sites/s/groups/y}); empty when the platform cannot say
     */
    List<String> groupPaths(JahiaUser user);

    /**
     * @param user a signed-in user
     * @return the user's {@code j:organization} property, or {@code null} when it is empty or unreadable
     */
    String organization(JahiaUser user);
}
