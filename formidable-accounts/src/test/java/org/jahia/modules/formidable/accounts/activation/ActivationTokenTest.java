package org.jahia.modules.formidable.accounts.activation;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivationTokenTest {

    private static final String ID = UUID.randomUUID().toString();

    @Test
    void aGeneratedTokenRoundTripsThroughItsParameter() {
        // Verifies the link value: identifier, dot, 43 URL-safe characters, and back.
        ActivationToken token = ActivationToken.generate(ID);

        assertEquals(36 + 1 + 43, token.asParameter().length());
        assertEquals(Optional.of(token), ActivationToken.parse(token.asParameter()));
    }

    @Test
    void twoTokensDifferAndOnlyTheHashOfTheSecretIsStored() {
        // Verifies the secrets are random and that the stored value is not the secret.
        ActivationToken first = ActivationToken.generate(ID);
        ActivationToken second = ActivationToken.generate(ID);

        assertNotEquals(first.secret(), second.secret());
        assertEquals(64, first.hash().length());
        assertFalse(first.hash().contains(first.secret()));
    }

    @Test
    void theHashIsMatchedInConstantTimeAndOnlyByTheRightSecret() {
        // Verifies verification: the right hash matches, another token's hash and null do not.
        ActivationToken token = ActivationToken.generate(ID);

        assertTrue(token.matches(token.hash()));
        assertFalse(token.matches(ActivationToken.generate(ID).hash()));
        assertFalse(token.matches(null));
        assertFalse(token.matches(""));
    }

    @Test
    void whatIsNotExactlyAnIdentifierADotAndASecretIsRefused() {
        // Verifies the parser takes nothing else: wrong length, wrong separator, wrong alphabet, null.
        ActivationToken token = ActivationToken.generate(ID);
        String good = token.asParameter();

        assertTrue(ActivationToken.parse(null).isEmpty());
        assertTrue(ActivationToken.parse("").isEmpty());
        assertTrue(ActivationToken.parse(good + "x").isEmpty());
        assertTrue(ActivationToken.parse(good.replace('.', ':')).isEmpty());
        assertTrue(ActivationToken.parse(good.substring(0, 36) + "." + "!".repeat(43)).isEmpty());
        assertTrue(ActivationToken.parse("not-a-uuid-not-a-uuid-not-a-uuid-xxxx." + token.secret()).isEmpty());
    }

    @Test
    void thePrintedFormNeverCarriesTheSecret() {
        // Verifies the log hygiene of the record.
        ActivationToken token = ActivationToken.generate(ID);

        assertFalse(token.toString().contains(token.secret()));
        assertTrue(token.toString().contains(ID));
    }
}
