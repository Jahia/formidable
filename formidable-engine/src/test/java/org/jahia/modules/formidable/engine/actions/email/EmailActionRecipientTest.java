package org.jahia.modules.formidable.engine.actions.email;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.mail.MailMessage;
import org.jahia.services.mail.MailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.jahia.modules.formidable.engine.actions.TemplateInterpolator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailActionRecipientTest {

    @Test
    void notificationActionKeepsRecipientLiteral() throws Exception {
        // Given a notification action whose static recipient contains a placeholder-looking string,
        // the action must keep it literal and send exactly that address as-is.
        JCRNodeWrapper actionNode = mockActionNodeWithTo("team+${email}@example.com");
        MailService mailService = mock(MailService.class);
        ArgumentCaptor<MailMessage> messageCaptor = ArgumentCaptor.forClass(MailMessage.class);

        doNothing().when(mailService).sendMessage(any(MailMessage.class));

        SendEmailNotificationFormAction action = new SendEmailNotificationFormAction();
        action.bindMailService(mailService);
        action.execute(actionNode, null, null, java.util.Map.of("email", java.util.List.of("guest@example.net")), java.util.List.of());

        verify(mailService).sendMessage(messageCaptor.capture());
        assertEquals("team+${email}@example.com", messageCaptor.getValue().getTo());
    }

    @Test
    void notificationActionSanitizesFromHeader() throws Exception {
        // Given a From value containing CRLF header-injection characters,
        // the action must flatten them before building the outgoing message.
        JCRNodeWrapper actionNode = mockActionNode("team@example.com", "sender@example.com\r\nBcc:evil@example.com");
        MailService mailService = mock(MailService.class);
        ArgumentCaptor<MailMessage> messageCaptor = ArgumentCaptor.forClass(MailMessage.class);

        doNothing().when(mailService).sendMessage(any(MailMessage.class));

        SendEmailNotificationFormAction action = new SendEmailNotificationFormAction();
        action.bindMailService(mailService);
        action.execute(actionNode, null, null, java.util.Map.of(), java.util.List.of());

        verify(mailService).sendMessage(messageCaptor.capture());
        assertEquals("sender@example.com  Bcc:evil@example.com", messageCaptor.getValue().getFrom());
    }

    @Test
    void notificationActionOmitsBlankFromHeader() throws Exception {
        // Given a From value that becomes blank after sanitization,
        // the action must omit the sender override instead of sending an empty header.
        JCRNodeWrapper actionNode = mockActionNode("team@example.com", " \r\n\t ");
        MailService mailService = mock(MailService.class);
        ArgumentCaptor<MailMessage> messageCaptor = ArgumentCaptor.forClass(MailMessage.class);

        doNothing().when(mailService).sendMessage(any(MailMessage.class));

        SendEmailNotificationFormAction action = new SendEmailNotificationFormAction();
        action.bindMailService(mailService);
        action.execute(actionNode, null, null, java.util.Map.of(), java.util.List.of());

        verify(mailService).sendMessage(messageCaptor.capture());
        assertNull(messageCaptor.getValue().getFrom());
    }

    @Test
    void contentActionKeepsRecipientLiteral() throws Exception {
        // Given the content email action with a static recipient containing a placeholder-looking string,
        // the action must also keep it literal to match the notification action contract.
        JCRNodeWrapper actionNode = mockActionNodeWithTo("team+${email}@example.com");
        MailService mailService = mock(MailService.class);
        ArgumentCaptor<MailMessage> messageCaptor = ArgumentCaptor.forClass(MailMessage.class);

        doNothing().when(mailService).sendMessage(any(MailMessage.class));

        SendEmailContentFormAction action = new SendEmailContentFormAction();
        action.bindMailService(mailService);
        action.execute(actionNode, null, null, java.util.Map.of("email", java.util.List.of("guest@example.net")), java.util.List.of());

        verify(mailService).sendMessage(messageCaptor.capture());
        assertEquals("team+${email}@example.com", messageCaptor.getValue().getTo());
    }

    @Test
    void notificationInterpolationEscapesHtmlAtOutputWithoutMutatingInput() {
        // Given a submitted plain-text value containing HTML-like content,
        // interpolation into an HTML email body must preserve the raw value in memory and escape it only at output.
        String template = "<p>${comment}</p>";
        Map<String, List<String>> parameters = Map.of(
                "comment", List.of("<script>alert(1)</script><!-- note -->")
        );

        // When the notification template is interpolated for an HTML sink.
        String html = TemplateInterpolator.interpolate(template, parameters, org.jahia.modules.formidable.engine.actions.FieldEscaper::html);
        String plainText = TemplateInterpolator.interpolate("${comment}", parameters, org.jahia.modules.formidable.engine.actions.FieldEscaper::plainText);

        // Then the HTML output is escaped, while the plain-text path keeps the original submitted value unchanged.
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;&lt;!-- note --&gt;</p>", html);
        assertEquals("<script>alert(1)</script><!-- note -->", plainText);
    }

    private static JCRNodeWrapper mockActionNodeWithTo(String to) throws Exception {
        return mockActionNode(to, null);
    }

    private static JCRNodeWrapper mockActionNode(String to, String from) throws Exception {
        JCRNodeWrapper actionNode = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper toProperty = mock(JCRPropertyWrapper.class);

        when(actionNode.hasProperty("to")).thenReturn(true);
        when(actionNode.getProperty("to")).thenReturn(toProperty);
        when(toProperty.getString()).thenReturn(to);

        if (from != null) {
            JCRPropertyWrapper fromProperty = mock(JCRPropertyWrapper.class);
            when(actionNode.hasProperty("from")).thenReturn(true);
            when(actionNode.getProperty("from")).thenReturn(fromProperty);
            when(fromProperty.getString()).thenReturn(from);
        }

        return actionNode;
    }
}
