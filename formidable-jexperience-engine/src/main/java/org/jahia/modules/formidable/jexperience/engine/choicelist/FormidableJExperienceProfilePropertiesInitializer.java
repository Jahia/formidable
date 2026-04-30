package org.jahia.modules.formidable.jexperience.engine.choicelist;

import org.jahia.modules.formidable.jexperience.engine.JExperienceFieldShape;
import org.jahia.modules.formidable.jexperience.engine.JExperienceFormFieldSupport;
import org.jahia.modules.formidable.jexperience.engine.JExperienceProfilePropertyDescriptor;
import org.jahia.modules.formidable.jexperience.engine.JExperienceProfileSchemaService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component(service = ModuleChoiceListInitializer.class)
public class FormidableJExperienceProfilePropertiesInitializer implements ModuleChoiceListInitializer {

    private static final Logger logger =
            LoggerFactory.getLogger(FormidableJExperienceProfilePropertiesInitializer.class);

    public static final String KEY = "formidableJExperienceProfileProperties";

    private JExperienceProfileSchemaService schemaService;

    @Reference
    public void setSchemaService(JExperienceProfileSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(
            ExtendedPropertyDefinition epd,
            String param,
            List<ChoiceListValue> values,
            Locale locale,
            Map<String, Object> context
    ) {
        JCRNodeWrapper currentNode = resolveCurrentNode(context);
        if (currentNode == null) {
            logger.debug("[FormidableJExperienceProfilePropertiesInitializer] No current node in context for '{}'.",
                    epd.getName());
            return List.of();
        }

        try {
            Optional<JExperienceFieldShape> fieldShape = JExperienceFormFieldSupport.resolveFieldShape(currentNode);
            if (fieldShape.isEmpty()) {
                return List.of();
            }

            return schemaService.getProfileProperties(currentNode.getResolveSite().getName()).stream()
                    .filter(descriptor -> isCompatible(descriptor, fieldShape.get()))
                    .map(descriptor -> new ChoiceListValue(descriptor.label(), descriptor.name()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("[FormidableJExperienceProfilePropertiesInitializer] Could not build choicelist for '{}': {}",
                    currentNode, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void setKey(String key) {
        // Jahia injects the service key on registration; this initializer uses a fixed key.
    }

    @Override
    public String getKey() {
        return KEY;
    }

    private boolean isCompatible(JExperienceProfilePropertyDescriptor descriptor, JExperienceFieldShape fieldShape) {
        return descriptor.kind() == fieldShape.kind() && descriptor.multiple() == fieldShape.multiple();
    }

    private JCRNodeWrapper resolveCurrentNode(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }

        for (String key : List.of("contextNode", "currentNode", "node")) {
            Object value = context.get(key);
            if (value instanceof JCRNodeWrapper node) {
                return node;
            }
        }

        for (Object value : context.values()) {
            if (value instanceof JCRNodeWrapper node) {
                return node;
            }
            if (value instanceof Map<?, ?> nestedMap) {
                for (Object nestedValue : nestedMap.values()) {
                    if (nestedValue instanceof JCRNodeWrapper node) {
                        return node;
                    }
                }
            }
        }

        return null;
    }
}
