package org.jahia.modules.formidable.accounts.account;

import org.jahia.modules.formidable.accounts.TestSupport;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.AccountException;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Created;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.NewAccount;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Pending;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Refusal;
import org.jahia.modules.formidable.accounts.activation.ActivationToken;
import org.jahia.modules.formidable.accounts.config.AccountsConfig;
import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRGroupNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Value;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JahiaAccountDirectoryTest {

    private static final String PASSWORD = "S3cret!pw-never-logged";
    private static final String USER_ID = "11111111-2222-4333-8444-555555555555";

    private JCRSessionWrapper session;
    private JahiaUserManagerService users;
    private JahiaGroupManagerService groups;
    private JCRUserNode user;
    private JahiaAccountDirectory directory;
    /** What the ACE query answers, and the role nodes by name: empty unless a test fills them. */
    private final List<Node> grantedAces = new java.util.ArrayList<>();
    private final java.util.Map<String, Node> roles = new java.util.HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        session = mock(JCRSessionWrapper.class);
        users = mock(JahiaUserManagerService.class);
        groups = mock(JahiaGroupManagerService.class);
        user = mock(JCRUserNode.class);
        when(user.getIdentifier()).thenReturn(USER_ID);
        when(user.getPath()).thenReturn("/users/a/d/a/ada@example.com");
        when(user.isNew()).thenReturn(true);
        JCRTemplate template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSession(any(JCRCallback.class))).thenAnswer(call -> ((JCRCallback<?>) call.getArgument(0)).doInJCR(session));
        AccountsConfigService config = TestSupport.config(true, 20, 5);
        directory = new JahiaAccountDirectory(() -> template, (querySession, sql, limit) -> sql.contains("[jnt:ace]") ? grantedAces
                : roles.entrySet().stream().filter(role -> sql.contains("localname() = '" + role.getKey() + "'")).map(java.util.Map.Entry::getValue).toList());
        directory.setUsers(users);
        directory.setGroups(groups);
        directory.setConfig(config);
        when(users.createUser(anyString(), anyString(), any(Properties.class), any(JCRSessionWrapper.class))).thenReturn(user);
        when(session.getNodeByIdentifier(USER_ID)).thenReturn(user);
    }

    private static JCRNodeIteratorWrapper iterator(List<Node> nodes) {
        java.util.Iterator<Node> it = nodes.iterator();
        return mock(JCRNodeIteratorWrapper.class, call -> switch (call.getMethod().getName()) {
            case "hasNext" -> it.hasNext();
            case "nextNode", "next" -> it.next();
            default -> RETURNS_DEFAULTS.answer(call);
        });
    }

    private static NewAccount account(String group) {
        return new NewAccount("ada@example.com", PASSWORD, "ada@example.com", "Ada", "Lovelace", group, "mysite");
    }

    private static JCRPropertyWrapper date(Instant instant) throws Exception {
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(instant.toEpochMilli());
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getDate()).thenReturn(calendar);
        return property;
    }

    private static JCRPropertyWrapper string(String value) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getString()).thenReturn(value);
        return property;
    }

    @Test
    void anAccountIsCreatedThroughThePlatformSoThePlatformHashesThePassword() throws Exception {
        // Verifies the creation: user name, password as typed (the platform hashes it), e-mail and names as properties.
        Created created = directory.create(account(null), null);

        assertEquals(USER_ID, created.identifier());
        ArgumentCaptor<Properties> properties = ArgumentCaptor.forClass(Properties.class);
        verify(users).createUser(eq("ada@example.com"), eq(PASSWORD), properties.capture(), eq(session));
        assertEquals("ada@example.com", properties.getValue().getProperty("j:email"));
        assertEquals("Ada", properties.getValue().getProperty("j:firstName"));
        assertEquals("Lovelace", properties.getValue().getProperty("j:lastName"));
        verify(session).save();
        verify(user, never()).addMixin(anyString());
    }

    @Test
    void theAccountToStringNeverPrintsThePassword() {
        // Verifies the log hygiene of the value type.
        assertFalse(account(null).toString().contains(PASSWORD));
    }

    @Test
    void anExistingUserNameIsTaken() throws Exception {
        // Verifies the refusal: nothing is created over an existing active account.
        JCRUserNode existing = mock(JCRUserNode.class);
        when(users.lookupUser("ada@example.com", session)).thenReturn(existing);

        AccountException e = assertThrows(AccountException.class, () -> directory.create(account(null), null));

        assertEquals(Refusal.TAKEN, e.refusal());
        verify(users, never()).createUser(anyString(), anyString(), any(Properties.class), any(JCRSessionWrapper.class));
    }

    @Test
    void aNodeThatCreateUserAnswersButDidNotCreateIsNeverTouched() throws Exception {
        // Verifies the trap of JahiaUserManagerService.createUser: for a name that exists (a concurrent sign-up got there
        // first) it answers the existing node. The account is refused as taken: no property written, no group joined,
        // and the existing user is not removed.
        when(user.isNew()).thenReturn(false);
        JCRGroupNode customers = mock(JCRGroupNode.class);
        when(groups.lookupGroup(null, "customers", session)).thenReturn(customers);

        AccountException e = assertThrows(AccountException.class, () -> directory.create(account("customers"), null));

        assertEquals(Refusal.TAKEN, e.refusal());
        verify(user, never()).setProperty(anyString(), anyString());
        verify(user, never()).remove();
        verify(customers, never()).addMember(any(JCRNodeWrapper.class));
        verify(session, never()).save();
    }

    @Test
    void anAccountWhoseActivationExpiredIsReplacedButOneStillWaitingIsTaken() throws Exception {
        // Verifies an abandoned sign-up does not hold the address for ever, and a live one does.
        JCRUserNode expired = mock(JCRUserNode.class);
        when(expired.isNodeType(JahiaAccountDirectory.PENDING_MIXIN)).thenReturn(true);
        when(expired.hasProperty(JahiaAccountDirectory.EXPIRES)).thenReturn(true);
        JCRPropertyWrapper past = date(Instant.now().minusSeconds(60));
        when(expired.getProperty(JahiaAccountDirectory.EXPIRES)).thenReturn(past);
        when(users.lookupUser("ada@example.com", session)).thenReturn(expired);

        directory.create(account(null), null);
        verify(expired).remove();

        JCRUserNode waiting = mock(JCRUserNode.class);
        when(waiting.isNodeType(JahiaAccountDirectory.PENDING_MIXIN)).thenReturn(true);
        when(waiting.hasProperty(JahiaAccountDirectory.EXPIRES)).thenReturn(true);
        JCRPropertyWrapper future = date(Instant.now().plusSeconds(3600));
        when(waiting.getProperty(JahiaAccountDirectory.EXPIRES)).thenReturn(future);
        when(users.lookupUser("ada@example.com", session)).thenReturn(waiting);

        assertEquals(Refusal.TAKEN, assertThrows(AccountException.class, () -> directory.create(account(null), null)).refusal());
        verify(waiting, never()).remove();
    }

    @Test
    void aRefusalOfThePlatformIsAFailure() {
        // Verifies createUser answering null (the platform refused) is reported as a failure, not a success.
        try {
            when(users.createUser(anyString(), anyString(), any(Properties.class), any(JCRSessionWrapper.class))).thenReturn(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertEquals(Refusal.FAILED, assertThrows(AccountException.class, () -> directory.create(account(null), null)).refusal());
    }

    @Test
    void anAccountAwaitingActivationIsCreatedLockedWithOnlyTheHashOfItsToken() throws Exception {
        // Verifies the activation record: locked, the mixin, the hash (never the secret), the expiry, the page to go to.
        ActivationToken[] issued = new ActivationToken[1];
        Instant expires = Instant.now().plusSeconds(172_800);

        directory.create(account(null), id -> {
            issued[0] = ActivationToken.generate(id);
            return new Pending(issued[0], expires, "/sites/mysite/active.html");
        });

        assertEquals(USER_ID, issued[0].identifier());
        verify(user).setProperty(JahiaAccountDirectory.LOCKED, true);
        verify(user).addMixin(JahiaAccountDirectory.PENDING_MIXIN);
        verify(user).setProperty(JahiaAccountDirectory.TOKEN_HASH, issued[0].hash());
        verify(user, never()).setProperty(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(issued[0].secret()));
        ArgumentCaptor<Calendar> stored = ArgumentCaptor.forClass(Calendar.class);
        verify(user).setProperty(eq(JahiaAccountDirectory.EXPIRES), stored.capture());
        assertEquals(expires.toEpochMilli(), stored.getValue().getTimeInMillis());
        verify(user).setProperty(JahiaAccountDirectory.REDIRECT, "/sites/mysite/active.html");
    }

    private JCRGroupNode group(String name, String path) {
        JCRGroupNode group = mock(JCRGroupNode.class);
        when(group.getName()).thenReturn(name);
        when(group.getPath()).thenReturn(path);
        return group;
    }

    @Test
    void theVisitorJoinsAnAllowedGlobalGroupOrTheAllowedGroupOfTheFormsSite() throws Exception {
        // Verifies the allow list of jahia-private#5909 finding 1: a listed name resolves globally first, then on the
        // form's own site, and membership is saved.
        JCRGroupNode customers = group("customers", "/groups/customers");
        when(groups.lookupGroup(null, "customers", session)).thenReturn(customers);
        JCRGroupNode members = group("members", "/sites/mysite/groups/members");
        when(groups.lookupGroup("mysite", "members", session)).thenReturn(members);

        directory.create(account("customers"), null);
        directory.create(account(" members "), null);

        verify(customers).addMember(user);
        verify(members).addMember(user);
        verify(session, times(4)).save();
    }

    @Test
    void aGroupTheAdministratorDidNotListIsRefusedWhateverItIsAndTheAccountRemoved() throws Exception {
        // Verifies finding 1: a contributor cannot type their way into another group (a privileged one, another site's
        // by key or path); nothing is looked up for a name outside the list.
        for (String typed : new String[]{"administrators", "orvane-signal-editors", "members:othersite", "/sites/othersite/groups/members", "/groups/customers"}) {
            AccountException e = assertThrows(AccountException.class, () -> directory.create(account(typed), null), typed);
            assertEquals(Refusal.GROUP, e.refusal(), typed);
        }
        verify(user, times(5)).remove();
        verify(groups, never()).lookupGroupByPath(anyString(), any(JCRSessionWrapper.class));
        verify(groups, never()).lookupGroup(eq("othersite"), anyString(), any(JCRSessionWrapper.class));
    }

    @Test
    void anAllowedGroupThatGivesBackOfficeAccessIsRefusedAndTheAccountRemoved() throws Exception {
        // Verifies the second check of finding 1: listed by mistake, a group that is (through its memberships) in a
        // site-privileged group, or that holds a server role, still adds nobody.
        JCRGroupNode customers = group("customers", "/groups/customers");
        when(groups.lookupGroup(null, "customers", session)).thenReturn(customers);
        when(groups.getMembershipByPath("/groups/customers")).thenReturn(List.of("/sites/mysite/groups/site-privileged"));
        JCRGroupNode members = group("members", "/groups/members");
        when(groups.lookupGroup(null, "members", session)).thenReturn(members);
        serverAcl(ace("g:members", "GRANT"));

        assertEquals(Refusal.GROUP, assertThrows(AccountException.class, () -> directory.create(account("customers"), null)).refusal());
        assertEquals(Refusal.GROUP, assertThrows(AccountException.class, () -> directory.create(account("members"), null)).refusal());
        verify(customers, never()).addMember(any(JCRNodeWrapper.class));
        verify(members, never()).addMember(any(JCRNodeWrapper.class));
        verify(user, times(2)).remove();
    }

    @Test
    void aListedGroupThatDoesNotExistIsRefusedAndTheAccountRemoved() throws Exception {
        // Verifies a configuration mistake: allowed, but neither a global group nor one of the form's site.
        assertEquals(Refusal.GROUP, assertThrows(AccountException.class, () -> directory.create(account("customers"), null)).refusal());
        verify(user).remove();
    }

    @Test
    void aBackOfficeAccountIsRecognisedThroughItsGroupsOrAServerRoleAndAVisitorIsNot() throws Exception {
        // Verifies finding 4: the form sign-in refuses root, members of privileged or of a site's site-privileged (where
        // the platform puts every editing role), and holders of a server role; a member of an ordinary group passes.
        JCRUserNode editor = principal("editor", "/users/editor");
        JCRUserNode marketer = principal("marketer", "/users/marketer");
        JCRUserNode reader = principal("reader", "/users/reader");
        JCRUserNode root = principal("root", "/users/root");
        when(root.isRoot()).thenReturn(true);
        when(groups.getMembershipByPath("/users/editor")).thenReturn(List.of("/groups/users", "/sites/mysite/groups/site-privileged", "/groups/privileged"));
        when(groups.getMembershipByPath("/users/marketer")).thenReturn(List.of("/groups/users", "/groups/marketing"));
        when(groups.getMembershipByPath("/users/reader")).thenReturn(List.of("/groups/users", "/groups/customers", "/sites/mysite/groups/site-users"));
        serverAcl(ace("g:users", "GRANT"), ace("g:marketing", "GRANT"), ace("u:reader", "DENY"));

        assertTrue(directory.isBackOfficeUser("editor"));
        assertTrue(directory.isBackOfficeUser("marketer"));
        assertTrue(directory.isBackOfficeUser("root"));
        assertFalse(directory.isBackOfficeUser("reader"), "every account is in users, which holds reader on the server");
        assertFalse(directory.isBackOfficeUser("nobody"));
    }

    @Test
    void anAccountJustGrantedAnEditingRoleIsBackOfficeBeforeThePlatformAddsItToSitePrivileged() throws Exception {
        // Verifies the role test: the platform's ACL listener adds a principal to site-privileged after the grant, so the
        // directory reads the grants themselves. A role with j:privilegedAccess, on itself or a parent role, granted to the
        // user or to one of its groups, is back office; a reader role is not.
        principal("fresh-admin", "/users/fresh-admin");
        principal("gated-reader", "/users/gated-reader");
        when(groups.getMembershipByPath("/users/fresh-admin")).thenReturn(List.of("/groups/users"));
        when(groups.getMembershipByPath("/users/gated-reader")).thenReturn(List.of("/groups/users", "/sites/mysite/groups/suppliers"));
        Node siteAdministrator = role(true, null);
        Node reader = role(false, null);
        roles.put("site-administrator", siteAdministrator);
        roles.put("reader", reader);
        grantedAces.add(grant("site-administrator"));

        assertTrue(directory.isBackOfficeUser("fresh-admin"));

        grantedAces.clear();
        grantedAces.add(grant("reader"));
        assertFalse(directory.isBackOfficeUser("gated-reader"));

        Node editorInChief = role(false, role(true, null));
        roles.put("editor-in-chief", editorInChief);
        grantedAces.clear();
        grantedAces.add(grant("editor-in-chief"));
        assertTrue(directory.isBackOfficeUser("gated-reader"), "a sub-role inherits its parent's privileged access");
    }

    private static Node role(boolean privileged, Node parent) throws Exception {
        Node role = mock(Node.class);
        when(role.isNodeType("jnt:role")).thenReturn(true);
        when(role.hasProperty("j:privilegedAccess")).thenReturn(true);
        JCRPropertyWrapper flag = TestSupport.bool(privileged);
        when(role.getProperty("j:privilegedAccess")).thenReturn(flag);
        Node top = mock(Node.class);
        when(role.getParent()).thenReturn(parent != null ? parent : top);
        return role;
    }

    private static Node grant(String roleName) throws Exception {
        Node ace = mock(Node.class);
        Value value = mock(Value.class);
        when(value.getString()).thenReturn(roleName);
        when(ace.hasProperty("j:roles")).thenReturn(true);
        Property plain = mock(Property.class);
        when(plain.getValues()).thenReturn(new Value[]{value});
        when(ace.getProperty("j:roles")).thenReturn(plain);
        return ace;
    }

    @Test
    void anAccountThatCannotBeCheckedCountsAsBackOffice() throws Exception {
        // Verifies fail-closed: a repository failure while checking keeps the account off the form sign-in.
        principal("ada", "/users/ada");
        when(groups.getMembershipByPath("/users/ada")).thenThrow(new IllegalStateException("cache down"));

        assertTrue(directory.isBackOfficeUser("ada"));
    }

    private JCRUserNode principal(String name, String path) throws Exception {
        JCRUserNode principal = mock(JCRUserNode.class);
        when(principal.getName()).thenReturn(name);
        when(principal.getPath()).thenReturn(path);
        when(users.lookupUser(name, session)).thenReturn(principal);
        return principal;
    }

    private void serverAcl(Node... aces) throws Exception {
        JCRNodeWrapper acl = mock(JCRNodeWrapper.class);
        when(session.nodeExists("/j:acl")).thenReturn(true);
        when(session.getNode("/j:acl")).thenReturn(acl);
        when(acl.getNodes()).thenAnswer(call -> iterator(List.of(aces)));
    }

    private static Node ace(String principal, String type) throws Exception {
        Node ace = mock(Node.class);
        JCRPropertyWrapper p = string(principal);
        JCRPropertyWrapper t = string(type);
        when(ace.hasProperty("j:principal")).thenReturn(true);
        when(ace.getProperty("j:principal")).thenReturn(p);
        when(ace.hasProperty("j:aceType")).thenReturn(true);
        when(ace.getProperty("j:aceType")).thenReturn(t);
        return ace;
    }

    @Test
    void anActivationLinkUnlocksTheAccountAndHandsBackTheChosenPage() throws Exception {
        // Verifies the activation: right token, in time, user node with the mixin: unlocked, mixin removed, saved.
        ActivationToken token = ActivationToken.generate(USER_ID);
        pending(token, Instant.now().plusSeconds(60), "/sites/mysite/active.html");

        Optional<String> redirect = directory.activate(token, Instant.now());

        assertEquals(Optional.of("/sites/mysite/active.html"), redirect);
        verify(user).setProperty(JahiaAccountDirectory.LOCKED, false);
        verify(user).removeMixin(JahiaAccountDirectory.PENDING_MIXIN);
        verify(session).save();
    }

    @Test
    void anActivationLinkThatIsWrongExpiredUnknownOrNotAUserChangesNothing() throws Exception {
        // Verifies every way an activation fails: the same empty answer, nothing unlocked.
        ActivationToken token = ActivationToken.generate(USER_ID);
        pending(token, Instant.now().plusSeconds(60), "");

        assertTrue(directory.activate(ActivationToken.generate(USER_ID), Instant.now()).isEmpty(), "another secret");
        assertTrue(directory.activate(token, Instant.now().plusSeconds(120)).isEmpty(), "expired");
        when(user.isNodeType(JahiaAccountDirectory.USER_TYPE)).thenReturn(false);
        assertTrue(directory.activate(token, Instant.now()).isEmpty(), "not a user node");
        when(session.getNodeByIdentifier(USER_ID)).thenThrow(new ItemNotFoundException("gone"));
        assertTrue(directory.activate(token, Instant.now()).isEmpty(), "unknown");

        verify(user, never()).setProperty(JahiaAccountDirectory.LOCKED, false);
    }

    @Test
    void anAccountWithoutAPageAnswersAnEmptyRedirect() throws Exception {
        // Verifies the success without a chosen page: present, empty string (the servlet shows its own page).
        ActivationToken token = ActivationToken.generate(USER_ID);
        pending(token, Instant.now().plusSeconds(60), null);

        assertEquals(Optional.of(""), directory.activate(token, Instant.now()));
    }

    @Test
    void anAccountCanBeRemovedByItsIdentifier() throws Exception {
        // Verifies the compensation used when the activation e-mail could not be sent.
        directory.delete(USER_ID);

        verify(user).remove();
        verify(session).save();
    }

    private void pending(ActivationToken token, Instant expires, String redirect) throws Exception {
        when(user.isNodeType(JahiaAccountDirectory.USER_TYPE)).thenReturn(true);
        when(user.isNodeType(JahiaAccountDirectory.PENDING_MIXIN)).thenReturn(true);
        when(user.hasProperty(JahiaAccountDirectory.TOKEN_HASH)).thenReturn(true);
        when(user.hasProperty(JahiaAccountDirectory.EXPIRES)).thenReturn(true);
        JCRPropertyWrapper hash = string(token.hash());
        JCRPropertyWrapper expiry = date(expires);
        when(user.getProperty(JahiaAccountDirectory.TOKEN_HASH)).thenReturn(hash);
        when(user.getProperty(JahiaAccountDirectory.EXPIRES)).thenReturn(expiry);
        if (redirect != null) {
            JCRPropertyWrapper page = string(redirect);
            when(user.hasProperty(JahiaAccountDirectory.REDIRECT)).thenReturn(true);
            when(user.getProperty(JahiaAccountDirectory.REDIRECT)).thenReturn(page);
        }
    }
}
