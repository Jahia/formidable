package org.jahia.modules.formidable.content.platform;

import org.jahia.modules.formidable.content.convert.PropertyShape;

import java.util.List;
import java.util.Optional;

/**
 * What the action asks the platform about node types: can this type be created, is this name a mixin, what is this
 * property. A seam over Jahia's node type registry, so the plan of a submission can be built and tested without one.
 *
 * <p>Implementations are stateless singletons and thread-safe.</p>
 */
public interface TypeCatalog {

    /**
     * @param typeName a node type name
     * @return whether the type exists and is a concrete primary type (not abstract, not a mixin)
     */
    boolean isCreatable(String typeName);

    /**
     * @param name a node type name
     * @return whether it exists and is a mixin
     */
    boolean isMixin(String name);

    /**
     * Looks a property up on the given types, in order. A property that is protected, or whose name belongs to the
     * platform ({@code jcr:}, {@code rep:}, except {@code jcr:title} and {@code jcr:description}) is not offered.
     *
     * @param types        the primary type, then the mixins the node will carry
     * @param propertyName the property as an author wrote it
     * @return its shape, empty when no type declares it or it may not be filled
     */
    Optional<PropertyShape> property(List<String> types, String propertyName);
}
