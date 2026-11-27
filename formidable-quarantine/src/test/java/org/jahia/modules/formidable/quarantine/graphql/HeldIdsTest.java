package org.jahia.modules.formidable.quarantine.graphql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The identifier of a held submission names a node below the quarantine root of the site, and nothing else. */
class HeldIdsTest {

    private static final String ID = "6a1f0c3e-0000-4000-8000-000000000001/2026/11/19/"
            + "held-4b0c1d2e-3f40-4a51-8b62-7c83d94ea5f6";

    @Test
    void anIdentifierIsThePathBelowTheRootOfTheSite() {
        String path = "/sites/siteA/formidable-quarantine/" + ID;

        assertEquals(path, HeldIds.pathOf("siteA", ID));
        assertEquals(ID, HeldIds.idOf("siteA", path));
    }

    @Test
    void anyOtherValueNamesNothing() {
        assertNull(HeldIds.pathOf("siteA", "../siteB/formidable-quarantine/" + ID));
        assertNull(HeldIds.pathOf("siteA", ID + "/item0"));
        assertNull(HeldIds.pathOf("siteA", ID.replace("/2026/", "/../")));
        assertNull(HeldIds.pathOf("siteA", "/" + ID));
        assertNull(HeldIds.pathOf("site/A", ID));
        assertNull(HeldIds.pathOf(null, ID));
        assertNull(HeldIds.pathOf("siteA", null));
    }

    @Test
    void aPathOfAnotherSiteHasNoIdentifierInThisOne() {
        String path = "/sites/siteB/formidable-quarantine/" + ID;

        assertThrows(IllegalArgumentException.class, () -> HeldIds.idOf("siteA", path));
    }
}
