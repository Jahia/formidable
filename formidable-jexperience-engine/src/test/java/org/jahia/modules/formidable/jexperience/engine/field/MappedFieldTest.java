package org.jahia.modules.formidable.jexperience.engine.field;

import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A field is mapped once it names a profile property: the mixin switched on with the property left empty is not. */
class MappedFieldTest {

    private static JCRNodeWrapper fieldNaming(String property) {
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        when(field.getPropertyAsString(JxpProperty.PROFILE_PROPERTY)).thenReturn(property);
        return field;
    }

    @Test
    void aFieldNamingAProfilePropertyIsMapped() {
        assertTrue(MappedField.isMapped(fieldNaming("email")));
    }

    @Test
    void aFieldNamingNoPropertyOrABlankOneIsNotMapped() {
        assertFalse(MappedField.isMapped(fieldNaming(null)));
        assertFalse(MappedField.isMapped(fieldNaming("")));
        assertFalse(MappedField.isMapped(fieldNaming("  ")));
    }
}
