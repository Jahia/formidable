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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code formidableJExperienceProfileProperties} choicelist: the profile properties a
 * field can be mapped to, filtered on the field's shape so that the dropdown and the mapping
 * rule never disagree. An unreachable jCustomer yields one explanatory entry, never an error
 * in the editor.
 */
@Component(service = ModuleChoiceListInitializer.class, immediate = true)
public class ProfilePropertiesChoiceListInitializer implements ModuleChoiceListInitializer {

    public static final String KEY = "formidableJExperienceProfileProperties";

    static final String BUNDLE = "resources.formidable-jexperience-engine";
    static final String UNAVAILABLE_KEY = "formidableJExperienceProfileProperties.unavailable";

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
            return choices(shape.get(), siteKey, locale);
        } catch (RepositoryException e) {
            log.warn("[ProfilePropertiesChoiceListInitializer] Could not read the field being edited: {}", e.getMessage());
            return List.of();
        }
    }

    List<ChoiceListValue> choices(FieldShape shape, String siteKey, Locale locale) {
        try {
            return catalog.profileProperties(siteKey).stream()
                    .filter(property -> shape.accepts(property.valueTypeId(), property.multivalued()))
                    .map(property -> new ChoiceListValue(property.label(), property.name()))
                    .toList();
        } catch (ProfilePropertiesUnavailableException e) {
            log.warn("[ProfilePropertiesChoiceListInitializer] No profile properties for site '{}': {}", siteKey, e.getMessage());
            return List.of(new ChoiceListValue(unavailableMessage(locale), ""));
        }
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

    private static String siteKeyOf(Map<String, Object> context) throws RepositoryException {
        for (String key : List.of(CONTEXT_NODE, CONTEXT_PARENT)) {
            if (context.get(key) instanceof JCRNodeWrapper node) {
                return node.getResolveSite().getSiteKey();
            }
        }
        return null;
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
