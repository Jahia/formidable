package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.templates.JahiaTemplateManagerService.TemplatePackageRedeployedEvent;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.EventObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The migrations must re-run when the formidable-elements module is (re)deployed —
 * on the engine-first upgrade path that run is the one that succeeds, the
 * engine-activation run having failed against the previous element definitions —
 * and must ignore every other module and event type.
 */
class ElementsRedeployRetriggeredMigrationTest {

    private static class CountingMigration extends ElementsRedeployRetriggeredMigration {
        int runs;

        @Override
        void run() {
            runs++;
        }
    }

    @Test
    void rerunsWhenTheElementsModuleIsRedeployed() {
        CountingMigration migration = new CountingMigration();
        migration.onEvent(new TemplatePackageRedeployedEvent(ElementsRedeployRetriggeredMigration.ELEMENTS_MODULE_ID));
        assertEquals(1, migration.runs);
    }

    @Test
    void ignoresOtherModulesRedeploymentsByDefault() {
        CountingMigration migration = new CountingMigration();
        migration.onEvent(new TemplatePackageRedeployedEvent("formidable-extended-inputs"));
        assertEquals(0, migration.runs);
    }

    @Test
    void aMigrationMayWidenTheModulesThatRerunIt() {
        CountingMigration migration = new CountingMigration() {
            @Override
            boolean retriggeredBy(String moduleId) {
                return true;
            }
        };
        migration.onEvent(new TemplatePackageRedeployedEvent("formidable-extended-inputs"));
        assertEquals(1, migration.runs);
    }

    @Test
    void ignoresOtherEventTypes() {
        CountingMigration migration = new CountingMigration();
        migration.onEvent(new EventObject(ElementsRedeployRetriggeredMigration.ELEMENTS_MODULE_ID));
        assertEquals(0, migration.runs);
    }

    @Test
    void onlySubscribesToRedeployEvents() {
        assertArrayEquals(new Class[]{TemplatePackageRedeployedEvent.class}, new CountingMigration().getEventTypes());
    }

    @Test
    void aMigratedNodeIsSavedOnItsOwn() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);

        ElementsRedeployRetriggeredMigration.Outcome outcome = new CountingMigration()
                .migrateOne(session, node, "default", "Rewrote", (s, n) -> ElementsRedeployRetriggeredMigration.Outcome.MIGRATED);

        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.MIGRATED, outcome);
        verify(session).save();
    }

    @Test
    void aDeferredOrUntouchedNodeIsNotSaved() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        CountingMigration migration = new CountingMigration();

        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.DEFERRED,
                migration.migrateOne(session, node, "default", "Rewrote", (s, n) -> ElementsRedeployRetriggeredMigration.Outcome.DEFERRED));
        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.UNTOUCHED,
                migration.migrateOne(session, node, "default", "Rewrote", (s, n) -> ElementsRedeployRetriggeredMigration.Outcome.UNTOUCHED));
        verify(session, never()).save();
    }

    @Test
    void aFailingNodeIsCountedFailedAndItsChangesDropped() throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);

        ElementsRedeployRetriggeredMigration.Outcome outcome = new CountingMigration()
                .migrateOne(session, node, "live", "Rewrote", (s, n) -> {
                    throw new RepositoryException("locked");
                });

        assertEquals(ElementsRedeployRetriggeredMigration.Outcome.FAILED, outcome);
        // The half-applied changes go, so the later saves of the pass are not poisoned by them.
        verify(session).refresh(false);
        verify(session, never()).save();
    }
}
