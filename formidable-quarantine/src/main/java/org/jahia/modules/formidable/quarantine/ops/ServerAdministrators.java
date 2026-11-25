package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;

/**
 * Whether a user is a server administrator: {@code root}, or a member of the server group {@code administrators}. A
 * site administrator is not one. {@link PlatformManagers} reads it: a server administrator manages every site that
 * exists, and alone runs the purge of the GraphQL API.
 */
final class ServerAdministrators {

    private ServerAdministrators() {
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
