package org.jahia.modules.formidable.engine.migration;

import org.jahia.services.templates.JahiaTemplateManagerService.TemplatePackageRedeployedEvent;
import org.junit.jupiter.api.Test;

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

    private static final class CountingMigration extends ElementsRedeployRetriggeredMigration {
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
    void ignoresOtherModulesRedeployments() {
        CountingMigration migration = new CountingMigration();
        migration.onEvent(new TemplatePackageRedeployedEvent("formidable-extended-inputs"));
        assertEquals(0, migration.runs);
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
}
