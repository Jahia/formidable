package org.jahia.modules.formidable.accounts.account;

import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRGroupNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jahia.modules.formidable.accounts.activation.ActivationToken;

/**
 * {@link AccountDirectory} on Jahia's user and group services, in a system session of the default workspace (users
 * live there). A system session is needed and justified: a guest creates an account for itself, and the action has
 * validated everything (captcha, rate limits, password rule) before it calls; the group is checked here, against the
 * operator's allowed groups and for back-office access.
 *
 * <p>Threading: a singleton; every call opens its own session inside {@code JCRTemplate} and never keeps a node. The
 * user is created by {@link JahiaUserManagerService#createUser}, so the platform hashes the password: this class never
 * handles a hash of it nor logs it. An account awaiting activation is a user locked ({@code j:accountLocked}) carrying
 * the mixin {@code fmdbaccmix:pendingActivation} (token hash, expiry, where to go after).</p>
 */
@Component(service = AccountDirectory.class, immediate = true)
public class JahiaAccountDirectory implements AccountDirectory {

    static final String PENDING_MIXIN = "fmdbaccmix:pendingActivation";
    static final String TOKEN_HASH = "activationTokenHash";
    static final String EXPIRES = "activationExpires";
    static final String REDIRECT = "activationRedirect";
    static final String LOCKED = "j:accountLocked";
    static final String USER_TYPE = "jnt:user";
    static final String SERVER_ACL = "/j:acl";
    static final String GLOBAL_GROUPS = "/groups/";
    private static final Set<String> PRIVILEGED_GLOBAL_GROUPS = Set.of("/groups/privileged", "/groups/administrators");
    private static final String SITE_PRIVILEGED_SUFFIX = "/groups/site-privileged";
    private static final String SITE_ADMINISTRATORS_SUFFIX = "/groups/site-administrators";
    private static final Set<String> EVERYONE_GROUPS = Set.of("users", "guest", "site-users");
    private static final String SITE_GROUPS = "/groups/";
    private static final String ROLES = "j:roles";
    private static final String ROLE_TYPE = "jnt:role";
    private static final String PRIVILEGED_ACCESS = "j:privilegedAccess";
    private static final long MAX_ACES = 1000;
    private static final String PRINCIPAL = "j:principal";
    private static final String ACE_TYPE = "j:aceType";
    private static final String GRANT = "GRANT";
    /** jcr:lastModifiedBy of what a system session wrote (provisioning, import scripts). */
    private static final String SYSTEM_ACCOUNT = "system";

    private static final Logger log = LoggerFactory.getLogger(JahiaAccountDirectory.class);

    private final Supplier<JCRTemplate> jcr;
    private final Queries queries;
    private JahiaUserManagerService users;
    private JahiaGroupManagerService groups;
    private AccountsConfigService config;

    /** Production constructor: the platform's {@code JCRTemplate}. */
    public JahiaAccountDirectory() {
        this(JCRTemplate::getInstance);
    }

    JahiaAccountDirectory(Supplier<JCRTemplate> jcr) {
        this(jcr, JahiaAccountDirectory::runQuery);
    }

    JahiaAccountDirectory(Supplier<JCRTemplate> jcr, Queries queries) {
        this.jcr = jcr;
        this.queries = queries;
    }

    /** Runs a JCR-SQL2 query in a session: the seam the tests replace (the platform's query wrappers are not mockable). */
    @FunctionalInterface
    interface Queries {
        List<Node> run(JCRSessionWrapper session, String sql, long limit) throws RepositoryException;
    }

