package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.utils.i18n.Messages;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

    private static final Logger log = LoggerFactory.getLogger(ProfilePropertiesChoiceListInitializer.class);

    private String registeredKey = KEY;

    @Reference
    private ProfilePropertyCatalog catalog;

    public ProfilePropertiesChoiceListInitializer() {
    }

    ProfilePropertiesChoiceListInitializer(ProfilePropertyCatalog catalog) {
        this.catalog = catalog;
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

    private static Optional<FieldShape> shapeOf(Map<String, Object> context) throws RepositoryException {
        if (context.get(CONTEXT_NODE) instanceof JCRNodeWrapper node) {
            return FieldShapes.infer(node);
        }
        // a field being created: its type is known, not its properties
        if (context.get(CONTEXT_TYPE) instanceof ExtendedNodeType type) {
            return FieldShapes.infer(type);
        }
        return Optional.empty();
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
        this.registeredKey = key;
    }

    @Override
    public String getKey() {
        return registeredKey;
    }
}
