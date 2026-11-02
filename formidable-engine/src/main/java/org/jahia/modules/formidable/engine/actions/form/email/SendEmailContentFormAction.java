package org.jahia.modules.formidable.engine.actions.form.email;

import org.jahia.modules.formidable.engine.actions.form.ContentDispositionUtils;
import org.jahia.modules.formidable.engine.actions.common.FieldEscaper;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.engine.api.FmdbNodeType;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.modules.formidable.engine.options.ManualOptionsDisplayService;
import org.jahia.modules.formidable.engine.util.JcrProps;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.mail.MailMessage;
import org.jahia.services.mail.MailService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.mail.util.ByteArrayDataSource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sends the submitted form content by email, optionally with uploaded files attached.
 *
 * The contributor configures on the fmdb:emailContentAction node:
 *   - {@code to}                  – recipient address
 *   - {@code from}                – optional sender override
 *   - {@code attachFiles}         – whether validated uploaded files should be attached
 *   - {@code maxAttachmentSizeMb} – per-attachment size cap for this email action
 *
 * The email subject is derived from the submitted form title. The body lists the answered fields in the order of
 * the form, each under its title, a choice under the label of its option (see {@link ContentEmailRows}).
 */
@Component(service = FormAction.class)
public class SendEmailContentFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(SendEmailContentFormAction.class);

    private static final String DEFAULT_SUBJECT = "Form submission";

    private MailService mailService;
    private UploadsConfigService configService;
    private volatile ManualOptionsDisplayService optionsDisplayService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unbindMailService")
    protected void bindMailService(MailService service) {
        this.mailService = service;
    }

    protected void unbindMailService(MailService service) {
        if (this.mailService == service) {
            this.mailService = null;
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unbindOptionsDisplayService")
    protected void bindOptionsDisplayService(ManualOptionsDisplayService service) {
        this.optionsDisplayService = service;
    }

    protected void unbindOptionsDisplayService(ManualOptionsDisplayService service) {
        if (this.optionsDisplayService == service) {
            this.optionsDisplayService = null;
        }
    }

    @Reference
    protected void setConfigService(UploadsConfigService service) {
        this.configService = service;
    }

    @Override
    public String getNodeType() {
        return FmdbNodeType.EMAIL_CONTENT_ACTION;
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters,
            List<SubmittedFile> files
    ) throws FormActionException {
        if (mailService == null || !mailService.isEnabled()) {
            throw FormActionException.serverError("MailService is unavailable or disabled. Check Jahia SMTP configuration.");
        }

        String to = FieldEscaper.headerSafe(JcrProps.string(actionNode, "to", ""));
        if (to.isBlank()) {
            throw FormActionException.serverError(FmdbNodeType.EMAIL_CONTENT_ACTION + " is missing a 'to' address.");
        }

        // The addresses the mail service would refuse later, in the background, are left out here and
        // logged (EmailAddresses): a typo costs an e-mail, never the submission.
        Optional<String> recipients = EmailAddresses.recipients(FmdbNodeType.EMAIL_CONTENT_ACTION, actionNode.getPath(), to);
        if (recipients.isEmpty()) {
            return;
        }
        String sentTo = recipients.get();
        String from = EmailAddresses.sender(FmdbNodeType.EMAIL_CONTENT_ACTION, actionNode.getPath(),
                FieldEscaper.headerSafe(JcrProps.string(actionNode, "from", "")), mailService.defaultSender());
        JCRNodeWrapper formNode = formNode(actionNode);
        String subject = FieldEscaper.headerSafe(resolveFormSubject(formNode));
        List<ContentEmailRows.Row> rows = ContentEmailRows.of(formNode, parameters, this::displayedOptions);

        MailMessage message = new MailMessage();
        message.setTo(sentTo);
        message.setFrom(from);
        message.setSubject(subject.isBlank() ? DEFAULT_SUBJECT : subject);
        message.setTextBody(buildTextBody(subject, rows));
        message.setHtmlBody(buildHtmlBody(subject, rows));

        if (JcrProps.bool(actionNode, "attachFiles", false)) {
            message.setAttachments(buildAttachments(actionNode, files));
        }

        try {
            // Note: Jahia's MailService.sendMessage() queues the message through a Camel route.
            // SMTP delivery failures on the asynchronous delivery path are logged by Jahia/Camel
            // and do not propagate back to this call site. The catch block below only handles
            // synchronous failures raised while invoking the mail service.
            mailService.sendMessage(message);
            log.debug("Email content sent to '{}' with subject '{}'", sentTo, subject);
        } catch (Exception e) {
            throw FormActionException.serverError(
                    "Failed to send form content email to '" + to + "': " + e.getMessage(),
                    e
            );
        }
    }

    /** The form of an action: the parent of its action list; null when it cannot be read. */
    private static JCRNodeWrapper formNode(JCRNodeWrapper actionNode) {
        try {
            JCRNodeWrapper actionsNode = actionNode.getParent();
            return actionsNode == null ? null : actionsNode.getParent();
        } catch (RepositoryException e) {
            log.debug("Could not resolve the form of action node '{}': {}", actionNode.getPath(), e.getMessage());
            return null;
        }
    }

    /** The option entries of a choice field in the submission's language, as its view renders them. */
    private String[] displayedOptions(JCRNodeWrapper field) throws RepositoryException {
        ManualOptionsDisplayService display = optionsDisplayService;
        String[] aligned = display == null ? null : display.forDisplay(field, field.getLanguage());
        if (aligned != null) {
            return aligned;
        }
        if (!field.hasProperty(FmdbProperty.OPTIONS)) {
            return null;
        }
        JCRPropertyWrapper options = field.getProperty(FmdbProperty.OPTIONS);
        if (!options.isMultiple()) {
            return null;
        }
        Value[] values = options.getValues();
        String[] entries = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            entries[i] = values[i].getString();
        }
        return entries;
    }

    private static String resolveFormSubject(JCRNodeWrapper formNode) {
        if (formNode == null) {
            return DEFAULT_SUBJECT;
        }

        String title = JcrProps.string(formNode, "jcr:title", "");
        if (!title.isBlank()) {
            return title;
        }

        String displayableName = formNode.getDisplayableName();
        if (displayableName != null && !displayableName.isBlank()) {
            return displayableName;
        }
        return DEFAULT_SUBJECT;
    }

    private static String buildTextBody(String subject, List<ContentEmailRows.Row> rows) {
        StringBuilder body = new StringBuilder();
        if (subject != null && !subject.isBlank()) {
            body.append(subject).append("\n\n");
        }

        for (ContentEmailRows.Row row : rows) {
            body.append(FieldEscaper.plainText(row.heading()))
                    .append(": ")
                    .append(joinValues(row.values()))
                    .append("\n");
        }
        return body.toString();
    }

    private static String buildHtmlBody(String subject, List<ContentEmailRows.Row> rows) {
        StringBuilder body = new StringBuilder();
        body.append("<html><body>");
        body.append("<h2>").append(FieldEscaper.html(subject)).append("</h2>");
        body.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\">");

        for (ContentEmailRows.Row row : rows) {
            body.append("<tr><th align=\"left\">")
                    .append(FieldEscaper.html(row.heading()))
                    .append("</th><td>")
                    .append(htmlEscapeJoinedValues(row.values()))
                    .append("</td></tr>");
        }

        body.append("</table></body></html>");
        return body.toString();
    }

    private Map<String, DataHandler> buildAttachments(
            JCRNodeWrapper actionNode,
            List<SubmittedFile> files
    ) {
        long configuredBytes = readMaxAttachmentSizeBytes(actionNode);
        long effectiveMaxBytes = Math.min(configuredBytes, configService.getUploadMaxFileSizeBytes());
        Map<String, DataHandler> attachments = new LinkedHashMap<>();

        for (SubmittedFile file : files) {
            // size() reads the stored length; data() clones the whole array, so it is
            // called once per kept attachment, never for a mere size check.
            if (file.size() > effectiveMaxBytes) {
                log.info(
                        "Skipping attachment '{}' ({} bytes) for fmdb:emailContentAction: exceeds effective limit {} bytes.",
                        file.originalName(),
                        file.size(),
                        effectiveMaxBytes
                );
                continue;
            }
            try {
                String attachmentName = ContentDispositionUtils.toRfc6266FilenameFallback(file.originalName());
                ByteArrayDataSource dataSource = new ByteArrayDataSource(file.data(), file.mimeType());
                dataSource.setName(attachmentName);
                attachments.put(attachmentName, new DataHandler(dataSource));
            } catch (Exception e) {
                log.warn("Could not attach file '{}' to form content email: {}", file.originalName(), e.getMessage());
            }
        }

        return attachments;
    }

    private static String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(FieldEscaper::plainText)
                .collect(Collectors.joining(", "));
    }

    private static String htmlEscapeJoinedValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(FieldEscaper::html)
                .collect(Collectors.joining("<br/>"));
    }

    private static long readMaxAttachmentSizeBytes(JCRNodeWrapper actionNode) {
        long configuredMb = JcrProps.longValue(actionNode, "maxAttachmentSizeMb", 10L);

        if (configuredMb <= 0) {
            configuredMb = 1L;
        }
        return configuredMb * 1024L * 1024L;
    }

}
