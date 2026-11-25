package org.jahia.modules.formidable.quarantine.ops;

import org.jahia.modules.formidable.quarantine.store.QuarantineTree;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.regex.Pattern;

/**
 * {@link Managers} on the platform: the permission is checked on the site node of the live workspace, in a session of
 * the current user, so that the roles granted on the published site decide. The root of a published site is readable;
 * a site with no live node has no manager. A server administrator manages every site that exists. A failure of the
 * repository is thrown, never read as "not a manager".
 */
final class PlatformManagers implements Managers {

    private static final Logger log = LoggerFactory.getLogger(PlatformManagers.class);
    private static final String LIVE_WORKSPACE = "live";
    private static final String SITE_TYPE = "jnt:virtualsite";
    /** A site key: no path separator, so the key names one child of {@code /sites}. */
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** What the check reads of the request: its user, a session of that user, and the groups. */
    interface Request {
        JahiaUser currentUser();

        JCRSessionWrapper currentUserSession(String workspace) throws RepositoryException;

        JahiaGroupManagerService groups();
    }

    /** The request as the authentication of Jahia set it. */
    private static final class PlatformRequest implements Request {
        @Override
        public JahiaUser currentUser() {
            return JCRSessionFactory.getInstance().getCurrentUser();
        }

        @Override
        public JCRSessionWrapper currentUserSession(String workspace) throws RepositoryException {
            return JCRSessionFactory.getInstance().getCurrentUserSession(workspace);
        }

        @Override
        public JahiaGroupManagerService groups() {
            return JahiaGroupManagerService.getInstance();
        }
    }

    private final Request request;

    PlatformManagers() {
        this(new PlatformRequest());
    }

    PlatformManagers(Request request) {
        this.request = request;
    }

    @Override
    public String currentUserName() {
        JahiaUser user = request.currentUser();
        return user == null ? null : user.getUsername();
    }

    @Override
    public boolean isServerAdministrator() {
        return ServerAdministrators.isOne(request.currentUser(), request.groups());
    }

    @Override
    public boolean mayManage(String siteKey) {
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches() || request.currentUser() == null) {
            return false;
        }
        JCRSessionWrapper session;
        try {
            session = request.currentUserSession(LIVE_WORKSPACE);
        } catch (RepositoryException e) {
            throw failure(siteKey);
        }
        return mayManage(session, siteKey, isServerAdministrator());
    }

    /**
     * The check in a session of the user: a site with no live node, or a node the user cannot read, gives no
     * permission. Any other failure of the repository is not an answer: it is thrown, so that the API answers an error
     * and not the answer of a user who does not manage the site.
     *
     * @throws IllegalStateException when the repository fails otherwise
     */
    static boolean mayManage(JCRSessionWrapper session, String siteKey, boolean serverAdministrator) {
        try {
            return mayManage(session.getNode(QuarantineTree.sitePath(siteKey)), serverAdministrator);
        } catch (PathNotFoundException | AccessDeniedException e) {
            return false;
        } catch (RepositoryException e) {
            throw failure(siteKey);
        }
    }

    private static IllegalStateException failure(String siteKey) {
        log.warn("Could not check the permission {} on site {}", PERMISSION, siteKey);
        return new IllegalStateException("Could not check the permission of a manager on site " + siteKey);
    }

    /** The rule of the check: a site node, and a server administrator or a holder of the permission on it. */
    static boolean mayManage(JCRNodeWrapper site, boolean serverAdministrator) throws RepositoryException {
        return site.isNodeType(SITE_TYPE) && (serverAdministrator || site.hasPermission(PERMISSION));
    }
}
