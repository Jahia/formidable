package org.jahia.modules.formidable.jexperience.engine;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormIdentifierTest {

    /** Unomi's item id constraint (items/item.json). */
    private static final String UNOMI_ITEM_ID = "^(\\w|[-_@\\.]){0,60}$";

    @Test
    void prefixesTheFormUuid() {
        // Verifies the identifier every jCustomer object keys on.
        assertEquals("formidable-jxp-8f7e2a10-0000-4000-8000-000000000001", FormIdentifier.of("8f7e2a10-0000-4000-8000-000000000001"));
    }

    @Test
    void isAValidUnomiItemId() {
        // Verifies that a UUID-based identifier fits Unomi's pattern and length (51 characters).
        String identifier = FormIdentifier.of(UUID.randomUUID().toString());
        assertTrue(identifier.matches(UNOMI_ITEM_ID), identifier);
        assertEquals(51, identifier.length());
    }

    @Test
    void refusesAMissingUuid() {
        // Verifies that an identifier is never built from nothing.
        assertThrows(IllegalArgumentException.class, () -> FormIdentifier.of(" "));
        assertThrows(IllegalArgumentException.class, () -> FormIdentifier.of(null));
    }
}
