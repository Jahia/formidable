package org.jahia.test.modules.formidable.quarantine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarantineTypesTest {

    @Test
    void theTwoStubChecksAreCheckTypesOfTheModule() {
        assertTrue(QuarantineTypes.isCheckType("fmdbquar:remoteStubCheck"));
        assertTrue(QuarantineTypes.isCheckType("fmdbquar:inProcessStubCheck"));
    }

    @Test
    void aTypeOfAnotherModuleIsNotACheckTypeOfTheModule() {
        assertFalse(QuarantineTypes.isCheckType("fmdbsample:uploadObserverAction"));
        assertFalse(QuarantineTypes.isCheckType(null));
    }
}
