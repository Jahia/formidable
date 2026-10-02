package org.jahia.modules.formidable.jexperience.engine.field;

import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.jexperience.engine.model.JxpProperty;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The stored flag is read under both its names until 0.6: a field saved by a development build carries the
 * retired one until {@code ProfileSensitiveFlagMigration} has run, which waits for the elements redeploy — and a
 * flag that failed open meanwhile would send a value to the profile, which cannot be taken back (review of #369).
 */
class SensitiveFieldTest {

    private static JCRNodeWrapper fieldWith(String name, boolean value) throws Exception {
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        flag(field, name, value);
        return field;
    }

    private static void flag(JCRNodeWrapper field, String name, boolean value) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getBoolean()).thenReturn(value);
        when(field.hasProperty(name)).thenReturn(true);
        when(field.getProperty(name)).thenReturn(property);
    }

    @Test
    void theEngineFlagMakesTheFieldSensitive() throws Exception {
        assertTrue(SensitiveField.isSensitive(fieldWith(FmdbProperty.PROFILE_SENSITIVE, true)));
    }

    @Test
    void theRetiredFlagStillMakesTheFieldSensitiveWhileTheMigrationWaits() throws Exception {
        assertTrue(SensitiveField.isSensitive(fieldWith(JxpProperty.RETIRED_SENSITIVE, true)));
    }

    @Test
    void aFieldCarryingNeitherOrBothUnsetIsNotSensitive() throws Exception {
        assertFalse(SensitiveField.isSensitive(mock(JCRNodeWrapper.class)));
        JCRNodeWrapper unset = fieldWith(FmdbProperty.PROFILE_SENSITIVE, false);
        flag(unset, JxpProperty.RETIRED_SENSITIVE, false);
        assertFalse(SensitiveField.isSensitive(unset));
    }
}
