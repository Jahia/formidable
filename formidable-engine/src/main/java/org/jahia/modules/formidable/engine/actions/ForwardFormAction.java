package org.jahia.modules.formidable.engine.actions;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Forwards the submitted form data to a third-party endpoint as multipart/form-data.
 * Text parameters and pre-parsed files (from request attribute) are forwarded as-is.
 */
@Component(service = FormAction.class)
public class ForwardFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(ForwardFormAction.class);

    private final HttpClient http = HttpClient.newHttpClient();

    @Override
    public String getNodeType() {
        return "fmdb:forwardAction";
    }

    @Override
    public void execute(
            JCRNodeWrapper actionNode,
            HttpServletRequest req,
            RenderContext renderContext,
            JCRSessionWrapper session,
            Map<String, List<String>> parameters
    ) throws FormActionException {

        String targetUrl;
        try {
            if (!actionNode.hasProperty("targetUrl")) {
                log.warn("[ForwardFormAction] targetUrl is not set on node '{}', skipping.", actionNode.getPath());
                return;
            }
            targetUrl = actionNode.getProperty("targetUrl").getString();
        } catch (RepositoryException e) {
            log.warn("[ForwardFormAction] Could not read targetUrl from node '{}'", actionNode.getPath(), e);
            return;
        }

        if (targetUrl == null || targetUrl.isBlank()) {
            log.warn("[ForwardFormAction] targetUrl is blank on node '{}', skipping.", actionNode.getPath());
            return;
        }

        @SuppressWarnings("unchecked")
        List<FormDataParser.FormFile> files =
                (List<FormDataParser.FormFile>) req.getAttribute(FormDataParser.PARSED_FILES_ATTR);
        if (files == null) {
            // PARSED_FILES_ATTR is always set by FormSubmitServlet before the pipeline runs.
            // A null value here means the request bypassed the servlet — this must never happen.
            throw new FormActionException(
                    "Form submission did not go through the expected servlet endpoint.", 500);
        }

        String boundary = UUID.randomUUID().toString();
        byte[] body;
        try {
            body = buildMultipartBody(parameters, files, boundary);
        } catch (IOException e) {
            log.error("[ForwardFormAction] Failed to build multipart body", e);
            throw new FormActionException("Failed to build form data.", 500);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[ForwardFormAction] Target '{}' returned HTTP {}", targetUrl, response.statusCode());
                throw new FormActionException("Forward target returned HTTP " + response.statusCode(), 502);
            }
        } catch (FormActionException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ForwardFormAction] Request to '{}' failed", targetUrl, e);
            throw new FormActionException("Failed to forward form data to target.", 502);
        }
    }

    private static byte[] buildMultipartBody(
            Map<String, List<String>> parameters,
            List<FormDataParser.FormFile> files,
            String boundary
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] dashdash = "--".getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                writePart(out, dashdash, boundaryBytes, crlf, name, null, null, value.getBytes(StandardCharsets.UTF_8));
            }
        }

        for (FormDataParser.FormFile file : files) {
            writePart(out, dashdash, boundaryBytes, crlf,
                    file.fieldName(), file.originalName(), file.mimeType(), file.data());
        }

        out.write(dashdash);
        out.write(boundaryBytes);
        out.write(dashdash);
        out.write(crlf);

        return out.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream out, byte[] dashdash, byte[] boundary, byte[] crlf,
                                   String name, String filename, String contentType, byte[] data) throws IOException {
        out.write(dashdash);
        out.write(boundary);
        out.write(crlf);

        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null && !filename.isEmpty()) {
            disposition += "; filename=\"" + filename + "\"";
        }
        out.write(disposition.getBytes(StandardCharsets.UTF_8));
        out.write(crlf);

        if (contentType != null) {
            out.write(("Content-Type: " + contentType).getBytes(StandardCharsets.UTF_8));
            out.write(crlf);
        }

        out.write(crlf);
        out.write(data);
        out.write(crlf);
    }
}
