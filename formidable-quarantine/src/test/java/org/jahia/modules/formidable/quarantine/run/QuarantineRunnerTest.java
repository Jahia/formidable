package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.engine.api.DeferredActions;
import org.jahia.modules.formidable.quarantine.api.QuarantineRunException;
import org.jahia.modules.formidable.quarantine.api.RunnerSettings;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.formidable.quarantine.store.QuarantineStoreException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The run as the exported runner answers it, for a caller outside the module such as "run now". */
class QuarantineRunnerTest {

    private static final String SITE = "formidableTestSite";

    private static QuarantineRun run() {
        return new QuarantineRun(formId -> List.of(), Clock.systemUTC());
    }

    @Test
    void theSettingsOfTheRunnerAreTheDefaultsOfTheRun() {
        assertEquals(new RunnerSettings(false, 10, 60, 3600), run().runnerSettings());
    }

    @Test
    void aTreeThatCannotBeReadFailsTheRunWithTheExceptionOfTheRunner() throws Exception {
        QuarantineRun run = run();
        CheckStore checkStore = mock(CheckStore.class);
        QuarantineStoreException unreadable = new QuarantineStoreException("the tree of site " + SITE
                + " cannot be read");
        when(checkStore.forms(SITE)).thenThrow(unreadable);
        run.setCheckStore(checkStore);
        run.setStore(mock(QuarantineStore.class));
        run.setActions(mock(DeferredActions.class));
        run.setRunLock(new FakeRunLock());

        QuarantineRunException failed = assertThrows(QuarantineRunException.class, () -> run.runNow(SITE, null, false));

        assertSame(unreadable, failed.getCause());
    }
}
