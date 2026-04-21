package org.jahia.modules.formidable.engine.actions;

import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Forwards the submitted form data to a third-party endpoint as multipart/form-data.
 * File parts are read via FormDataParser (Commons FileUpload + Tika security checks).
 * CAPTCHA token fields are stripped before forwarding.
 */
@Component(service = FormAction.class)
public class ForwardFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(ForwardFormAction.class);

    private final HttpClient http = HttpClient.newHttpClient();
    private FormidableConfigService config;

    @Reference
    public void setConfig(FormidableConfigService config) {
        this.config = config;
    }

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

        Map<String, String> fieldAcceptTypes = resolveFieldAcceptTypes(actionNode);

        List<FormDataParser.FormFile> files;
        try {
            files = FormDataParser.parseFiles(req, parameters, config, fieldAcceptTypes);
        } catch (FormDataParser.ParseException e) {
            throw new FormActionException(e.getMessage(), e.getHttpStatus());
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
            if (FormidableConstants.CAPTCHA_TOKEN_FIELDS.contains(name)) continue;
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

    /**
     * Walks the form node to find fmdb:inputFile nodes and collect their 'accept' values.
     * actionNode → fmdb:actionList → fmdb:form
     */
    private static Map<String, String> resolveFieldAcceptTypes(JCRNodeWrapper actionNode) {
        Map<String, String> result = new HashMap<>();
        try {
            JCRNodeWrapper formNode = actionNode.getParent().getParent();
            NodeIterator it = formNode.getNodes();
            while (it.hasNext()) {
                javax.jcr.Node child = it.nextNode();
                if (child instanceof JCRNodeWrapper w && w.isNodeType("fmdb:inputFile")) {
                    if (w.hasProperty("accept")) {
                        result.put(w.getName(), w.getProperty("accept").getString());
                    }
                }
            }
        } catch (RepositoryException e) {
            log.debug("[ForwardFormAction] Could not resolve field accept types: {}", e.getMessage());
        }
        return result;
    }
}
