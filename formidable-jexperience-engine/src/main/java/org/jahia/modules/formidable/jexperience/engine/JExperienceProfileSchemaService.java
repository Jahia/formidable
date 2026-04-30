package org.jahia.modules.formidable.jexperience.engine;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PropertyType;
import org.jahia.modules.jexperience.admin.ContextServerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component(service = JExperienceProfileSchemaService.class)
public class JExperienceProfileSchemaService {

    private static final Logger logger = LoggerFactory.getLogger(JExperienceProfileSchemaService.class);

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private volatile ContextServerService contextServerService;

    public List<JExperienceProfilePropertyDescriptor> getProfileProperties(String siteKey) {
        if (contextServerService == null) {
            return List.of();
        }
        if (siteKey == null || siteKey.isBlank()) {
            return List.of();
        }
        if (!contextServerService.isAvailable(siteKey)) {
            return List.of();
        }

        try {
            return resolveProfilePropertyTypes(siteKey).stream()
                    .map(this::toDescriptor)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(JExperienceProfilePropertyDescriptor::label, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        } catch (RuntimeException | IOException e) {
            logger.warn("[JExperienceProfileSchemaService] Could not resolve jExperience profile properties: {}",
                    e.getMessage());
            return List.of();
        }
    }

    private List<PropertyType> resolveProfilePropertyTypes(String siteKey) throws IOException {
        PropertyType[] propertyTypes = contextServerService.executeGetRequest(
                siteKey,
                "/cxs/profiles/properties/targets/profiles",
                null,
                null,
                PropertyType[].class
        );

        if (propertyTypes == null || propertyTypes.length == 0) {
            return List.of();
        }

        Set<PropertyType> ordered = new LinkedHashSet<>();
        for (PropertyType propertyType : propertyTypes) {
            if (propertyType != null) {
                ordered.add(propertyType);
            }
        }
        return List.copyOf(ordered);
    }

    private Optional<JExperienceProfilePropertyDescriptor> toDescriptor(PropertyType propertyType) {
        if (propertyType == null) {
            return Optional.empty();
        }

        Metadata metadata = propertyType.getMetadata();
        if (metadata != null && (metadata.isHidden() || metadata.isReadOnly())) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(propertyType.isProtected())) {
            return Optional.empty();
        }

        JExperienceFieldValueKind kind = toKind(propertyType.getValueTypeId());
        if (kind == null) {
            return Optional.empty();
        }

        String name = firstNonBlank(propertyType.getItemId(), metadata != null ? metadata.getId() : null);
        if (name == null) {
            return Optional.empty();
        }

        String label = firstNonBlank(metadata != null ? metadata.getName() : null, name);
        if (!Objects.equals(label, name)) {
            label = label + " (" + name + ")";
        }

        return Optional.of(new JExperienceProfilePropertyDescriptor(
                name,
                label,
                kind,
                Boolean.TRUE.equals(propertyType.isMultivalued())
        ));
    }

    private JExperienceFieldValueKind toKind(String valueTypeId) {
        if (valueTypeId == null || valueTypeId.isBlank()) {
            return null;
        }

        String normalized = valueTypeId.toLowerCase(Locale.ROOT);
        if (normalized.equals("string")) {
            return JExperienceFieldValueKind.STRING;
        }
        if (normalized.contains("date")) {
            return JExperienceFieldValueKind.DATE;
        }

        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
