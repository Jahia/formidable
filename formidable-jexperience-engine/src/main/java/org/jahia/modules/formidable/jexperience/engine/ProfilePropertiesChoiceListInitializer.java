package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.formidable.engine.api.ChoiceOptionsResolver;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.utils.i18n.Messages;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The {@code formidableJExperienceProfileProperties} choicelist: the profile properties a
 * field can be mapped to, filtered on the field's shape so that the dropdown and the mapping
 * rule never disagree. An unreachable jCustomer, or a schema with no property of the field's
 * kind, yields one explanatory entry with an empty value, never a blank or broken dropdown. The
 * field's current mapping is always offered, whatever the list says: the Content Editor resets a
 * value it cannot find among the constraints, and a save during a jCustomer outage must not wipe
 * a mapping the author never touched.
 */
@Component(service = ModuleChoiceListInitializer.class, immediate = true)
public class ProfilePropertiesChoiceListInitializer implements ModuleChoiceListInitializer {

    public static final String KEY = "formidableJExperienceProfileProperties";

    static final String BUNDLE = "resources.formidable-jexperience-engine";
    static final String UNAVAILABLE_KEY = "formidableJExperienceProfileProperties.unavailable";
    static final String NONE_KEY = "formidableJExperienceProfileProperties.none";
    static final String KEPT_KEY = "formidableJExperienceProfileProperties.kept";
    static final String PROPERTY = "jExperienceProfileProperty";
    /** The value property the Content Editor reads to pre-select an entry (jcontent, registerChoiceList initValue). */
    static final String DEFAULT_PROPERTY = "defaultProperty";

    // the keys jcontent's editor puts in the initializer context
    static final String CONTEXT_NODE = "contextNode";
    static final String CONTEXT_PARENT = "contextParent";
    static final String CONTEXT_TYPE = "contextType";
    // the choice-field properties the choicelist declares as dependentProperties, sent unsaved on a re-query
    static final String OPTIONS_PROPERTY = "options";
    static final String OPTIONS_MODE_PROPERTY = "optionsMode";
    static final String MANUAL_MODE = "manual";
    private static final List<String> SOURCED_OPTIONS_MIXINS = List.of("fmdbmix:sourcedOptions", "fmdbmix:categoryOptions", "fmdbmix:contentOptions");

    private static final Logger log = LoggerFactory.getLogger(ProfilePropertiesChoiceListInitializer.class);

    @Reference
    private ProfilePropertyCatalog catalog;

    // the engine counts a field's choices as its views render them
    @Reference
    private ChoiceOptionsResolver optionsResolver;

    public ProfilePropertiesChoiceListInitializer() {
    }

    ProfilePropertiesChoiceListInitializer(ProfilePropertyCatalog catalog) {
        this(catalog, (field, languageTag) -> OptionalInt.empty());
    }

    ProfilePropertiesChoiceListInitializer(ProfilePropertyCatalog catalog, ChoiceOptionsResolver optionsResolver) {
        this.catalog = catalog;
        this.optionsResolver = optionsResolver;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition definition, String param,
                                                     List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        if (context == null) {
            return List.of();
        }
        try {
            Optional<FieldShape> shape = shapeOf(context);
            String siteKey = siteKeyOf(context);
            if (shape.isEmpty() || siteKey == null) {
                return List.of();
            }
            return choices(shape.get(), siteKey, locale, storedOf(context));
        } catch (RepositoryException e) {
            log.warn("[ProfilePropertiesChoiceListInitializer] Could not read the field being edited: {}", e.getMessage());
            return List.of();
        }
    }

    List<ChoiceListValue> choices(FieldShape shape, String siteKey, Locale locale, Optional<String> stored) {
        List<ChoiceListValue> offered;
        try {
            List<ChoiceListValue> compatible = catalog.profileProperties(siteKey).stream()
                    .filter(property -> shape.accepts(property.valueTypeId(), property.multivalued()))
                    .map(property -> new ChoiceListValue(property.label(), property.name()))
                    .toList();
            offered = compatible.isEmpty() ? messageEntry(noneMessage(locale)) : compatible;
        } catch (ProfilePropertiesUnavailableException e) {
            log.warn("[ProfilePropertiesChoiceListInitializer] No profile properties for site '{}': {}", siteKey, e.getMessage());
            offered = messageEntry(unavailableMessage(locale));
        }
        return withStored(offered, stored, locale);
    }

    /**
     * The current mapping of the field, first in the list when the list does not carry it: the
     * Content Editor resets a value it cannot find among the constraints, and neither a jCustomer
     * outage nor a property gone from the schema may wipe a mapping behind the author's back. The
     * entry says the mapping is kept as is; the author keeps it or picks another entry.
     */
    private List<ChoiceListValue> withStored(List<ChoiceListValue> offered, Optional<String> stored, Locale locale) {
        if (stored.isEmpty() || offered.stream().anyMatch(entry -> stored.get().equals(stringValue(entry)))) {
            return offered;
        }
        List<ChoiceListValue> withKept = new ArrayList<>(offered.size() + 1);
        withKept.add(new ChoiceListValue(stored.get() + " " + keptMessage(locale), stored.get()));
        withKept.addAll(offered);
        return withKept;
    }

