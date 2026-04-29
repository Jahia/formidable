package org.jahia.modules.formidable.engine.actions.email;

import org.jahia.modules.formidable.engine.actions.ContentDispositionUtils;
import org.jahia.modules.formidable.engine.actions.FieldSanitizer;
import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
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

import javax.activation.DataHandler;
import javax.jcr.RepositoryException;
import javax.mail.util.ByteArrayDataSource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The email subject is derived from the submitted form title.
 */
@Component(service = FormAction.class)
public class SendEmailContentFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(SendEmailContentFormAction.class);

    private static final String DEFAULT_SUBJECT = "Form submission";

    private MailService mailService;
    private FormidableConfigService configService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unbindMailService")
    protected void bindMailService(MailService service) {
        this.mailService = service;
    }

    protected void unbindMailService(MailService service) {
        this.mailService = null;
    }

    @Reference
    protected void setConfigService(FormidableConfigService service) {
        this.configService = service;
    }

    @Override
    public String getNodeType() {
        return "fmdb:emailContentAction";
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

        String to = FieldSanitizer.headerSafe(readProperty(actionNode, "to"));
        if (to.isBlank()) {
            throw FormActionException.serverError("fmdb:emailContentAction is missing a 'to' address.");
        }

        String from = FieldSanitizer.headerSafe(readProperty(actionNode, "from"));
        String subject = FieldSanitizer.headerSafe(resolveFormSubject(actionNode));

        MailMessage message = new MailMessage();
        message.setTo(to);
        message.setFrom(from.isBlank() ? null : from);
        message.setSubject(subject.isBlank() ? DEFAULT_SUBJECT : subject);
        message.setTextBody(buildTextBody(subject, parameters));
        message.setHtmlBody(buildHtmlBody(subject, parameters));

        if (readBooleanProperty(actionNode, "attachFiles")) {
            @SuppressWarnings("unchecked")
            List<FormDataParser.FormFile> files =
                    (List<FormDataParser.FormFile>) req.getAttribute(FormDataParser.PARSED_FILES_ATTR);
            if (files == null) {
                throw FormActionException.serverError(
                        "Validated uploaded files are unavailable for fmdb:emailContentAction.");
            }
            message.setAttachments(buildAttachments(actionNode, files));
        }

        try {
            mailService.sendMessage(message);
            log.debug("Email content sent to '{}' with subject '{}'", to, subject);
        } catch (Exception e) {
            log.error("Failed to send form content email to '{}'", to, e);
            throw FormActionException.serverError("Failed to send form content email: " + e.getMessage());
        }
    }

    private static String resolveFormSubject(JCRNodeWrapper actionNode) {
        try {
            JCRNodeWrapper actionsNode = (JCRNodeWrapper) actionNode.getParent();
            if (actionsNode == null) {
                return DEFAULT_SUBJECT;
            }
            JCRNodeWrapper formNode = (JCRNodeWrapper) actionsNode.getParent();
            if (formNode == null) {
                return DEFAULT_SUBJECT;
            }

            String title = readProperty(formNode, "jcr:title");
            if (!title.isBlank()) {
                return title;
            }

            String displayableName = formNode.getDisplayableName();
            if (displayableName != null && !displayableName.isBlank()) {
                return displayableName;
            }
        } catch (RepositoryException e) {
            log.debug("Could not resolve form subject from action node '{}': {}", safeNodePath(actionNode), e.getMessage());
        }
        return DEFAULT_SUBJECT;
    }

    private static String buildTextBody(String subject, Map<String, List<String>> parameters) {
        StringBuilder body = new StringBuilder();
        if (subject != null && !subject.isBlank()) {
            body.append(subject).append("\n\n");
        }

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            body.append(entry.getKey())
                    .append(": ")
                    .append(joinValues(entry.getValue()))
                    .append("\n");
        }
        return body.toString();
    }

    private static String buildHtmlBody(String subject, Map<String, List<String>> parameters) {
        StringBuilder body = new StringBuilder();
        body.append("<html><body>");
        body.append("<h2>").append(FieldSanitizer.htmlEncode(subject)).append("</h2>");
        body.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\">");

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            body.append("<tr><th align=\"left\">")
                    .append(FieldSanitizer.htmlEncode(entry.getKey()))
                    .append("</th><td>")
                    .append(htmlEncodeJoinedValues(entry.getValue()))
                    .append("</td></tr>");
        }

        body.append("</table></body></html>");
        return body.toString();
    }

    private Map<String, DataHandler> buildAttachments(
            JCRNodeWrapper actionNode,
            List<FormDataParser.FormFile> files
    ) {
        long configuredBytes = readMaxAttachmentSizeBytes(actionNode);
        long effectiveMaxBytes = Math.min(configuredBytes, configService.getUploadMaxFileSizeBytes());
        Map<String, DataHandler> attachments = new LinkedHashMap<>();

        for (FormDataParser.FormFile file : files) {
            if (file.data().length > effectiveMaxBytes) {
                log.info(
                        "Skipping attachment '{}' ({} bytes) for fmdb:emailContentAction: exceeds effective limit {} bytes.",
                        file.originalName(),
                        file.data().length,
                        effectiveMaxBytes
                );
                continue;
            }
            try {
                String attachmentName = ContentDispositionUtils.toAsciiFilenameFallback(file.originalName());
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
                .map(FieldSanitizer::plainText)
                .collect(Collectors.joining(", "));
    }

    private static String htmlEncodeJoinedValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(FieldSanitizer::htmlEncode)
                .collect(Collectors.joining("<br/>"));
    }

    private static boolean readBooleanProperty(JCRNodeWrapper node, String name) {
        try {
            return node.hasProperty(name) && node.getProperty(name).getBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static long readMaxAttachmentSizeBytes(JCRNodeWrapper actionNode) {
        long configuredMb = 10L;
        try {
            if (actionNode.hasProperty("maxAttachmentSizeMb")) {
                configuredMb = actionNode.getProperty("maxAttachmentSizeMb").getLong();
            }
        } catch (Exception e) {
            return 10L * 1024L * 1024L;
        }

        if (configuredMb <= 0) {
            configuredMb = 1L;
        }
        return configuredMb * 1024L * 1024L;
    }

    private static String readProperty(JCRNodeWrapper node, String name) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeNodePath(JCRNodeWrapper node) {
        return node.getPath();
    }
}
