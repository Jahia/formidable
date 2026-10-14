package org.jahia.modules.formidable.accounts.action;

import org.jahia.bin.Jahia;
import org.jahia.modules.formidable.accounts.account.AccountDirectory;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.AccountException;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Created;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.NewAccount;
import org.jahia.modules.formidable.accounts.account.AccountDirectory.Pending;
import org.jahia.modules.formidable.accounts.account.Authenticator;
import org.jahia.modules.formidable.accounts.activation.ActivationMail;
import org.jahia.modules.formidable.accounts.activation.ActivationMailer;
import org.jahia.modules.formidable.accounts.activation.ActivationToken;
import org.jahia.modules.formidable.accounts.config.AccountsConfigService;
import org.jahia.modules.formidable.accounts.messages.VisitorText;
import org.jahia.modules.formidable.accounts.security.AccountsGuard;
import org.jahia.modules.formidable.accounts.security.PasswordPolicy;
import org.jahia.modules.formidable.accounts.security.UserNames;
import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Creates the account of the visitor who submitted the form: the action of {@code fmdbacc:createAccountAction}.
 *
 * <p>What it does, in order: refuses a form that is not CAPTCHA protected or a site that did not enable the module
 * ({@link AccountsGuard}); counts the attempt per remote address and per e-mail; reads the e-mail from its field and
 * the password from its <em>sensitive</em> field (the engine hands it to an action that declares
 * {@link #consumesSensitiveValues()} and to no other); derives the user name from the e-mail with the platform's own
 * syntax check; holds the password to the contributor's rule; creates the user, which the platform hashes, adds it to
 * the configured group, and either signs it in or, when activation is on, leaves it locked and e-mails a link.</p>
 *
 * <p>What it never does: write, return or log the password; say whether an address is already registered (a taken
 * name, a refused group and a platform failure all read {@link VisitorText#GENERIC_FAILURE}); carry
 * {@code fmdbmix:readOnlyCompatibleAction}, since it writes.</p>
 *
 * <p>Threading: a singleton, no per-request state in fields; everything comes from the {@link FormActionContext}.</p>
 */
@Component(service = FormAction.class, immediate = true)
public class CreateAccountFormAction implements ContextualFormAction {

    private static final Logger log = LoggerFactory.getLogger(CreateAccountFormAction.class);

    static final String NODE_TYPE = "fmdbacc:createAccountAction";
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final long DEFAULT_ACTIVATION_HOURS = 48;
    private static final long MAX_ACTIVATION_HOURS = 24 * 30;

    private AccountDirectory directory;
    private Authenticator authenticator;
    private ActivationMailer mailer;
    private AccountsGuard guard;
    private AccountsConfigService config;
    private final Redirects redirects;
    private final Supplier<String> platformContextPath;

    public CreateAccountFormAction() {
        this(new Redirects(), Jahia::getContextPath);
    }

    CreateAccountFormAction(Redirects redirects, Supplier<String> platformContextPath) {
        this.redirects = redirects;
        this.platformContextPath = platformContextPath;
    }

    @Reference
    public void setDirectory(AccountDirectory directory) {
        this.directory = directory;
    }

    @Reference
    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Reference
    public void setMailer(ActivationMailer mailer) {
        this.mailer = mailer;
    }

    @Reference
    public void setGuard(AccountsGuard guard) {
        this.guard = guard;
    }

    @Reference
    public void setConfig(AccountsConfigService config) {
        this.config = config;
    }

    @Override
    public String getNodeType() {
        return NODE_TYPE;
    }

    /** The password arrives as a sensitive value: this action is one of the two the engine hands it to. */
    @Override
    public boolean consumesSensitiveValues() {
        return true;
    }

    @Override
    public FormActionResult execute(FormActionContext context) throws FormActionException {
        Locale locale = context.locale();
        guard.requireProtectedForm(context);

        JCRNodeWrapper action = context.actionNode();
        String emailField = ActionProps.string(action, "emailField");
        String passwordField = ActionProps.string(action, "passwordField");
        if (emailField == null || passwordField == null) {
            log.error("[formidable-accounts] The Create account action {} has no e-mail or password field chosen: the submission is refused.", action.getPath());
            throw unavailable(locale);
        }
        String email = context.parameter(emailField);
        guard.admitSignUp(context.request(), email, locale);

        String password = context.sensitiveParameter(passwordField);
        if (password == null && context.parameter(passwordField) != null) {
            // the author picked an ordinary field: the engine kept nothing secret there, and this action will not take
            // a password from a field that is stored, mailed and sent to jExperience like any other
            log.error("[formidable-accounts] The password field '{}' of {} is not a sensitive field (use a password input): the submission is refused.",
                    passwordField, action.getPath());
            throw unavailable(locale);
        }
        Optional<String> username = email == null || !validEmail(email) ? Optional.empty() : UserNames.forRegistration(email, directory::isValidUsername);
        if (username.isEmpty()) {
            throw FormActionException.refusal(422, "sign-up refused: the e-mail is not usable", VisitorText.INVALID_EMAIL.in(locale));
        }
        if (!PasswordPolicy.accepts(password, ActionProps.string(action, "passwordPattern"))) {
            throw FormActionException.refusal(422, "sign-up refused: the password does not meet the rule", VisitorText.PASSWORD_POLICY.in(locale));
        }

        boolean activation = ActionProps.bool(action, "activationRequired", false);
        long hours = Math.max(1, Math.min(MAX_ACTIVATION_HOURS, ActionProps.number(action, "activationHours", DEFAULT_ACTIVATION_HOURS)));
        if (activation && !mailer.isAvailable()) {
            log.error("[formidable-accounts] {} requires e-mail activation but Jahia's mail service is unavailable or disabled: the submission is refused.", action.getPath());
            throw unavailable(locale);
        }
        boolean signIn = !activation && ActionProps.bool(action, "signIn", true);
        // computed before the sign-in: it replaces the HTTP session, which closes the sessions bound to it
        Optional<String> redirect = redirects.resolve(context, signIn);
        Optional<String> activationPage = activation ? redirects.page(context, action, "activationPage") : Optional.empty();

        NewAccount account = new NewAccount(username.get(), password, email.trim(),
                field(context, ActionProps.string(action, "firstNameField")), field(context, ActionProps.string(action, "lastNameField")),
                ActionProps.string(action, "group"), context.siteKey());
        AtomicReference<ActivationToken> issued = new AtomicReference<>();
        Instant expires = Instant.now().plus(Duration.ofHours(hours));
        Created created;
        try {
            created = directory.create(account, activation ? identifier -> {
                ActivationToken token = ActivationToken.generate(identifier);
                issued.set(token);
                return new Pending(token, expires, activationPage.orElse(null));
            } : null);
        } catch (AccountException e) {
            // the reason is for the log; the visitor reads one sentence whatever it was, a taken address included
            log.warn("[formidable-accounts] An account was not created ({}).", e.refusal());
            throw FormActionException.refusal(422, "sign-up refused: " + e.refusal(), VisitorText.GENERIC_FAILURE.in(locale), e);
        }

        if (activation) {
            return awaitActivation(context, action, email.trim(), created, issued.get(), hours, redirect);
        }
        if (signIn) {
            Authenticator.Outcome outcome = authenticator.signIn(username.get(), password, context.siteKey(), context.request(), context.response());
            if (outcome != Authenticator.Outcome.SIGNED_IN) {
                // the account exists: the visitor signs in by hand; this is not a reason to fail the sign-up
                log.warn("[formidable-accounts] The account {} was created but the automatic sign-in was not accepted ({}).", created.identifier(), outcome);
                return FormActionResult.none();
            }
        }
        log.info("[formidable-accounts] The account {} was created{}.", created.identifier(), signIn ? " and signed in" : "");
        return redirect.map(FormActionResult::redirect).orElseGet(FormActionResult::none);
    }

    private FormActionResult awaitActivation(FormActionContext context, JCRNodeWrapper action, String email, Created created,
                                             ActivationToken token, long hours, Optional<String> redirect) throws FormActionException {
        Locale locale = context.locale();
        HttpServletRequest request = context.request();
        String link = ActivationMail.link(config.settings().publicBaseUrl(), request.getScheme(), request.getServerName(),
                request.getServerPort(), platformContextPath.get(), token);
        ActivationMail mail = ActivationMail.compose(
                ActionProps.string(action, "activationSubject") != null ? ActionProps.string(action, "activationSubject") : VisitorText.ACTIVATION_SUBJECT.in(locale),
                ActionProps.string(action, "activationMessage") != null ? ActionProps.string(action, "activationMessage") : VisitorText.ACTIVATION_BODY.in(locale),
                link, hours);
        try {
            mailer.send(config.settings().mailFrom(), email, mail.subject(), mail.text(), mail.html());
        } catch (ActivationMailer.MailUnavailableException e) {
            // an account nobody can activate is worse than no account: remove it, so the visitor can try again
            directory.delete(created.identifier());
            log.error("[formidable-accounts] The activation e-mail of {} could not be sent: the account was removed.", created.identifier(), e);
            throw FormActionException.refusal(503, "sign-up refused: the activation e-mail could not be sent", VisitorText.UNAVAILABLE.in(locale), e);
        }
        log.info("[formidable-accounts] The account {} was created and awaits its activation.", created.identifier());
        String pending = ActionProps.string(action, "pendingMessage");
        FormActionResult result = FormActionResult.message(pending != null ? pending : VisitorText.CHECK_MAILBOX.in(locale));
        return redirect.map(result::withRedirect).orElse(result);
    }

    private static String field(FormActionContext context, String fieldName) {
        return fieldName == null ? null : context.parameter(fieldName);
    }

    private static boolean validEmail(String email) {
        String trimmed = email.trim();
        return trimmed.length() <= MAX_EMAIL_LENGTH && EMAIL_SHAPE.matcher(trimmed).matches();
    }

    private static FormActionException unavailable(Locale locale) {
        return FormActionException.refusal(503, "sign-up refused: the action is misconfigured", VisitorText.UNAVAILABLE.in(locale));
    }
}
