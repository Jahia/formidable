package org.jahia.modules.formidable.accounts.messages;

import org.jahia.modules.formidable.engine.api.VisitorMessages;

import java.util.Locale;

/**
 * Every sentence a visitor can read from these actions, with its resource-bundle key and an English last resort, so a
 * message always reaches the visitor in some language. The failure sentences are generic on purpose: a visitor, or a
 * script, learns nothing about which accounts exist from what the form says.
 *
 * <p>Immutable constants; {@link #in(Locale)} is thread-safe.</p>
 */
public enum VisitorText {

    /** Any failure of a sign-up that must not tell why: the address is taken, the account could not be created. */
    GENERIC_FAILURE("visitor.genericFailure", "We could not complete your request. Check your details and try again."),
    /** The e-mail is not an address; independent of whether an account exists. */
    INVALID_EMAIL("visitor.invalidEmail", "Enter a valid e-mail address."),
    /** The password breaks the rule; independent of whether an account exists. */
    PASSWORD_POLICY("visitor.passwordPolicy", "The password does not meet the requirements."),
    /** A rate limit: the same sentence for an account that exists and one that does not. */
    TOO_MANY_ATTEMPTS("visitor.tooManyAttempts", "Too many attempts. Please try again later."),
    /** A failed sign-in, whatever the cause: unknown account, wrong password. */
    SIGN_IN_FAILED("visitor.signInFailed", "Wrong e-mail or password."),
    /** Said only once the password was verified: the account exists and is locked. */
    ACCOUNT_NOT_ACTIVE("visitor.accountNotActive", "Your account is not active yet. Check your mailbox for the activation e-mail."),
    /** The form is misconfigured (no CAPTCHA, a missing field): nothing the visitor can do. */
    UNAVAILABLE("visitor.unavailable", "This form is temporarily unavailable. Please try again later."),
    /** Shown after a sign-up that awaits its e-mail. */
    CHECK_MAILBOX("visitor.checkMailbox", "Thank you. We sent you an e-mail: follow its link to activate your account."),
    /** Subject of the activation e-mail. */
    ACTIVATION_SUBJECT("mail.activationSubject", "Activate your account"),
    /** Body of the activation e-mail; {@code ${link}} and {@code ${hours}} are replaced. */
    ACTIVATION_BODY("mail.activationBody",
            "Hello,\n\nFollow this link to activate your account:\n\n${link}\n\nThe link expires in ${hours} hours. "
                    + "If you did not ask for an account, ignore this message."),
    /** The page the verification servlet shows when the link worked and no page was chosen. */
    VERIFY_DONE("verify.done", "Your account is active. You can now sign in."),
    /** The page the verification servlet shows for any link that does not work: expired, used, unknown. */
    VERIFY_INVALID("verify.invalid", "This activation link is not valid or has expired.");

    /** Bundle base name as the module's class loader knows it ({@code resources/formidable-accounts*.properties}). */
    static final String BUNDLE = "resources.formidable-accounts";

    private final String key;
    private final String english;

    VisitorText(String key, String english) {
        this.key = key;
        this.english = english;
    }

    /** @return the resource-bundle key */
    public String key() {
        return key;
    }

    /** @return the English last resort, used when no bundle answers */
    public String english() {
        return english;
    }

    /**
     * @param locale the submission's locale; English when {@code null}
     * @return the sentence in that locale, never {@code null}
     */
    public String in(Locale locale) {
        return VisitorMessages.resolve(VisitorText.class.getClassLoader(), BUNDLE, key, locale, english);
    }
}
