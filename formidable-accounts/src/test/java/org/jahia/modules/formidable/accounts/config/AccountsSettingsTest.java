package org.jahia.modules.formidable.accounts.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountsSettingsTest {

    private static AccountsConfig config(boolean captcha, int perAddress, long addressWindow, int perAccount, long accountWindow,
                                         int keys, String groups, String base, String from) {
        AccountsConfig config = mock(AccountsConfig.class);
        when(config.requireCaptcha()).thenReturn(captcha);
        when(config.maxAttemptsPerAddress()).thenReturn(perAddress);
        when(config.addressWindowSeconds()).thenReturn(addressWindow);
        when(config.maxAttemptsPerAccount()).thenReturn(perAccount);
        when(config.accountWindowSeconds()).thenReturn(accountWindow);
        when(config.maxTrackedKeys()).thenReturn(keys);
        when(config.deniedGroups()).thenReturn(groups);
        when(config.publicBaseUrl()).thenReturn(base);
        when(config.mailFrom()).thenReturn(from);
        return config;
    }

    @Test
    void theDefaultsAreTheDocumentedOnes() {
        // Verifies what runs before any configuration: captcha required, 20 per address in 10 minutes, 5 per account in 15.
        AccountsSettings defaults = AccountsSettings.defaults();

        assertTrue(defaults.requireCaptcha());
        assertEquals(20, defaults.addressLimit());
        assertEquals(Duration.ofSeconds(600), defaults.addressWindow());
        assertEquals(5, defaults.accountLimit());
        assertEquals(Duration.ofSeconds(900), defaults.accountWindow());
        assertEquals(10_000, defaults.maxTrackedKeys());
        assertEquals(Set.of("administrators", "privileged", "site-administrators", "site-privileged", "guest", "users"), defaults.deniedGroups());
    }

    @Test
    void aSettingIsReadFromTheTypedConfigurationAndBadNumbersAreBroughtBackToABound() {
        // Verifies the clamps: a zero or negative value never reaches the limiter, and the base URL loses its slash.
        AccountsSettings settings = AccountsSettings.from(config(false, 0, -5, -1, 0, 3, "Staff, Admins;", "https://x.example//", " no-reply@x.example "));

        assertFalse(settings.requireCaptcha());
        assertEquals(1, settings.addressLimit());
        assertEquals(Duration.ofSeconds(1), settings.addressWindow());
        assertEquals(1, settings.accountLimit());
        assertEquals(Duration.ofSeconds(1), settings.accountWindow());
        assertEquals(100, settings.maxTrackedKeys());
        assertEquals("https://x.example", settings.publicBaseUrl());
        assertEquals("no-reply@x.example", settings.mailFrom());
        assertEquals(Set.of("staff", "admins"), settings.deniedGroups());
    }

    @Test
    void aDeniedGroupIsMatchedByNameOrKeyInAnyCase() {
        // Verifies the comparison the directory makes on the resolved group.
        AccountsSettings settings = AccountsSettings.defaults();

        assertTrue(settings.isDeniedGroup("Administrators"));
        assertTrue(settings.isDeniedGroup(" privileged "));
        assertFalse(settings.isDeniedGroup("customers"));
        assertFalse(settings.isDeniedGroup(null));
    }

    @Test
    void nullsAreTolerated() {
        // Verifies the record never holds a null string or set.
        AccountsSettings settings = new AccountsSettings(true, 1, Duration.ofSeconds(1), 1, Duration.ofSeconds(1), 100, null, null, null);

        assertTrue(settings.deniedGroups().isEmpty());
        assertEquals("", settings.publicBaseUrl());
        assertEquals("", settings.mailFrom());
    }

    @Test
    void theServiceReplacesItsSettingsWhenTheConfigurationChanges() {
        // Verifies the component: defaults first, then the new configuration as one object.
        AccountsConfigService service = new AccountsConfigService();
        assertTrue(service.settings().requireCaptcha());

        service.apply(config(false, 7, 60, 3, 120, 500, "", "", ""));

        assertFalse(service.settings().requireCaptcha());
        assertEquals(7, service.settings().addressLimit());
    }
}
