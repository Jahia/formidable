package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.store.QuarantineTree;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.modules.formidable.quarantine.web.ServerAdministrators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.regex.Pattern;

/**
 * {@link Managers} on the platform: the permission is checked on the site node of the live workspace, in a session of
 * the current user, so that the roles granted on the published site decide. The root of a published site is readable;
 * a site with no live node has no manager. A server administrator manages every site that exists.
 */
final class PlatformManagers implements Managers {

    private static final Logger log = LoggerFactory.getLogger(PlatformManagers.class);
    private static final String LIVE_WORKSPACE = "live";
    private static final String SITE_TYPE = "jnt:virtualsite";
    /** A site key: no path separator, so the key names one child of {@code /sites}. */
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    public String currentUserName() {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        return user == null ? null : user.getUsername();
    }

    @Override
    public boolean isServerAdministrator() {
        return ServerAdministrators.isOne(JCRSessionFactory.getInstance().getCurrentUser(),
                JahiaGroupManagerService.getInstance());
    }

    @Override
    public boolean mayManage(String siteKey) {
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches()
                || JCRSessionFactory.getInstance().getCurrentUser() == null) {
            return false;
        }
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(LIVE_WORKSPACE);
            return mayManage(session.getNode(QuarantineTree.sitePath(siteKey)), isServerAdministrator());
        } catch (PathNotFoundException e) {
            return false;
        } catch (RepositoryException e) {
            log.warn("Could not check the permission {} on site {}", PERMISSION, siteKey);
            return false;
        }
    }

    /** The rule of the check: a site node, and a server administrator or a holder of the permission on it. */
    static boolean mayManage(JCRNodeWrapper site, boolean serverAdministrator) throws RepositoryException {
        return site.isNodeType(SITE_TYPE) && (serverAdministrator || site.hasPermission(PERMISSION));
    }
}
