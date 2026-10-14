package org.jahia.modules.formidable.accounts.config;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

/**
 * Holds the current {@link AccountsSettings}, replaced as one object whenever {@code
 * org.jahia.modules.formidable.accounts.cfg} changes.
 *
 * <p>Threading: a singleton read from request threads; the only field is one {@code volatile} reference, so a reader
 * takes {@link #settings()} once per request and sees a consistent set of values. Never throws on a bad value.</p>
 */
@Component(service = AccountsConfigService.class, configurationPid = AccountsConfig.PID, immediate = true)
@Designate(ocd = AccountsConfig.class)
public class AccountsConfigService {

    private volatile AccountsSettings settings = AccountsSettings.defaults();

    /** Reads the configuration on activation and again on every change, without restarting the component. */
    @Activate
    @Modified
    public void apply(AccountsConfig config) {
        settings = AccountsSettings.from(config);
    }

    /** @return the settings in force; read once per request and keep the reference */
    public AccountsSettings settings() {
        return settings;
    }
}
