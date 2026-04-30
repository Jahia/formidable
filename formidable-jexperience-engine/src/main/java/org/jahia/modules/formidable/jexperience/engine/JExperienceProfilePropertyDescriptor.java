package org.jahia.modules.formidable.jexperience.engine;

public record JExperienceProfilePropertyDescriptor(
        String name,
        String label,
        JExperienceFieldValueKind kind,
        boolean multiple
) {
}
