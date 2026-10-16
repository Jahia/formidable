package org.jahia.modules.formidable.accounts.security;

import org.jahia.modules.formidable.accounts.account.AccountDirectory;
import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.modules.formidable.accounts.config.AccountsSettings;
import org.jahia.modules.formidable.accounts.messages.VisitorText;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * The abuse protection both actions and the verification servlet share, in one place so that the three cannot drift:
 * the form must be CAPTCHA protected (unless the operator allowed otherwise for development), the site must have the
 * module enabled, the action must have been last saved by someone holding {@value #ACTIONS_PERMISSION} on it, and the
 * attempts are counted per remote address and per account in memory.
 *
 * <p>Threading: a singleton; the limiter is synchronized and the settings are read once per call from the
 * configuration service's immutable snapshot. Every refusal is a {@link FormActionException#refusal} whose text is
 * generic and identical for an account that exists and one that does not: a limit must not become an oracle.</p>
 */
@Component(service = AccountsGuard.class, immediate = true)
public class AccountsGuard {

    /** The Jahia module id: what a site's installed modules list holds. */
    static final String MODULE_ID = "formidable-accounts";

    /**
     * The permission whoever configures an account action must hold on it (declared under {@code site-admin} by the
     * module's {@code permissions.xml}, so site administrators hold it and an administrator can grant it to another role).
     */
    public static final String ACTIONS_PERMISSION = "formidableAccountsActions";
    private static final String LAST_MODIFIED_BY = "jcr:lastModifiedBy";
    private static final String LIVE = "live";

    private static final Logger log = LoggerFactory.getLogger(AccountsGuard.class);
    private static final String UNKNOWN_ADDRESS = "unknown";
    private static final int MAX_ACCOUNT_KEY = 255;

    private AccountsConfigService config;
    private AccountDirectory directory;
    private LongSupplier clock = System::currentTimeMillis;
    private volatile AttemptLimiter limiter;
    private volatile int limiterBound;

    public AccountsGuard() {
    }

    AccountsGuard(AccountsConfigService config, AccountDirectory directory, LongSupplier clock) {
        this.config = config;
        this.directory = directory;
        this.clock = clock;
    }

    @Reference
    public void setConfig(AccountsConfigService config) {
        this.config = config;
    }

    @Reference
    public void setDirectory(AccountDirectory directory) {
        this.directory = directory;
    }

    /**
     * Refuses a form the actions must not run on: no CAPTCHA while the configuration requires one, a site that did not
     * enable the module, or an action last saved by someone without {@value #ACTIONS_PERMISSION} on it (any editor of
     * the form can add the action; only who holds the permission makes it run, and an edit by anyone else stops it until
     * an authorised person saves it again). All are configuration errors: logged with the path, and answered with a
     * generic "unavailable" the visitor can do nothing about.
     *
     * @param context the submission
     * @throws FormActionException the refusal, status 503
     */
    public void requireProtectedForm(FormActionContext context) throws FormActionException {
        AccountsSettings settings = config.settings();
        JCRNodeWrapper form = context.formNode();
        try {
            if (settings.requireCaptcha() && (form == null || !form.isNodeType(FmdbMixin.CAPTCHA_PROTECTED_FORM))) {
                log.error("[formidable-accounts] The form {} carries an account action but no CAPTCHA, which the configuration requires: "
                        + "the submission is refused. Turn the CAPTCHA option on for the form (or set requireCaptcha=false in "
                        + "org.jahia.modules.formidable.accounts.cfg on a development instance only).", pathOf(form));
                throw unavailable(context.locale());
            }
            if (form != null) {
                JCRSiteNode site = form.getResolveSite();
                if (site == null || !site.getInstalledModulesWithAllDependencies().contains(MODULE_ID)) {
                    log.error("[formidable-accounts] The site of the form {} has not enabled the module {}: the submission is refused.",
                            pathOf(form), MODULE_ID);
                    throw unavailable(context.locale());
                }
            }
            requireAuthorisedAction(context.actionNode(), context.locale());
        } catch (RepositoryException e) {
            log.error("[formidable-accounts] The form {} could not be inspected: the submission is refused.", pathOf(form), e);
            throw unavailable(context.locale());
        }
    }

    private void requireAuthorisedAction(JCRNodeWrapper action, Locale locale) throws RepositoryException, FormActionException {
        if (action == null) {
            throw unavailable(locale);
        }
        String author = action.hasProperty(LAST_MODIFIED_BY) ? action.getProperty(LAST_MODIFIED_BY).getString() : null;
        if (!directory.holdsPermission(author, action.getPath(), workspaceOf(action), ACTIONS_PERMISSION)) {
            log.error("[formidable-accounts] The action {} was last saved by '{}', who does not hold the permission {} on it: the "
                    + "submission is refused. A site administrator (or a role granted {}) must save the action again.",
                    action.getPath(), author, ACTIONS_PERMISSION, ACTIONS_PERMISSION);
            throw unavailable(locale);
        }
    }

    private static String workspaceOf(JCRNodeWrapper node) {
        try {
            return node.getSession() == null ? LIVE : node.getSession().getWorkspace().getName();
        } catch (RepositoryException | RuntimeException e) {
            return LIVE;
        }
    }

    /**
     * Admits a sign-up attempt: counts it against the remote address and against the e-mail typed (both, whatever the
     * outcome: an activation e-mail is a mailbox a script could flood).
     *
     * @param request    the submission request
     * @param accountKey the e-mail as typed, may be blank
     * @param locale     the submission's locale
     * @throws FormActionException the refusal, status 429, when either limit is reached
     */
    public void admitSignUp(HttpServletRequest request, String accountKey, Locale locale) throws FormActionException {
        AccountsSettings settings = config.settings();
        AttemptLimiter limiter = limiterFor(settings);
        String address = "addr:" + addressOf(request);
        String account = accountKey("signup", accountKey);
        long addressWindow = settings.addressWindow().toMillis();
        long accountWindow = settings.accountWindow().toMillis();
        if (limiter.isBlocked(address, settings.addressLimit(), addressWindow)
                || (account != null && limiter.isBlocked(account, settings.accountLimit(), accountWindow))) {
            log.warn("[formidable-accounts] A sign-up was refused by a rate limit.");
            throw tooMany(locale);
        }
        limiter.record(address, addressWindow);
        if (account != null) {
            limiter.record(account, accountWindow);
        }
    }

    /**
     * Admits a sign-in attempt: counts it against the remote address; the account is only charged for a failure
     * ({@link #signInFailed}), so that someone typing the right password is never slowed by their own successes.
     *
     * @param request    the submission request
     * @param accountKey the e-mail or user name as typed, may be blank
     * @param locale     the submission's locale
     * @throws FormActionException the refusal, status 429, when either limit is reached
     */
    public void admitSignIn(HttpServletRequest request, String accountKey, Locale locale) throws FormActionException {
        AccountsSettings settings = config.settings();
        AttemptLimiter limiter = limiterFor(settings);
        String address = "addr:" + addressOf(request);
        String account = signInKey(request, accountKey);
        if (limiter.isBlocked(address, settings.addressLimit(), settings.addressWindow().toMillis())
                || (account != null && limiter.isBlocked(account, settings.accountLimit(), settings.accountWindow().toMillis()))) {
            log.warn("[formidable-accounts] A sign-in was refused by a rate limit.");
            throw tooMany(locale);
        }
        limiter.record(address, settings.addressWindow().toMillis());
    }

    /** Charges a failed sign-in to the account that was typed, existing or not, from this address. */
    public void signInFailed(HttpServletRequest request, String accountKey) {
        AccountsSettings settings = config.settings();
        String account = signInKey(request, accountKey);
        if (account != null) {
            limiterFor(settings).record(account, settings.accountWindow().toMillis());
        }
    }

    /** Clears the failures charged to an account from this address: it has just signed in. */
    public void signInSucceeded(HttpServletRequest request, String accountKey) {
        String account = signInKey(request, accountKey);
        if (account != null) {
            limiterFor(config.settings()).reset(account);
        }
    }

    /**
     * A sign-in's account counter is per account AND address: someone guessing a password from elsewhere exhausts their
     * own counter, never the owner's (counted per account alone, five wrong guesses by anyone locked the owner out of the
     * form). The per-address limit still bounds each address over every account.
     */
    private static String signInKey(HttpServletRequest request, String typed) {
        String account = accountKey("signin", typed);
        return account == null ? null : account + "|" + addressOf(request);
    }

    /**
     * Admits a hit on the verification servlet: counted per remote address only (a token is 256 random bits).
     *
     * @param request the request
     * @return {@code false} when the address is over its limit
     */
    public boolean admitVerification(HttpServletRequest request) {
        AccountsSettings settings = config.settings();
        AttemptLimiter limiter = limiterFor(settings);
        String address = "addr:verify:" + addressOf(request);
        if (limiter.isBlocked(address, settings.addressLimit(), settings.addressWindow().toMillis())) {
            return false;
        }
        limiter.record(address, settings.addressWindow().toMillis());
        return true;
    }

    /** One limiter at a time, rebuilt (counts forgotten) only when the configured bound changes. */
    private AttemptLimiter limiterFor(AccountsSettings settings) {
        AttemptLimiter current = limiter;
        if (current == null || limiterBound != settings.maxTrackedKeys()) {
            synchronized (this) {
                if (limiter == null || limiterBound != settings.maxTrackedKeys()) {
                    limiter = new AttemptLimiter(settings.maxTrackedKeys(), clock);
                    limiterBound = settings.maxTrackedKeys();
                }
                current = limiter;
            }
        }
        return current;
    }

    private static String accountKey(String kind, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        String normalised = typed.trim().toLowerCase(Locale.ROOT);
        return "acct:" + kind + ":" + (normalised.length() > MAX_ACCOUNT_KEY ? normalised.substring(0, MAX_ACCOUNT_KEY) : normalised);
    }

    private static String addressOf(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? UNKNOWN_ADDRESS : address;
    }

    private static String pathOf(JCRNodeWrapper form) {
        return form == null ? "(unknown form)" : form.getPath();
    }

    private static FormActionException unavailable(Locale locale) {
        return FormActionException.refusal(503, "account action refused: misconfigured form", VisitorText.UNAVAILABLE.in(locale));
    }

    private static FormActionException tooMany(Locale locale) {
        return FormActionException.refusal(429, "account action refused: rate limit", VisitorText.TOO_MANY_ATTEMPTS.in(locale));
    }
}
