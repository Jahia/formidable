package org.jahia.modules.formidable.engine.actions.email;

import org.jahia.modules.formidable.engine.actions.FieldSanitizer;
import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.mail.MailMessage;
import org.jahia.services.mail.MailService;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends a notification email after a successful form submission.
 *
 * The contributor configures on the fmdb:emailNotificationAction node:
 *   - {@code to}              – recipient address (supports ${fieldName} interpolation)
 *   - {@code from}            – optional sender override
 *   - {@code subject}         – email subject (supports ${fieldName} interpolation)
 *   - {@code templateMessage} – HTML body    (supports ${fieldName} interpolation)
 *
 * Requires Jahia's MailService to be configured (SMTP settings in Jahia administration).
 */
@Component(service = FormAction.class)
public class SendEmailNotificationFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(SendEmailNotificationFormAction.class);

    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([^}]+)}");

    private MailService mailService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unbindMailService")
    protected void bindMailService(MailService service) {
        this.mailService = service;
    }

    protected void unbindMailService(MailService service) {
        this.mailService = null;
    }

    @Override
    public String getNodeType() {
        return "fmdb:emailNotificationAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {

        if (mailService == null) {
            throw FormActionException.serverError("MailService is unavailable. Check Jahia SMTP configuration.");
        }

        String to = interpolate(readProperty(actionNode, "to"), parameters, false);
        if (to == null || to.isBlank()) {
            throw FormActionException.serverError("fmdb:emailNotificationAction is missing a 'to' address.");
        }
        to = FieldSanitizer.headerSafe(to);

        String from = readProperty(actionNode, "from");
        String subject = FieldSanitizer.headerSafe(
                interpolate(readProperty(actionNode, "subject"), parameters, false));
        String htmlBody = interpolate(readProperty(actionNode, "templateMessage"), parameters, true);

        MailMessage message = new MailMessage();
        message.setTo(to);
        message.setFrom(from);
        message.setSubject(subject != null ? subject : "");
        message.setHtmlBody(htmlBody);

        try {
            mailService.sendMessage(message);
            log.debug("Email notification sent to '{}' with subject '{}'", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email notification to '{}'", to, e);
            throw FormActionException.serverError("Failed to send email notification: " + e.getMessage());
        }
    }

    /**
     * Replaces {@code ${fieldName}} placeholders with form parameter values.
     * When {@code escapeHtmlValues} is true, values are HTML-encoded via {@link FieldSanitizer#htmlEncode}
     * before insertion (use for HTML email body). Pass false for plain-text fields like
     * addresses or subjects.
     * Unknown placeholders are replaced with an empty string.
     */
    static String interpolate(String template, Map<String, List<String>> parameters, boolean escapeHtmlValues) {
        if (template == null) return null;
        Matcher m = INTERPOLATION.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String field = m.group(1);
            List<String> values = parameters.get(field);
            String raw = (values != null && !values.isEmpty()) ? values.get(0) : "";
            String replacement = escapeHtmlValues ? FieldSanitizer.htmlEncode(raw) : FieldSanitizer.plainText(raw);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String readProperty(JCRNodeWrapper node, String name) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
