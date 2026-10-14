package org.jahia.modules.formidable.accounts.activation;

import org.jahia.services.mail.MailService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JahiaActivationMailerTest {

    private final JahiaActivationMailer mailer = new JahiaActivationMailer();

    private MailService enabledService() {
        MailService service = mock(MailService.class);
        when(service.isEnabled()).thenReturn(true);
        when(service.defaultSender()).thenReturn("jahia@example.com");
        mailer.bindMailService(service);
        return service;
    }

    @Test
    void withoutAServiceOrWithADisabledOneNothingCanBeSent() {
        // Verifies availability: absent and disabled both read as unavailable, and send refuses.
        assertFalse(mailer.isAvailable());
        assertThrows(ActivationMailer.MailUnavailableException.class, () -> mailer.send("", "ada@example.com", "s", "t", "h"));

        MailService disabled = mock(MailService.class);
        when(disabled.isEnabled()).thenReturn(false);
        mailer.bindMailService(disabled);
        assertFalse(mailer.isAvailable());
        mailer.unbindMailService(disabled);
        assertFalse(mailer.isAvailable());
    }

    @Test
    void aMessageIsSentFromTheConfiguredSenderOrJahiasDefault() throws Exception {
        // Verifies the call: sender fallback, recipient, subject and both bodies, no cc or bcc.
        MailService service = enabledService();
        assertTrue(mailer.isAvailable());

        mailer.send("", "ada@example.com", "Subject", "text", "<p>html</p>");
        mailer.send("no-reply@example.com", "ada@example.com", "Subject", "text", "<p>html</p>");

        verify(service).sendMessage("jahia@example.com", "ada@example.com", null, null, "Subject", "text", "<p>html</p>");
        verify(service).sendMessage("no-reply@example.com", "ada@example.com", null, null, "Subject", "text", "<p>html</p>");
    }

    @Test
    void aRecipientThatIsNotAnAddressOrCarriesALineBreakIsRefusedBeforeAnythingIsSent() throws Exception {
        // Verifies header safety: the recipient comes from the visitor.
        MailService service = enabledService();

        for (String hostile : new String[]{"not an address", "ada@example.com\r\nBcc: x@example.com", "", null}) {
            assertThrows(ActivationMailer.MailUnavailableException.class, () -> mailer.send("", hostile, "s", "t", "h"), String.valueOf(hostile));
        }
        verify(service, never()).sendMessage(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void aRefusalOfTheMailServiceIsReportedAsUnavailable() {
        // Verifies the synchronous failure path (the asynchronous one is the platform's to log).
        MailService service = enabledService();
        doThrow(new IllegalStateException("smtp")).when(service).sendMessage("jahia@example.com", "ada@example.com", null, null, "s", "t", "h");

        assertThrows(ActivationMailer.MailUnavailableException.class, () -> mailer.send("", "ada@example.com", "s", "t", "h"));
    }
}
