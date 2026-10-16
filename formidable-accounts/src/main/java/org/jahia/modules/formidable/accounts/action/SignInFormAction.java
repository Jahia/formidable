package org.jahia.modules.formidable.accounts.action;

import org.jahia.modules.formidable.accounts.account.AccountDirectory;
import org.jahia.modules.formidable.accounts.account.Authenticator;
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
import java.util.Locale;
import java.util.Optional;

/**
 * Signs the visitor in: the action of {@code fmdbacc:signInAction}.
 *
 * <p>The sign-in goes through the platform's authentication service ({@link Authenticator}), never by setting session
 * attributes, so it is a real Jahia login: the session is replaced, the login event fires and jExperience merges the
 * visitor's anonymous profile into the user's. The same protection as the sign-up applies ({@link AccountsGuard}).</p>
 *
 * <p>The failure is one sentence for an unknown account and a wrong password alike. The one exception is a locked
 * account, which the platform reports only after verifying the password: the visitor who proved who they are is told
 * the account is not active yet. The password is never logged nor returned.</p>
 *
 * <p>Back-office accounts never sign in here ({@link AccountDirectory#isBackOfficeUser}): this path calls the platform's
 * authentication service directly, so a second factor (UPA) or an SSO policy on the platform's login would not apply.
 * They read the answer of a wrong password.</p>
 *
 * <p>Threading: a singleton, no per-request state in fields.</p>
 */
@Component(service = FormAction.class, immediate = true)
public class SignInFormAction implements ContextualFormAction {

    private static final Logger log = LoggerFactory.getLogger(SignInFormAction.class);

    static final String NODE_TYPE = "fmdbacc:signInAction";

    private AccountDirectory directory;
    private Authenticator authenticator;
    private AccountsGuard guard;
    private final Redirects redirects;

    public SignInFormAction() {
        this.redirects = new Redirects();
    }

    SignInFormAction(Redirects redirects) {
        this.redirects = redirects;
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
    public void setGuard(AccountsGuard guard) {
        this.guard = guard;
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
        String loginField = ActionProps.string(action, "loginField");
        String passwordField = ActionProps.string(action, "passwordField");
        if (loginField == null || passwordField == null) {
            log.error("[formidable-accounts] The Sign in action {} has no login or password field chosen: the submission is refused.", action.getPath());
            throw FormActionException.refusal(503, "sign-in refused: the action is misconfigured", VisitorText.UNAVAILABLE.in(locale));
        }
        String typed = context.parameter(loginField);
        HttpServletRequest request = context.request();
        guard.admitSignIn(request, typed, locale);

        String password = context.sensitiveParameter(passwordField);
        if (password == null && context.parameter(passwordField) != null) {
            log.error("[formidable-accounts] The password field '{}' of {} is not a sensitive field (use a password input): the submission is refused.",
                    passwordField, action.getPath());
            throw FormActionException.refusal(503, "sign-in refused: the action is misconfigured", VisitorText.UNAVAILABLE.in(locale));
        }
        Optional<String> username = UserNames.forSignIn(typed, directory::isValidUsername);
        if (username.isEmpty() || password == null || password.length() > PasswordPolicy.MAX_LENGTH) {
            guard.signInFailed(request, typed);
            throw failure(action, locale);
        }
        if (directory.isBackOfficeUser(username.get())) {
            // a form signs in site visitors; back-office accounts use the platform's login, where MFA and SSO apply (this
            // path would skip them). The visitor reads the answer of a wrong password: the form does not say who is staff.
            log.warn("[formidable-accounts] A back-office account tried to sign in through the form {}: refused.", action.getPath());
            guard.signInFailed(request, typed);
            throw failure(action, locale);
        }

        // computed before the sign-in: it replaces the HTTP session, which closes the sessions bound to it
        Optional<String> redirect = redirects.resolve(context, true);
        Authenticator.Outcome outcome = authenticator.signIn(username.get(), password, context.siteKey(), request, context.response());
        switch (outcome) {
            case SIGNED_IN:
                guard.signInSucceeded(request, typed);
                return redirect.map(FormActionResult::redirect).orElseGet(FormActionResult::none);
            case NOT_ACTIVE:
                guard.signInFailed(request, typed);
                throw FormActionException.refusal(422, "sign-in refused: the account is locked", VisitorText.ACCOUNT_NOT_ACTIVE.in(locale));
            default:
                guard.signInFailed(request, typed);
                throw failure(action, locale);
        }
    }

    private static FormActionException failure(JCRNodeWrapper action, Locale locale) {
        String custom = ActionProps.string(action, "failureMessage");
        return FormActionException.refusal(422, "sign-in refused", custom != null ? custom : VisitorText.SIGN_IN_FAILED.in(locale));
    }
}
