package org.jahia.modules.formidable.engine.actions.form.email;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailAddressesTest {

    private static final String ACTION = "fmdb:emailNotificationAction";
    private static final String PATH = "/sites/site/contents/form/actions/notify";
    private static final String TEAM = "team@example.com";
    /** The address of the incident: a domain ending with a dot, which the mail queue refused after the action. */
    private static final String TYPO = "formidable-no-reply@jahia.com.";

    @Test
    void anAddressBareOrWithANameIsValid() {
        assertTrue(EmailAddresses.isValid(TEAM));
        assertTrue(EmailAddresses.isValid("first.last+forms@mail.example.co.uk"));
        assertTrue(EmailAddresses.isValid("Formidable <no-reply@example.com>"));
        assertTrue(EmailAddresses.isValid("ops@localhost"));
    }

    @Test
    void aTypoInAnAddressIsInvalid() {
        assertFalse(EmailAddresses.isValid(TYPO));
        assertFalse(EmailAddresses.isValid("team@example..com"));
        assertFalse(EmailAddresses.isValid("team.example.com"));
        assertFalse(EmailAddresses.isValid("team@@example.com"));
        assertFalse(EmailAddresses.isValid("team @example.com"));
        assertFalse(EmailAddresses.isValid("Team <team@example.com"));
    }

    @Test
    void aListIsSplitIntoItsValidAndInvalidEntriesInOrder() {
        EmailAddresses.Split split = EmailAddresses.split("a@example.com, b@example.com., Team <c@example.com>,, d");
        assertEquals(List.of("a@example.com", "Team <c@example.com>"), split.valid());
        assertEquals(List.of("b@example.com.", "d"), split.invalid());
        assertEquals(List.of(), EmailAddresses.split("").invalid());
        assertEquals(List.of(), EmailAddresses.split(null).valid());
    }

    @Test
    void theRecipientsAreTheValidEntriesJoinedBackOrNothing() {
        assertEquals(Optional.of(TEAM + ", Ops <ops@example.com>"),
                EmailAddresses.recipients(ACTION, PATH, TEAM + " , sales@example.., Ops <ops@example.com>"));
        assertEquals(Optional.empty(), EmailAddresses.recipients(ACTION, PATH, "sales@example.., team.example.com"));
        assertEquals(Optional.empty(), EmailAddresses.recipients(ACTION, PATH, ""));
    }

    @Test
    void theSenderIsKeptWhenValidAndDroppedForTheDefaultOneOtherwise() {
        assertEquals(TEAM, EmailAddresses.sender(ACTION, PATH, TEAM, "jahia@example.com"));
        assertNull(EmailAddresses.sender(ACTION, PATH, TYPO, "jahia@example.com"));
        assertNull(EmailAddresses.sender(ACTION, PATH, "", "jahia@example.com"));
        assertNull(EmailAddresses.sender(ACTION, PATH, null, "jahia@example.com"));
    }
}
