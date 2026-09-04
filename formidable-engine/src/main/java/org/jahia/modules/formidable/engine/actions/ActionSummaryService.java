package org.jahia.modules.formidable.engine.actions;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.SelectorType;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializerService;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the authoring surfaces (the Page Builder's actions zone) show about an action node
 * besides its title: its <em>key parameter</em>, the first property its type declares after
 * the title that a contributor can read at a glance — a small text or a choice, never a long
 * text, a flag or a list. A choice is shown by the label its choicelist initializer gives it,
 * the same label the Content Editor showed when the contributor picked it.
 * <p>
 * The rule reads the type declaration, so every action type gets it for free: the engine's
 * e-mail actions show their recipient, the forward action its target's label, and a
 * third-party action whatever it declares first — nothing to register anywhere.
 */
@Component(service = ActionSummaryService.class, immediate = true)
public class ActionSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ActionSummaryService.class);

    /** The key parameter of an action: property name and display value. */
    public record KeyParameter(String name, String value) {}

    /**
     * @param action an action node (fmdbmix:formAction)
     * @param locale the locale the display value is resolved in
     * @return the key parameter, or null when the type declares none or the property is not set
     */
    public KeyParameter keyParameter(JCRNodeWrapper action, Locale locale) throws RepositoryException {
        ExtendedPropertyDefinition definition = firstTellingProperty(action.getPrimaryNodeType());
        if (definition == null || !action.hasProperty(definition.getName())) {
            return null;
        }
        String raw = action.getProperty(definition.getName()).getString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new KeyParameter(definition.getName(), displayValue(action, definition, raw, locale));
    }

    /** The first telling property in CND declaration order, or null. */
    static ExtendedPropertyDefinition firstTellingProperty(ExtendedNodeType type) {
        for (ExtendedPropertyDefinition definition : type.getDeclaredPropertyDefinitions()) {
            if (isTelling(definition)) {
                return definition;
            }
        }
        return null;
    }

    /**
     * A property a contributor reads at a glance: a single, visible, unprotected string
     * edited as a small text or a choice. Long texts (text area, rich text), flags, numbers,
     * dates, references and lists are not it, nor are the technical jcr:/j: properties (the
     * title among them).
     */
    static boolean isTelling(ExtendedPropertyDefinition definition) {
        return isTelling(definition.getName(), definition.isHidden(), definition.isProtected(),
                definition.isMultiple(), definition.getRequiredType(), definition.getSelector());
    }

    /** The criterion on the property's declared attributes, kept free of Jahia types for the tests. */
    static boolean isTelling(String name, boolean hidden, boolean protectedProperty, boolean multiple,
            int requiredType, int selector) {
        if (name == null || name.startsWith("jcr:") || name.startsWith("j:")) {
            return false;
        }
        if (hidden || protectedProperty || multiple || requiredType != PropertyType.STRING) {
            return false;
        }
        return selector == SelectorType.SMALLTEXT || selector == SelectorType.CHOICELIST;
    }

    private String displayValue(JCRNodeWrapper node, ExtendedPropertyDefinition definition, String raw, Locale locale) {
        if (definition.getSelector() != SelectorType.CHOICELIST) {
            return raw;
        }
        try {
            Map<String, ChoiceListInitializer> initializers = ChoiceListInitializerService.getInstance().getInitializers();
            Map<String, Object> context = new HashMap<>();
            context.put("contextNode", node);
            context.put("contextParent", node.getParent());
            List<ChoiceListValue> values = new ArrayList<>();
            for (Map.Entry<String, String> option : definition.getSelectorOptions().entrySet()) {
                ChoiceListInitializer initializer = initializers.get(option.getKey());
                if (initializer != null) {
                    values = initializer.getChoiceListValues(definition, option.getValue(), values, locale, context);
                }
            }
            for (ChoiceListValue value : values) {
                if (value.getValue() != null && raw.equals(value.getValue().getString())) {
                    return value.getDisplayName();
                }
            }
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[ActionSummaryService] Could not resolve the label of {}='{}' on {}", definition.getName(), raw, node.getPath(), e);
        }
        return raw;
    }
}
