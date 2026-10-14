package org.jahia.modules.formidable.accounts.account;

import org.jahia.modules.formidable.accounts.account.AccountDirectory.AccountException;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Created;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.NewAccount;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Pending;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Refusal;
import org.jahia.modules.formidable.accounts.activation.ActivationToken;
import org.jahia.modules.formidable.accounts.config.AccountsConfig;
import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.services.content.JCRCallback;
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
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
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

    @BeforeEach
    void setUp() throws Exception {
        session = mock(JCRSessionWrapper.class);
        users = mock(JahiaUserManagerService.class);
        groups = mock(JahiaGroupManagerService.class);
        user = mock(JCRUserNode.class);
        when(user.getIdentifier()).thenReturn(USER_ID);
        when(user.getPath()).thenReturn("/users/a/d/a/ada@example.com");
        JCRTemplate template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSession(any(JCRCallback.class))).thenAnswer(call -> ((JCRCallback<?>) call.getArgument(0)).doInJCR(session));
        AccountsConfigService config = new AccountsConfigService();
        directory = new JahiaAccountDirectory(() -> template);
        directory.setUsers(users);
        directory.setGroups(groups);
        directory.setConfig(config);
        when(users.createUser(anyString(), anyString(), any(Properties.class), any(JCRSessionWrapper.class))).thenReturn(user);
        when(session.getNodeByIdentifier(USER_ID)).thenReturn(user);
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

    @Test
    void theVisitorJoinsAGlobalGroupByNameASiteGroupByKeyOrAGroupByPath() throws Exception {
        // Verifies the three ways the contributor can name a group, and that membership is saved.
        JCRGroupNode customers = mock(JCRGroupNode.class);
        when(customers.getName()).thenReturn("customers");
        when(customers.getGroupKey()).thenReturn("customers");
        when(groups.lookupGroup("customers", null, session)).thenReturn(customers);
        JCRGroupNode siteGroup = mock(JCRGroupNode.class);
        when(siteGroup.getName()).thenReturn("members");
        when(siteGroup.getGroupKey()).thenReturn("members:mysite");
        when(groups.lookupGroup("members", "mysite", session)).thenReturn(siteGroup);
        JCRGroupNode byPath = mock(JCRGroupNode.class);
        when(byPath.getName()).thenReturn("vip");
        when(byPath.getGroupKey()).thenReturn("vip");
        when(groups.lookupGroupByPath("/groups/vip", session)).thenReturn(byPath);

        directory.create(account("customers"), null);
        directory.create(account("members:mysite"), null);
        directory.create(account("/groups/vip"), null);

        verify(customers).addMember(user);
        verify(siteGroup).addMember(user);
        verify(byPath).addMember(user);
        verify(session, times(6)).save();
    }

    @Test
    void aGroupThatGrantsPrivilegesIsRefusedAndTheAccountRemoved() throws Exception {
        // Verifies the deny list: a contributor cannot hand out administrators from a public form, and the
        // half-made account does not stay behind.
        JCRGroupNode administrators = mock(JCRGroupNode.class);
        when(administrators.getName()).thenReturn("administrators");
        when(administrators.getGroupKey()).thenReturn("administrators");
        when(groups.lookupGroup("administrators", null, session)).thenReturn(administrators);

        AccountException e = assertThrows(AccountException.class, () -> directory.create(account("administrators"), null));

        assertEquals(Refusal.GROUP, e.refusal());
        verify(administrators, never()).addMember(any(JCRNodeWrapper.class));
        verify(user).remove();
    }

    @Test
    void aMissingGroupOrAPathOutsideTheGroupsTreeIsRefusedAndTheAccountRemoved() throws Exception {
        // Verifies two group mistakes: unknown group, and a path that is not under /groups or /sites.
        assertEquals(Refusal.GROUP, assertThrows(AccountException.class, () -> directory.create(account("nobody"), null)).refusal());
        assertEquals(Refusal.GROUP, assertThrows(AccountException.class, () -> directory.create(account("/users/root"), null)).refusal());
        verify(user, times(2)).remove();
        verify(groups, never()).lookupGroupByPath(eq("/users/root"), any(JCRSessionWrapper.class));
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
