package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;

/**
 * Whether the user of the current request is a server administrator: {@code root}, or a member of the server group
 * {@code administrators}. A site administrator is not one. A copy of the rule of {@code formidable-quarantine},
 * whose package {@code web} is private: "run now" and the control of the stubs read it.
 */
@FunctionalInterface
public interface ServerAdministrators {

    boolean currentUserIsOne();

    /** The check of the platform: the user that the authentication of Jahia set on the request. */
    static ServerAdministrators ofThePlatform() {
        return () -> isOne(JCRSessionFactory.getInstance().getCurrentUser(), JahiaGroupManagerService.getInstance());
    }

    /** The rule of the check, apart from the services that give the user and the groups. */
    static boolean isOne(JahiaUser user, JahiaGroupManagerService groups) {
        if (user == null) {
            return false;
        }
        // a null site key asks for the server group administrators, not for the site-administrators of a site
        return user.isRoot() || groups.isAdminMember(user.getUsername(), user.getRealm(), null);
    }
}
