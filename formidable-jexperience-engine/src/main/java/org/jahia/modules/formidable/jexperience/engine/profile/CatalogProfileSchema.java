package org.jahia.modules.formidable.jexperience.engine.profile;

import org.jahia.modules.formidable.engine.api.VisitorProfileSchema;
import org.jahia.modules.formidable.jexperience.engine.field.FieldShape;
import org.jahia.modules.formidable.jexperience.engine.field.FieldShapes;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The visitor profile properties of a site for the engine, from the catalog the editor reads and with the rule the
 * mapping rule of a form applies (docs/architecture/jexperience-integration.md, "Type compatibility in the
 * dropdown"): a property fits a field when jCustomer declares it and its type and cardinality fit the kind of value
 * the field holds. The Forms import asks it before it carries a profile prefill over, so that it never writes a
 * mapping that {@link org.jahia.modules.formidable.jexperience.engine.rule.FormMappingReader} would skip.
 */
@Component(service = VisitorProfileSchema.class, immediate = true)
public class CatalogProfileSchema implements VisitorProfileSchema {

    private static final Logger log = LoggerFactory.getLogger(CatalogProfileSchema.class);

    /** The node type of a field by name — a seam for the tests, which have no type registry. */
    @FunctionalInterface
    interface NodeTypes {
        NodeType get(String name) throws RepositoryException;
    }

    @Reference
    private ProfilePropertyCatalog catalog;

    private final NodeTypes nodeTypes;

    public CatalogProfileSchema() {
        this(name -> NodeTypeRegistry.getInstance().getNodeType(name));
    }

    CatalogProfileSchema(NodeTypes nodeTypes) {
        this.nodeTypes = nodeTypes;
    }

    CatalogProfileSchema(ProfilePropertyCatalog catalog, NodeTypes nodeTypes) {
        this(nodeTypes);
        this.catalog = catalog;
    }

    @Override
    public Fit fit(String siteKey, String fieldType, boolean multiple, OptionalInt choices, String property) {
        List<ProfilePropertyDescriptor> schema;
        try {
            schema = catalog.profileProperties(siteKey);
        } catch (ProfilePropertiesUnavailableException e) {
            return Fit.UNAVAILABLE;
        }
        Optional<ProfilePropertyDescriptor> descriptor = schema.stream().filter(p -> p.name().equals(property)).findFirst();
        if (descriptor.isEmpty()) {
            return Fit.UNKNOWN;
        }
        Optional<FieldShape> shape;
        try {
            // a field being created: its type, its multiple toggle and, for a checkbox, its number of options
            shape = FieldShapes.infer(nodeTypes.get(fieldType), multiple, FieldShapes.ChoiceCount.of(choices));
        } catch (RepositoryException e) {
            log.debug("[CatalogProfileSchema] Type {} unreadable: {}", fieldType, e.getMessage());
            return Fit.INCOMPATIBLE;
        }
        return shape.isPresent() && shape.get().accepts(descriptor.get().valueTypeId(), descriptor.get().multivalued())
                ? Fit.FITS : Fit.INCOMPATIBLE;
    }
}
