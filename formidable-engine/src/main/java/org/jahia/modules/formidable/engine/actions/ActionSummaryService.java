package org.jahia.modules.formidable.engine.actions;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
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
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.Arrays;
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

    /** Selector option (and context key) listing the properties a choicelist depends on, as the Content Editor names it. */
    private static final String DEPENDENT_PROPERTIES = "dependentProperties";

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
            Map<String, String> options = definition.getSelectorOptions();
            // The same context the Content Editor hands the initializers (core ContentDefinitionHelper):
            // the type, the node and its parent, and the current values of the properties the
            // choicelist depends on, under their names — so a third-party initializer that reads
            // them behaves here as it does in the editor. The chain starts from null, as there.
            Map<String, Object> context = new HashMap<>();
            context.put("contextType", node.getPrimaryNodeType());
            context.put("contextNode", node);
            context.put("contextParent", node.getParent());
            // split() returns null for a null value (the option declared without a list).
            String[] dependentNames = StringUtils.split(options.get(DEPENDENT_PROPERTIES), ',');
            if (dependentNames != null) {
                List<String> dependentProperties = Arrays.asList(dependentNames);
                context.put(DEPENDENT_PROPERTIES, dependentProperties);
                for (String dependentProperty : dependentProperties) {
                    String name = dependentProperty.trim();
                    if (node.hasProperty(name)) {
                        context.put(name, currentValues(node.getProperty(name)));
                    }
                }
            }
            List<ChoiceListValue> values = null;
            for (Map.Entry<String, String> option : options.entrySet()) {
                ChoiceListInitializer initializer = initializers.get(option.getKey());
                if (initializer != null) {
                    values = initializer.getChoiceListValues(definition, option.getValue(), values, locale, context);
                }
            }
            return labelOf(raw, values == null ? List.of() : values);
        } catch (RepositoryException | RuntimeException e) {
            log.warn("[ActionSummaryService] Could not resolve the label of {} on {}", definition.getName(), node.getPath(), e);
        }
        return raw;
    }

    /** The current values of a property, as the Content Editor passes dependent values: strings, one per value. */
    private static List<String> currentValues(JCRPropertyWrapper property) throws RepositoryException {
        List<String> values = new ArrayList<>();
        if (property.isMultiple()) {
            for (Value value : property.getValues()) {
                values.add(value.getString());
            }
        } else {
            values.add(property.getValue().getString());
        }
        return values;
    }

    /**
     * The display name of the choice whose stored value is {@code raw}, or {@code raw} itself
     * when no choice matches (a target removed from the configuration, a stale value) or the
     * matching choice has no label: the card then shows the identifier rather than nothing.
     */
    static String labelOf(String raw, List<ChoiceListValue> values) throws RepositoryException {
        for (ChoiceListValue value : values) {
            if (value.getValue() != null && raw.equals(value.getValue().getString())) {
                String label = value.getDisplayName();
                return label != null && !label.isBlank() ? label : raw;
            }
        }
        return raw;
    }
}
