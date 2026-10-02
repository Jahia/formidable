package org.jahia.modules.formidable.engine.migration;

import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileSensitiveFlagMigrationTest {

    private static void flag(JCRNodeWrapper field, String name, boolean value) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getBoolean()).thenReturn(value);
        when(field.hasProperty(name)).thenReturn(true);
        when(field.getProperty(name)).thenReturn(property);
    }

    @Test
    void rerunsOnTheRedeployOfTheElementsAndOfTheJExperienceModule() {
        ProfileSensitiveFlagMigration migration = new ProfileSensitiveFlagMigration();
        assertTrue(migration.retriggeredBy(ElementsRedeployRetriggeredMigration.ELEMENTS_MODULE_ID));
        assertTrue(migration.retriggeredBy(ProfileSensitiveFlagMigration.JEXPERIENCE_MODULE_ID));
        assertFalse(migration.retriggeredBy("formidable-extended-inputs"));
    }

    @Test
    void theFlagMovesUnderItsNewNameAndTheRetiredMixinGoes() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        flag(field, ProfileSensitiveFlagMigration.RETIRED_PROPERTY, true);

        ElementsRedeployRetriggeredMigration.Outcome outcome = ProfileSensitiveFlagMigration.moveFlag(session, field, true);

        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.MIGRATED, outcome);
        verify(session).checkout(field);
        verify(field).setProperty(FmdbProperty.PROFILE_SENSITIVE, true);
        verify(field).removeMixin(ProfileSensitiveFlagMigration.RETIRED_MIXIN);
    }

    @Test
    void aValueAlreadyUnderTheNewNameWins() throws Exception {
        // Written by the editor after the upgrade, before this ran: the more recent value stays, only the mixin goes.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        flag(field, ProfileSensitiveFlagMigration.RETIRED_PROPERTY, true);
        flag(field, FmdbProperty.PROFILE_SENSITIVE, false);

        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.MIGRATED, ProfileSensitiveFlagMigration.moveFlag(session, field, true));
        verify(field, never()).setProperty(anyString(), anyBoolean());
        verify(field).removeMixin(ProfileSensitiveFlagMigration.RETIRED_MIXIN);
    }

    @Test
    void aMixinWithoutItsFlagIsSimplyDropped() throws Exception {
        // A field that carried the mixin with the flag unset reads as not sensitive before and after.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);

        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.MIGRATED, ProfileSensitiveFlagMigration.moveFlag(session, field, true));
        verify(field, never()).setProperty(anyString(), anyBoolean());
        verify(field).removeMixin(ProfileSensitiveFlagMigration.RETIRED_MIXIN);
    }

    @Test
    void aFieldWhoseDefinitionsAreNotReadyWaitsForTheRedeploy() throws Exception {
        // The type does not define the new name yet (elements not redeployed), or the retired mixin is not registered.
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper field = mock(JCRNodeWrapper.class);
        flag(field, ProfileSensitiveFlagMigration.RETIRED_PROPERTY, true);

        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.DEFERRED, ProfileSensitiveFlagMigration.moveFlag(session, field, false));
        verify(session, never()).checkout(field);
        verify(field, never()).setProperty(anyString(), anyBoolean());
        verify(field, never()).removeMixin(anyString());
    }
}
