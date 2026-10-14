package org.jahia.modules.formidable.engine.api;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Resolves a text the visitor reads from the resource bundle of the module that wrote the action, in the
 * submission's locale. An action throws {@link FormActionException#refusal} or returns
 * {@link FormActionResult#message} with the result, so a French form answers in French.
 *
 * <p>Stateless. The lookup does not fall back to the server's default locale: an English visitor on a French
 * server reads the base bundle, as everywhere else in Formidable.</p>
 */
public final class VisitorMessages {

    private VisitorMessages() {
    }

    /**
     * @param loader         the class loader of the module that owns the bundle ({@code MyAction.class.getClassLoader()})
     * @param bundleBaseName the bundle base name as the classpath knows it, e.g. {@code resources.formidable-accounts}
     * @param key            the key
     * @param locale         the submission's locale; English when {@code null}
     * @param fallback       what to show when no bundle answers: every message must reach the visitor in some language
     * @return the translated text, or {@code fallback}
     */
    public static String resolve(ClassLoader loader, String bundleBaseName, String key, Locale locale, String fallback) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(bundleBaseName, locale == null ? Locale.ENGLISH : locale, loader,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            String text = bundle.containsKey(key) ? bundle.getString(key) : null;
            return text == null || text.isBlank() ? fallback : text;
        } catch (MissingResourceException e) {
            return fallback;
        }
    }
}
