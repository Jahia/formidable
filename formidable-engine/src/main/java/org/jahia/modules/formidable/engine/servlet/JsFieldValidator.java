package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.javascript.modules.engine.sdk.JSServerExtensionInvoker;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs JavaScript-declared field validators against a submitted value, on the server, so that a client
 * that bypasses browser constraints cannot inject out-of-range values.
 *
 * <p>Field modules (e.g. formidable-extended-inputs) register validators in JavaScript with
 * {@code registerFormFieldValidator({ nodeType }, (node, value, locale) => …)}, which stores a registry
 * entry of type {@value #REGISTRY_TYPE}. This bridge finds those entries via the js-modules SDK
 * ({@link JSServerExtensionInvoker}), matches them to the field node type, invokes the JS callback with
 * the field node, the submitted value and the locale tag, and returns the violation messages.
 *
 * <p>Fails closed: a validator that throws yields a generic violation rather than letting the value through.
 * When the SDK service is absent (older js-modules engine deployed), no JS validation runs and submission
 * proceeds — Formidable degrades gracefully.
 */
final class JsFieldValidator {

    static final String REGISTRY_TYPE = "formidable-field-validator";

    private static final Logger log = LoggerFactory.getLogger(JsFieldValidator.class);

    private JsFieldValidator() {
    }

    /**
     * Returns the violation messages produced by the JS validators registered for {@code fieldNode}'s type
     * against the submitted {@code values} (all raw values submitted for that field). Empty when valid, when
     * no validator matches, or when the SDK is unavailable.
     */
    static List<String> validate(JSServerExtensionInvoker invoker, JCRNodeWrapper fieldNode,
                                 List<String> values, Locale locale) {
        if (invoker == null || fieldNode == null || values == null || values.isEmpty()) {
            return List.of();
        }
        String localeTag = locale != null ? locale.toLanguageTag() : null;

        return invoker.forEach(REGISTRY_TYPE, (entry, jsInvoker) -> {
            Object nodeType = entry.get("nodeType");
            if (nodeType == null || !isNodeType(fieldNode, nodeType.toString())) {
                return null;
            }
            Object validate = entry.get("validate");
            if (validate == null) {
                return null;
            }
            String key = String.valueOf(entry.get("key"));
            for (String value : values) {
                String message = runOne(jsInvoker, validate, fieldNode, value, localeTag, key);
                if (message != null) {
                    return message;
                }
            }
            return null;
        });
    }

    /** Invokes a single validator for one value; fails closed on error. Returns the violation message or null. */
    private static String runOne(JSServerExtensionInvoker.Invoker jsInvoker, Object validate,
                                 JCRNodeWrapper fieldNode, String value, String localeTag, String key) {
        Object result;
        try {
            result = jsInvoker.call(validate, fieldNode, value, localeTag);
        } catch (RuntimeException e) {
            log.error("[JsFieldValidator] JS field validator '{}' threw; rejecting value (fail closed)", key, e);
            return "The submitted value could not be validated.";
        }
        return firstMessage(result);
    }

    /** Interprets a converted validator result: {@code {message}}, {@code [{message}, …]}, or null/other. */
    private static String firstMessage(Object result) {
        if (result instanceof Map<?, ?> violation) {
            return messageOf(violation);
        }
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> violation) {
                    String message = messageOf(violation);
                    if (message != null) {
                        return message;
                    }
                }
            }
        }
        return null;
    }

    private static String messageOf(Map<?, ?> violation) {
        Object message = violation.get("message");
        return message instanceof String s && !s.isBlank() ? s : null;
    }

    private static boolean isNodeType(JCRNodeWrapper node, String nodeType) {
        try {
            return node.isNodeType(nodeType);
        } catch (RepositoryException e) {
            log.warn("[JsFieldValidator] Unable to check node type {} during JS validation", nodeType, e);
            return false;
        }
    }
}