    private static List<Node> runQuery(JCRSessionWrapper session, String sql, long limit) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(sql, Query.JCR_SQL2);
        query.setLimit(limit);
        List<Node> nodes = new ArrayList<>();
        NodeIterator found = query.execute().getNodes();
        while (found.hasNext()) {
            nodes.add(found.nextNode());
        }
        return nodes;
    }

    @Reference
    public void setUsers(JahiaUserManagerService users) {
        this.users = users;
    }

    @Reference
    public void setGroups(JahiaGroupManagerService groups) {
        this.groups = groups;
    }

    @Reference
    public void setConfig(AccountsConfigService config) {
        this.config = config;
    }

    @Override
    public boolean isValidUsername(String username) {
        return username != null && users.isUsernameSyntaxCorrect(username);
    }

    @Override
    public Created create(NewAccount account, Function<String, Pending> pending) throws AccountException {
        try {
            return jcr.get().doExecuteWithSystemSession((JCRCallback<Created>) session -> {
                try {
                    return createIn(session, account, pending);
                } catch (AccountException e) {
                    throw new WrappedAccountException(e);
                }
            });
        } catch (WrappedAccountException e) {
            throw e.unwrap();
        } catch (RepositoryException | RuntimeException e) {
            throw new AccountException(Refusal.FAILED, "the account could not be created", e);
        }
    }

    private Created createIn(JCRSessionWrapper session, NewAccount account, Function<String, Pending> pending)
            throws AccountException, RepositoryException {
        JCRUserNode existing = users.lookupUser(account.username(), session);
        if (existing != null) {
            if (!isExpiredPending(existing, Instant.now())) {
                throw new AccountException(Refusal.TAKEN, "the user name is taken");
            }
            // an account that never answered its activation e-mail does not hold the address for ever
            existing.remove();
            session.save();
        }
        Properties properties = new Properties();
        putIfPresent(properties, "j:email", account.email());
        putIfPresent(properties, "j:firstName", account.firstName());
        putIfPresent(properties, "j:lastName", account.lastName());
        JCRUserNode user = users.createUser(account.username(), account.password(), properties, session);
        if (user == null) {
            throw new AccountException(Refusal.FAILED, "the platform refused to create the user");
        }
        if (!user.isNew()) {
            // createUser answers the EXISTING node when the name is already there (it only adds what is missing): a
            // concurrent sign-up for the same address got there first. Nothing is touched, nothing is granted.
            throw new AccountException(Refusal.TAKEN, "the user name was taken meanwhile");
        }
        String identifier = user.getIdentifier();
        try {
            if (pending != null) {
                lock(user, pending.apply(identifier));
            }
            session.save();
            addToGroup(session, user, account);
            return new Created(identifier, user.getPath());
        } catch (AccountException | RepositoryException | RuntimeException e) {
            rollback(session, identifier);
            throw e;
        }
    }

    private static void lock(JCRUserNode user, Pending pending) throws RepositoryException {
        user.setProperty(LOCKED, true);
        user.addMixin(PENDING_MIXIN);
        user.setProperty(TOKEN_HASH, pending.token().hash());
        Calendar expires = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        expires.setTimeInMillis(pending.expires().toEpochMilli());
        user.setProperty(EXPIRES, expires);
        if (pending.redirectPath() != null && !pending.redirectPath().isBlank()) {
            user.setProperty(REDIRECT, pending.redirectPath());
        }
    }

    private void addToGroup(JCRSessionWrapper session, JCRUserNode user, NewAccount account)
            throws AccountException, RepositoryException {
        String name = account.groupReference();
        if (name == null || name.isBlank()) {
            return;
        }
        name = name.trim();
        if (!config.settings().isAllowedGroup(name)) {
            throw new AccountException(Refusal.GROUP, "the group of the action is not in allowedGroups of org.jahia.modules.formidable.accounts.cfg");
        }
        JCRGroupNode group = resolveGroup(session, name, account.siteKey());
        if (group == null) {
            throw new AccountException(Refusal.GROUP, "the group of the action exists neither globally nor on the form's site");
        }
        if (isBackOffice(session, group.getPath(), "g:" + group.getName())) {
            throw new AccountException(Refusal.GROUP, "the group of the action gives back-office access");
        }
        group.addMember(user);
        session.save();
    }

    /** A plain name: the global group of that name, else the group of that name on the form's site. Never another site's. */
    private JCRGroupNode resolveGroup(JCRSessionWrapper session, String name, String siteKey) {
        if (name.indexOf('/') >= 0 || name.indexOf(':') >= 0) {
            return null;
        }
        // JahiaGroupManagerService.lookupGroup takes the site key FIRST, and null for a global group
        JCRGroupNode global = groups.lookupGroup(null, name, session);
        return global != null || siteKey == null || siteKey.isBlank() ? global : groups.lookupGroup(siteKey, name, session);
    }

    @Override
    public boolean isBackOfficeUser(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        try {
            return jcr.get().doExecuteWithSystemSession(session -> {
                JCRUserNode user = users.lookupUser(username, session);
                if (user == null) {
                    return false;
                }
                return user.isRoot() || isBackOffice(session, user.getPath(), "u:" + user.getName());
            });
        } catch (RepositoryException | RuntimeException e) {
            // fail closed: an account that cannot be checked does not sign in through a form
            log.warn("[formidable-accounts] The back-office access of an account could not be checked: it is treated as back office.", e);
            return true;
        }
    }

    /**
     * Back-office access of a principal: one of the privileged or administrators groups, a member of one (the platform's
     * membership cache is transitive), a server role granted to it or to one of its groups ({@code users}, {@code guest}
     * and {@code site-users} excepted: every account is in them), or, anywhere, a role that gives privileged access
     * ({@code j:privilegedAccess}, on the role or a parent role) granted to it or to one of its groups. The last test is
     * the one the platform's ACL listener makes before it adds a principal to {@code site-privileged}, read here directly:
     * that listener runs after the grant, so the membership alone can lag behind it.
     */
    private boolean isBackOffice(JCRSessionWrapper session, String principalPath, String principalKey) throws RepositoryException {
        List<String> memberships = new ArrayList<>();
        memberships.add(principalPath);
        List<String> owners = groups.getMembershipByPath(principalPath);
        if (owners != null) {
            memberships.addAll(owners);
        }
        Set<String> principals = new HashSet<>();
        principals.add(principalKey);
        for (String path : memberships) {
            if (PRIVILEGED_GLOBAL_GROUPS.contains(path) || path.endsWith(SITE_PRIVILEGED_SUFFIX) || path.endsWith(SITE_ADMINISTRATORS_SUFFIX)) {
                return true;
            }
            String group = groupName(path);
            if (group != null && !EVERYONE_GROUPS.contains(group)) {
                principals.add("g:" + group);
            }
        }
        return holdsServerRole(session, principals) || grantsPrivilegedRole(session, principals);
    }

    private static boolean holdsServerRole(JCRSessionWrapper session, Set<String> principals) throws RepositoryException {
        if (!session.nodeExists(SERVER_ACL)) {
            return false;
        }
        NodeIterator aces = session.getNode(SERVER_ACL).getNodes();
        while (aces.hasNext()) {
            Node ace = aces.nextNode();
            if (ace.hasProperty(PRINCIPAL) && principals.contains(ace.getProperty(PRINCIPAL).getString())
                    && ace.hasProperty(ACE_TYPE) && GRANT.equals(ace.getProperty(ACE_TYPE).getString())) {
                return true;
            }
        }
        return false;
    }

    /** The group name of a global ({@code /groups/x}) or site ({@code /sites/s/groups/x}) group path, else null. */
    private static String groupName(String path) {
        if (path.startsWith(GLOBAL_GROUPS)) {
            return path.substring(GLOBAL_GROUPS.length());
        }
        int at = path.indexOf(SITE_GROUPS);
        return path.startsWith("/sites/") && at > 0 ? path.substring(at + SITE_GROUPS.length()) : null;
    }

    private boolean grantsPrivilegedRole(JCRSessionWrapper session, Set<String> principals) throws RepositoryException {
        StringBuilder sql = new StringBuilder("select * from [jnt:ace] where [j:aceType] = 'GRANT' and (");
        String separator = "";
        for (String principal : principals) {
            sql.append(separator).append("[j:principal] = '").append(literal(principal)).append('\'');
            separator = " or ";
        }
        sql.append(')');
        Set<String> checked = new HashSet<>();
        for (Node ace : queries.run(session, sql.toString(), MAX_ACES)) {
            if (!ace.hasProperty(ROLES)) {
                continue;
            }
            for (Value role : ace.getProperty(ROLES).getValues()) {
                if (checked.add(role.getString()) && isPrivilegedRole(session, role.getString())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A JCR-SQL2 string literal's content: quotes doubled. */
    private static String literal(String value) {
        return value.replace("'", "''");
    }

    /** Whether the role, or one of its parent roles, gives privileged (back-office) access. */
    private boolean isPrivilegedRole(JCRSessionWrapper session, String roleName) throws RepositoryException {
        List<Node> found = queries.run(session,
                "select * from [jnt:role] as r where localname() = '" + literal(roleName) + "' and isdescendantnode(r, ['/roles'])", 1);
        if (found.isEmpty()) {
            return false;
        }
        Node role = found.get(0);
        while (role != null && role.isNodeType(ROLE_TYPE)) {
            if (role.hasProperty(PRIVILEGED_ACCESS) && role.getProperty(PRIVILEGED_ACCESS).getBoolean()) {
                return true;
            }
            role = role.getParent();
        }
        return false;
    }

    @Override
    public boolean holdsPermission(String username, String path, String workspace, String permission) {
        if (username == null || username.isBlank() || path == null) {
            return false;
        }
        String name = username.trim();
        if (SYSTEM_ACCOUNT.equals(name)) {
            return true;
        }
        try {
            return jcr.get().doExecuteWithUserSession(name, workspace, null,
                    session -> session.nodeExists(path) && session.getNode(path).hasPermission(permission));
        } catch (RepositoryException | RuntimeException e) {
            log.debug("[formidable-accounts] The permission {} of {} on {} could not be read.", permission, name, path, e);
            return false;
        }
    }

    private static boolean isExpiredPending(JCRUserNode user, Instant now) throws RepositoryException {
        if (!user.isNodeType(PENDING_MIXIN) || !user.hasProperty(EXPIRES)) {
            return false;
        }
        return !user.getProperty(EXPIRES).getDate().toInstant().isAfter(now);
    }

    private static void putIfPresent(Properties properties, String name, String value) {
        if (value != null && !value.isBlank()) {
            properties.setProperty(name, value.trim());
        }
    }

    private static void rollback(JCRSessionWrapper session, String identifier) {
        try {
            session.refresh(false);
            JCRNodeWrapper node = session.getNodeByIdentifier(identifier);
            node.remove();
            session.save();
        } catch (javax.jcr.ItemNotFoundException e) {
            // the refresh discarded a node that was never saved: nothing is left to remove
            log.debug("[JahiaAccountDirectory] Nothing to remove: the account was never saved.");
        } catch (RepositoryException | RuntimeException e) {
            log.error("[JahiaAccountDirectory] An account that could not be completed could not be removed either: remove the user {} by hand.", identifier, e);
        }
    }

    @Override
    public void delete(String identifier) {
        try {
            jcr.get().doExecuteWithSystemSession((JCRCallback<Void>) session -> {
                session.getNodeByIdentifier(identifier).remove();
                session.save();
                return null;
            });
        } catch (RepositoryException | RuntimeException e) {
            log.error("[JahiaAccountDirectory] The account {} could not be removed: remove it by hand.", identifier, e);
        }
    }

    @Override
    public Optional<String> activate(ActivationToken token, Instant now) {
        try {
            return jcr.get().doExecuteWithSystemSession((JCRCallback<Optional<String>>) session -> {
                JCRNodeWrapper node;
                try {
                    node = session.getNodeByIdentifier(token.identifier());
                } catch (javax.jcr.ItemNotFoundException e) {
                    return Optional.empty();
                }
                if (!node.isNodeType(USER_TYPE) || !node.isNodeType(PENDING_MIXIN) || !node.hasProperty(TOKEN_HASH)
                        || !node.hasProperty(EXPIRES)) {
                    return Optional.empty();
                }
                if (!node.getProperty(EXPIRES).getDate().toInstant().isAfter(now)
                        || !token.matches(node.getProperty(TOKEN_HASH).getString())) {
                    return Optional.empty();
                }
                String redirect = node.hasProperty(REDIRECT) ? node.getProperty(REDIRECT).getString() : "";
                node.setProperty(LOCKED, false);
                node.removeMixin(PENDING_MIXIN);
                session.save();
                return Optional.of(redirect);
            });
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[JahiaAccountDirectory] An activation could not be completed.", e);
            return Optional.empty();
        }
    }

    /** Carries an {@link AccountException} through the unchecked-only callback of the template. */
    private static final class WrappedAccountException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient AccountException wrapped;

        WrappedAccountException(AccountException wrapped) {
            super(wrapped.getMessage(), null, false, false);
            this.wrapped = wrapped;
        }

        AccountException unwrap() {
            return wrapped;
        }
    }
}
