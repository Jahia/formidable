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

import javax.jcr.RepositoryException;
import java.time.Instant;
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
 * validated everything (captcha, rate limits, password rule, group not denied) before it calls.
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

    private static final Logger log = LoggerFactory.getLogger(JahiaAccountDirectory.class);

    private final Supplier<JCRTemplate> jcr;
    private JahiaUserManagerService users;
    private JahiaGroupManagerService groups;
    private AccountsConfigService config;

    /** Production constructor: the platform's {@code JCRTemplate}. */
    public JahiaAccountDirectory() {
        this(JCRTemplate::getInstance);
    }

    JahiaAccountDirectory(Supplier<JCRTemplate> jcr) {
        this.jcr = jcr;
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
        String reference = account.groupReference();
        if (reference == null || reference.isBlank()) {
            return;
        }
        JCRGroupNode group = resolveGroup(session, reference.trim(), account.siteKey());
        if (group == null) {
            throw new AccountException(Refusal.GROUP, "the group of the action does not exist");
        }
        if (config.settings().isDeniedGroup(group.getName()) || config.settings().isDeniedGroup(group.getGroupKey())) {
            throw new AccountException(Refusal.GROUP, "the group of the action is one a form can never add a visitor to");
        }
        group.addMember(user);
        session.save();
    }

    private JCRGroupNode resolveGroup(JCRSessionWrapper session, String reference, String siteKey) {
        if (reference.startsWith("/")) {
            if (!reference.startsWith("/groups/") && !reference.startsWith("/sites/")) {
                return null;
            }
            return groups.lookupGroupByPath(reference, session);
        }
        int colon = reference.indexOf(':');
        if (colon > 0) {
            return groups.lookupGroup(reference.substring(0, colon), reference.substring(colon + 1), session);
        }
        JCRGroupNode global = groups.lookupGroup(reference, null, session);
        return global != null || siteKey == null || siteKey.isBlank() ? global : groups.lookupGroup(reference, siteKey, session);
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
