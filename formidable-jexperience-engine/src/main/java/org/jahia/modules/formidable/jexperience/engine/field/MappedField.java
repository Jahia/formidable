package org.jahia.modules.formidable.jexperience.engine.field;

import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.services.content.JCRNodeWrapper;

/**
 * Whether a field is mapped to a visitor profile property — the one rule the rule reader and the render filter
 * share, written once so that they agree.
 */
public final class MappedField {

    private MappedField() {
    }

    /**
     * Whether the field is mapped to a visitor profile property: the mixin alone is not enough, since an
     * author can switch the section on and leave the property empty, and jcontent clears a property its list
     * no longer offers.
     */
    public static boolean isMapped(JCRNodeWrapper field) {
        String property = field.getPropertyAsString(JxpProperty.PROFILE_PROPERTY);
        return property != null && !property.isBlank();
    }
}
