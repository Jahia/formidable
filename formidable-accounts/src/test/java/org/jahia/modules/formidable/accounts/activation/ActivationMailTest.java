package org.jahia.modules.formidable.accounts.activation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivationMailTest {

    private static final ActivationToken TOKEN = ActivationToken.generate(UUID.randomUUID().toString());

    @Test
    void theLinkIsOnTheConfiguredOriginOnlyNeverOnARequestsHost() {
        // Verifies finding 2 of jahia-private#5909: the link takes the operator's origin and the platform's context
        // path; without an origin there is no link at all (the action refused the sign-up before).
        assertEquals("https://www.example.com/modules/formidable-accounts/verify?t=" + TOKEN.asParameter(),
                ActivationMail.link("https://www.example.com", "", TOKEN));
        assertEquals("https://site.example/jahia/modules/formidable-accounts/verify?t=" + TOKEN.asParameter(),
                ActivationMail.link("https://site.example", "/jahia", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> ActivationMail.link("", "", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> ActivationMail.link(null, "", TOKEN));
    }

    @Test
    void theBodyHasTheLinkAndTheHoursAndTheHtmlEscapesWhatIsNotMarkup() {
        // Verifies the composition: placeholders replaced, the text escaped in HTML, the link clickable.
        String link = "https://www.example.com/modules/formidable-accounts/verify?t=abc";
        ActivationMail mail = ActivationMail.compose("Activate <now>", "Hi <b>you</b>\n\n${link}\n\nValid ${hours} hours.", link, 48);

        assertEquals("Activate <now>", mail.subject());
        assertTrue(mail.text().contains(link) && mail.text().contains("48 hours"));
        assertFalse(mail.html().contains("<b>you</b>"), "markup in the template is escaped");
        assertTrue(mail.html().contains("Hi &lt;b&gt;you&lt;/b&gt;"));
        assertTrue(mail.html().contains("<a href=\"" + link + "\">" + link + "</a>"));
    }

    @Test
    void aSubjectIsOneLineWhateverTheTemplateHolds() {
        // Verifies header safety: no line break can survive into the subject.
        ActivationMail mail = ActivationMail.compose("Activate\r\nBcc: attacker@example.com", "${link}", "https://x", 1);

        assertFalse(mail.subject().contains("\n") || mail.subject().contains("\r"));
    }

    @Test
    void thePrintedFormNeverCarriesTheLink() {
        // Verifies the log hygiene: the body holds the secret.
        ActivationMail mail = ActivationMail.compose("S", "${link}", "https://x/?t=secret", 1);

        assertFalse(mail.toString().contains("secret"));
    }
}