    private static String stringValue(ChoiceListValue entry) {
        try {
            return entry.getValue() == null ? null : entry.getValue().getString();
        } catch (RepositoryException e) {
            return null;
        }
    }

    /**
     * The one entry a dropdown shows when it has nothing to offer: the message as its label, an empty
     * value (saving it stores nothing), and pre-selected — the Content Editor picks a value flagged
     * {@code defaultProperty} as the field's initial value, so the author reads the message in the
     * closed select instead of finding it in the list.
     */
    static List<ChoiceListValue> messageEntry(String message) {
        ChoiceListValue entry = new ChoiceListValue(message, "");
        entry.addProperty(DEFAULT_PROPERTY, "true");
        return List.of(entry);
    }

    private Optional<FieldShape> shapeOf(Map<String, Object> context) throws RepositoryException {
        Optional<Boolean> pendingMultiple = pendingMultiple(context);
        if (context.get(CONTEXT_NODE) instanceof JCRNodeWrapper node) {
            return FieldShapes.infer(node, pendingMultiple, choiceCountOf(context, node));
        }
        // a field being created: its type is known, its properties only as the editor holds them
        if (context.get(CONTEXT_TYPE) instanceof NodeType type) {
            return FieldShapes.infer(type, pendingMultiple.orElse(false), choiceCountOf(context, null));
        }
        return Optional.empty();
    }

    /**
     * How many choices the field offers, for the checkbox rule: the manual options the editor holds
     * unsaved when it re-asks the list (a change of {@code options} or {@code optionsMode}), else
     * the stored state counted as the view counts it, through the engine. A switch to a sourced
     * mode that is not saved yet leaves the count unknown — a group — until the save resolves it.
     */
    OptionalInt choiceCountOf(Map<String, Object> context, JCRNodeWrapper node) throws RepositoryException {
        Optional<String> pendingMode = pendingString(context, OPTIONS_MODE_PROPERTY);
        boolean manual = pendingMode.isPresent() ? MANUAL_MODE.equals(pendingMode.get()) : node == null || !usesASource(node);
        if (!manual) {
            return node == null || pendingMode.isPresent() ? OptionalInt.empty() : countStored(node);
        }
        if (context.get(OPTIONS_PROPERTY) instanceof Collection<?> options) {
            return OptionalInt.of((int) options.stream().filter(option -> option != null && !String.valueOf(option).isBlank()).count());
        }
        return node == null ? OptionalInt.empty() : countStored(node);
    }

    private OptionalInt countStored(JCRNodeWrapper node) throws RepositoryException {
        String language = node.getLanguage();
        return optionsResolver.countChoices(node, language != null ? language : node.getResolveSite().getDefaultLanguage());
    }

    private static boolean usesASource(JCRNodeWrapper node) throws RepositoryException {
        for (String mixin : SOURCED_OPTIONS_MIXINS) {
            if (node.isNodeType(mixin)) {
                return true;
            }
        }
        return false;
    }

    /** A dependent property's unsaved value as jcontent sends it: a string, or a list holding one. */
    private static Optional<String> pendingString(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value instanceof Collection<?> values) {
            value = values.isEmpty() ? null : values.iterator().next();
        }
        return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
    }

    /**
     * The field's "multiple" toggle as the editor holds it, unsaved: the choicelist declares
     * {@code dependentProperties='multiple'}, so when the author switches the toggle the Content
     * Editor asks the list again with the new value in the context (jcontent sends a boolean, or an
     * empty list for null). Absent from a plain opening, where the stored value applies.
     */
    static Optional<Boolean> pendingMultiple(Map<String, Object> context) {
        Object value = context.get(FieldShapes.MULTIPLE_PROPERTY);
        if (value instanceof Collection<?> values) {
            value = values.isEmpty() ? null : values.iterator().next();
        }
        return value == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(String.valueOf(value)));
    }

    /** The mapping the field already stores, if it is an existing field with one. */
    private static Optional<String> storedOf(Map<String, Object> context) throws RepositoryException {
        if (context.get(CONTEXT_NODE) instanceof JCRNodeWrapper node && node.hasProperty(PROPERTY)) {
            String value = node.getProperty(PROPERTY).getString();
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
        }
        return Optional.empty();
    }

    private static String siteKeyOf(Map<String, Object> context) throws RepositoryException {
        for (String key : List.of(CONTEXT_NODE, CONTEXT_PARENT)) {
            if (context.get(key) instanceof JCRNodeWrapper node) {
                return node.getResolveSite().getSiteKey();
            }
        }
        return null;
    }

    String keptMessage(Locale locale) {
        return Messages.get(BUNDLE, KEPT_KEY, locale, "(current mapping, kept)");
    }

    String noneMessage(Locale locale) {
        return Messages.get(BUNDLE, NONE_KEY, locale, "No visitor profile property matches this field's type yet");
    }

    String unavailableMessage(Locale locale) {
        return Messages.get(BUNDLE, UNAVAILABLE_KEY, locale, "The visitor profile properties cannot be listed right now: jExperience is not connected");
    }

    @Override
    public void setKey(String key) {
        // Jahia injects the service key on registration; this initializer uses a fixed key, like the engine's
    }

    @Override
    public String getKey() {
        return KEY;
    }
}
