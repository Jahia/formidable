package org.jahia.modules.formidable.content.platform;

import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.osgi.service.component.annotations.Component;

import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link TypeCatalog} on Jahia's {@link NodeTypeRegistry}: the definitions every installed module registered.
 *
 * <p>Threading: a stateless singleton; the registry is thread-safe for reads.</p>
 */
@Component(service = TypeCatalog.class, immediate = true)
public class JahiaTypeCatalog implements TypeCatalog {

    /** The platform's own properties an author may still fill. */
    private static final Set<String> ALLOWED_PLATFORM_PROPERTIES = Set.of("jcr:title", "jcr:description");

    @Override
    public boolean isCreatable(String typeName) {
        return find(typeName).map(type -> !type.isAbstract() && !type.isMixin()).orElse(false);
    }

    @Override
    public boolean isMixin(String name) {
        return find(name).map(ExtendedNodeType::isMixin).orElse(false);
    }

    @Override
    public Optional<PropertyShape> property(List<String> types, String propertyName) {
        if (!isFillable(propertyName)) {
            return Optional.empty();
        }
        for (String typeName : types) {
            Optional<ExtendedNodeType> type = find(typeName);
            if (type.isEmpty()) {
                continue;
            }
            ExtendedPropertyDefinition definition = type.get().getPropertyDefinitionsAsMap().get(propertyName);
            if (definition != null && !definition.isProtected()) {
                return Optional.of(new PropertyShape(propertyName, definition.getRequiredType(), definition.isMultiple(),
                        definition.isInternationalized()));
            }
        }
        return Optional.empty();
    }

    /**
     * @param propertyName a property name an author wrote
     * @return whether the name is one the action may write: not a platform property, except the title and description
     */
    static boolean isFillable(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            return false;
        }
        boolean platform = propertyName.startsWith("jcr:") || propertyName.startsWith("rep:");
        return !platform || ALLOWED_PLATFORM_PROPERTIES.contains(propertyName);
    }

    private static Optional<ExtendedNodeType> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(NodeTypeRegistry.getInstance().getNodeType(name));
        } catch (NoSuchNodeTypeException e) {
            return Optional.empty();
        }
    }
}
