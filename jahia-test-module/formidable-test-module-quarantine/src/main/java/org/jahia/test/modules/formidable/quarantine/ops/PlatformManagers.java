package org.jahia.test.modules.formidable.quarantine.ops;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

/**
 * {@link Managers} on the platform: the permission is checked on the live form, in a session of the current user, so
 * that the ACL of the form decides. A form the user cannot read gives no permission.
 */
final class PlatformManagers implements Managers {

    private static final Logger log = LoggerFactory.getLogger(PlatformManagers.class);
    private static final String LIVE_WORKSPACE = "live";

    @Override
    public String currentUserName() {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        return user == null ? null : user.getUsername();
    }

    @Override
    public boolean isServerAdministrator() {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null) {
            return false;
        }
        // a null site key asks for the server group administrators, not for the site-administrators of a site
        return user.isRoot()
                || JahiaGroupManagerService.getInstance().isAdminMember(user.getUsername(), user.getRealm(), null);
    }

    @Override
    public boolean mayManage(String formId) {
        if (isServerAdministrator()) {
            return true;
        }
        if (formId == null || JCRSessionFactory.getInstance().getCurrentUser() == null) {
            return false;
        }
        try {
            JCRNodeWrapper form = JCRSessionFactory.getInstance().getCurrentUserSession(LIVE_WORKSPACE)
                    .getNodeByIdentifier(formId);
            return form.hasPermission(PERMISSION);
        } catch (ItemNotFoundException e) {
            return false;
        } catch (RepositoryException e) {
            log.warn("Could not check the permission {} on form {}: {}", PERMISSION, formId, e.getMessage());
            return false;
        }
    }
}
