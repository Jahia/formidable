package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;

/**
 * Whether the user of the current request is a server administrator: {@code root}, or a member of the server group
 * {@code administrators}. A site administrator is not one.
 */
@FunctionalInterface
interface ServerAdministrators {

    boolean currentUserIsOne();

    /** The check of the platform: the user that the authentication of Jahia set on the request. */
    static ServerAdministrators ofThePlatform() {
        return () -> {
            JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
            if (user == null) {
                return false;
            }
            // a null site key asks for the server group administrators, not for the site-administrators of a site
            return user.isRoot()
                    || JahiaGroupManagerService.getInstance().isAdminMember(user.getUsername(), user.getRealm(), null);
        };
    }
}
