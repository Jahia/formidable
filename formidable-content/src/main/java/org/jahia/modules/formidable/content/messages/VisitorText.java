package org.jahia.modules.formidable.content.messages;

import org.jahia.modules.formidable.engine.api.VisitorMessages;

import java.util.Locale;

/**
 * Every sentence a visitor can read from the Create content action, with its resource-bundle key and an English last
 * resort, so a message always reaches the visitor in some language. The sentences are generic on purpose: they say what
 * the visitor can do about it, never which folder, group or setting is behind it (that goes to the server log).
 *
 * <p>Immutable constants; {@link #in(Locale)} is thread-safe.</p>
 */
public enum VisitorText {

    /** A guest submitted a form that creates content: only signed-in users may. */
    SIGN_IN_REQUIRED("visitor.signInRequired", "Please sign in to send this form."),
    /** No folder is open to this user: no group matches, several do, the subfolder is missing or the user cannot read it. */
    NO_FOLDER("visitor.noFolder", "Your account is not set up to send content here. Please contact your administrator."),
    /** A submitted value does not fit the property it is stored in, or the content could not be created from it. */
    BAD_VALUE("visitor.badValue", "One of the values you entered is not valid. Check the form and try again."),
    /** The author did not write a message for the pair rule. */
    PAIR_REFUSAL("visitor.pairRefusal", "A file is missing: some of the files you sent need a companion file."),
    /** The form is misconfigured: nothing the visitor can do. */
    UNAVAILABLE("visitor.unavailable", "This form is temporarily unavailable. Please try again later."),
    /** The platform failed while storing or publishing; nothing was kept. */
    FAILED("visitor.failed", "We could not save your submission. Please try again later.");

    /** Bundle base name as the module's class loader knows it ({@code resources/formidable-content*.properties}). */
    static final String BUNDLE = "resources.formidable-content";

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
