package org.jahia.modules.formidable.jexperience.engine;

import org.jahia.modules.formidable.engine.api.ChoiceOptionsResolver;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reads what a published form maps: every field under it carrying a non-empty
 * {@code jExperienceProfileProperty}, checked against the profile property it names — the
 * property must exist in jCustomer's schema and fit the field's shape, the same rule the editor's
 * dropdown applies, so the rule never disagrees with the dropdown. A mapping that no longer fits
 * (the field's choices changed, the property left the schema) is skipped and logged, never turned
 * into an action.
 */
public class FormMappingReader {

    static final String MAPPING_MIXIN = "fmdbmix:jExperienceProfileMapping";
    static final String STRATEGY_PROPERTY = "jExperienceSetStrategy";
    static final String DEFAULT_STRATEGY = "alwaysSet";

    private static final Logger log = LoggerFactory.getLogger(FormMappingReader.class);

    private final ProfilePropertyCatalog catalog;
    private final ChoiceOptionsResolver optionsResolver;

    public FormMappingReader(ProfilePropertyCatalog catalog, ChoiceOptionsResolver optionsResolver) {
        this.catalog = catalog;
        this.optionsResolver = optionsResolver;
    }

    /**
     * @param session  a live session
     * @param form     the published form node
     * @param formName the title to show in jExperience's Form mappings screen
     * @throws ProfilePropertiesUnavailableException when jCustomer's schema cannot be read — the
     *                                               caller retries later rather than writing a rule
     *                                               that ignores half the mappings
     */
    public MappingRule.FormMapping read(JCRSessionWrapper session, JCRNodeWrapper form, String formName)
            throws RepositoryException, ProfilePropertiesUnavailableException {
        String siteKey = form.getResolveSite().getSiteKey();
        List<ProfilePropertyDescriptor> schema = catalog.profileProperties(siteKey);
        String language = form.getLanguage();
        List<MappingRule.FieldMapping> fields = new ArrayList<>();
        NodeIterator mapped = mappedFields(session, form);
        while (mapped.hasNext()) {
            JCRNodeWrapper field = (JCRNodeWrapper) mapped.nextNode();
            fieldMappingOf(field, schema, language).ifPresent(fields::add);
        }
        return new MappingRule.FormMapping(siteKey, form.getIdentifier(), formName, fields);
    }

    /** The fields under the form carrying the mapping mixin — a seam for the tests, which have no query engine. */
    NodeIterator mappedFields(JCRSessionWrapper session, JCRNodeWrapper form) throws RepositoryException {
        return session.getWorkspace().getQueryManager()
                .createQuery("SELECT * FROM [" + MAPPING_MIXIN + "] WHERE ISDESCENDANTNODE('" + form.getPath() + "')", Query.JCR_SQL2)
                .execute().getNodes();
    }

    Optional<MappingRule.FieldMapping> fieldMappingOf(JCRNodeWrapper field, List<ProfilePropertyDescriptor> schema, String language)
            throws RepositoryException {
        String propertyName = field.getPropertyAsString(ProfilePropertiesChoiceListInitializer.PROPERTY);
        if (propertyName == null || propertyName.isBlank()) {
            return Optional.empty();
        }
        Optional<FieldShape> shape = FieldShapes.infer(field, Optional.empty(), () -> countChoices(field, language));
        if (shape.isEmpty()) {
            log.warn("[FormMappingReader] '{}' maps '{}' but is not a mappable field: skipped", field.getPath(), propertyName);
            return Optional.empty();
        }
        Optional<ProfilePropertyDescriptor> property = schema.stream().filter(p -> p.name().equals(propertyName)).findFirst();
        if (property.isEmpty()) {
            log.warn("[FormMappingReader] '{}' maps '{}', which the visitor profile schema does not offer: skipped", field.getPath(), propertyName);
            return Optional.empty();
        }
        if (!shape.get().accepts(property.get().valueTypeId(), property.get().multivalued())) {
            log.warn("[FormMappingReader] '{}' maps '{}' ({}{}), which does not fit the field any more: skipped",
                    field.getPath(), propertyName, property.get().valueTypeId(), property.get().multivalued() ? ", multivalued" : "");
            return Optional.empty();
        }
        String strategy = field.getPropertyAsString(STRATEGY_PROPERTY);
        return Optional.of(new MappingRule.FieldMapping(field.getName(), propertyName,
                strategy == null || strategy.isBlank() ? DEFAULT_STRATEGY : strategy,
                MappingRule.ValueKind.of(property.get().valueTypeId(), property.get().multivalued())));
    }

    private OptionalInt countChoices(JCRNodeWrapper field, String language) throws RepositoryException {
        return optionsResolver.countChoices(field, language != null ? language : field.getResolveSite().getDefaultLanguage());
    }
}
