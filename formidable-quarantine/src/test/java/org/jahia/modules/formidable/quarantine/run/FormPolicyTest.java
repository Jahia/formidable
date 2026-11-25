package org.jahia.modules.formidable.quarantine.run;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The scan status that a policy adds to the values of the actions after release. */
class FormPolicyTest {

    private static final Map<String, List<String>> VALUES = Map.of("fullName", List.of("Ada"),
            "scanStatus", List.of("forged"));

    @Test
    void aPolicyWithNoScanStatusLeavesTheValuesAsTheyAre() {
        FormPolicy policy = new FormPolicy(OnFail.RELEASE_VALUES, "  ", "clean", "failed", false, false);

        assertNull(policy.scanStatusName());
        assertSame(VALUES, policy.withScanStatus(VALUES, false));
    }

    @Test
    void theScanStatusIsWrittenOverTheValueOfItsName() {
        FormPolicy policy = new FormPolicy(OnFail.RELEASE_VALUES, "scanStatus", "clean", "failed", false, false);

        assertEquals(List.of("clean"), policy.withScanStatus(VALUES, true).get("scanStatus"));
        assertEquals(List.of("failed"), policy.withScanStatus(VALUES, false).get("scanStatus"));
        assertEquals(List.of("Ada"), policy.withScanStatus(VALUES, false).get("fullName"));
    }

    @Test
    void aMissingStatusValueIsEmpty() {
        FormPolicy policy = new FormPolicy(OnFail.REJECT, "scanStatus", null, null, false, false);

        assertEquals(List.of(""), policy.withScanStatus(Map.of(), true).get("scanStatus"));
    }

    @Test
    void anUnknownOnFailFailsClosed() {
        assertEquals(OnFail.REJECT, OnFail.fromValue(null));
        assertEquals(OnFail.REJECT, OnFail.fromValue("rejectAll"));
        assertEquals(OnFail.RELEASE_VALUES, OnFail.fromValue("releaseValues"));
    }
}
