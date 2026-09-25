package org.jahia.modules.formidable.engine.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What every check of an address agrees on: which values are one address, and what their domain is.
 */
class EmailAddressTest {

    @Test
    void theDomainIsReadLowerCasedAndWithoutItsTrailingDot() {
        // Verifies the reading the provider-backed checks and the domain lookup share: the part after the last @,
        // normalised the way a resolver or a provider wants it, whatever the visitor typed around it.
        assertEquals("example.com", EmailAddress.domainOf("ada@example.com"));
        assertEquals("example.com", EmailAddress.domainOf("  Ada@Example.COM.  "));
        assertEquals("mail.example-site.co.uk", EmailAddress.domainOf("ada.lovelace+tag@mail.example-site.co.uk"));
    }

    @Test
    void aValueThatIsNotOneAddressHasNoDomain() {
        // Verifies the boundary with the field's own validation: no @, an @ at either end, two of them, a domain of one
        // label, a label with a character or a hyphen where none may be, an absurd length — none is an address to check.
        assertNull(EmailAddress.domainOf(null));
        assertNull(EmailAddress.domainOf(""));
        assertNull(EmailAddress.domainOf("hello"));
        assertNull(EmailAddress.domainOf("@example.com"));
        assertNull(EmailAddress.domainOf("ada@"));
        assertNull(EmailAddress.domainOf("a@b@c.example"));
        assertNull(EmailAddress.domainOf("ada@localhost"));
        assertNull(EmailAddress.domainOf("ada@exa_mple.com"));
        assertNull(EmailAddress.domainOf("ada@-example.com"));
        assertNull(EmailAddress.domainOf("ada@" + "a".repeat(64) + ".com"));
        assertNull(EmailAddress.domainOf("ada@" + "abcdefghij.".repeat(25) + "com"));
    }
}
