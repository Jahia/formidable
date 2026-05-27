package org.jahia.modules.formidable.engine.actions.forward;

import org.jahia.modules.formidable.engine.actions.ContentDispositionUtils;
import org.jahia.modules.formidable.engine.actions.FormAction;
import org.jahia.modules.formidable.engine.actions.FormActionException;
import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Forwards the submitted form data to a third-party endpoint as multipart/form-data.
 * Text parameters and pre-parsed files (from request attribute) are forwarded as-is.
 *
 * The target URL is never stored in JCR. The JCR node only holds a stable {@code targetId}
 * that is resolved to a URI via operator configuration (forwardTargets and, optionally,
 * devForwardTargets in org.jahia.modules.formidable.cfg). This is the primary defence
 * against contributors redirecting submissions to arbitrary hosts.
 *
 * Defence in depth: at execution time, the resolved hostname is checked once and the
 * request is rejected if any resolved address is loopback, site-local, link-local,
 * any-local or multicast. This catches operator misconfiguration, such as an allowlisted
 * hostname pointing to an internal service, but it does not provide hard guarantees
 * against DNS rebinding because HttpClient resolves the hostname again when sending
 * the request. The operator allowlist remains the trust boundary.
 */
@Component(service = FormAction.class)
public class ForwardSubmissionFormAction implements FormAction {

    private static final Logger log = LoggerFactory.getLogger(ForwardSubmissionFormAction.class);

    private FormidableConfigService configService;
    private HostnameResolutionService hostnameResolutionService;
    private volatile HttpClient forwardHttpClient;
    private volatile Duration forwardHttpConnectTimeout;

    @Reference
    public void setConfigService(FormidableConfigService service) {
        this.configService = service;
    }

    @Reference
    public void setHostnameResolutionService(HostnameResolutionService service) {
        this.hostnameResolutionService = service;
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

        String targetId;
        try {
            if (!actionNode.hasProperty("targetId")) {
                log.warn("[ForwardSubmissionFormAction] targetId is not set on node '{}', skipping.", actionNode.getPath());
                return;
            }
            targetId = actionNode.getProperty("targetId").getString();
        } catch (RepositoryException e) {
            log.warn("[ForwardSubmissionFormAction] Could not read targetId from node '{}'", actionNode.getPath(), e);
            return;
        }

        if (targetId == null || targetId.isBlank()) {
            log.warn("[ForwardSubmissionFormAction] targetId is blank on node '{}', skipping.", actionNode.getPath());
            return;
        }

        FormidableConfigService.ForwardTarget target = configService.resolveForwardTarget(targetId).orElseThrow(() -> {
            log.warn("[ForwardSubmissionFormAction] targetId '{}' on node '{}' does not match any configured forward target.",
                    targetId, actionNode.getPath());
            return new FormActionException("Forward target '" + targetId + "' is not configured.", 403);
        });
        URI targetUri = target.uri();

        checkNotPrivateAddress(targetUri, target.development());

        @SuppressWarnings("unchecked")
        List<FormDataParser.FormFile> files =
                (List<FormDataParser.FormFile>) req.getAttribute(FormDataParser.PARSED_FILES_ATTR);
        if (files == null) {
            throw new FormActionException(
                    "Form submission did not go through the expected servlet endpoint.", 500);
        }

        String boundary = UUID.randomUUID().toString();
        byte[] body;
        try {
            body = buildMultipartBody(parameters, files, boundary);
        } catch (IOException e) {
            log.error("[ForwardSubmissionFormAction] Failed to build multipart body", e);
            throw new FormActionException("Failed to build form data.", 500);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .timeout(configService.getForwardHttpRequestTimeout())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<Void> response = getForwardHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[ForwardSubmissionFormAction] Target '{}' returned HTTP {}", targetUri, response.statusCode());
                throw new FormActionException("Forward target returned HTTP " + response.statusCode(), 502);
            }
        } catch (FormActionException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ForwardSubmissionFormAction] Request to '{}' failed", targetUri, e);
            throw new FormActionException("Failed to forward form data to target.", 502);
        }
    }

    void checkNotPrivateAddress(URI uri, boolean allowDevelopmentEndpoint) throws FormActionException {
        String hostname = uri.getHost();
        if (hostname == null || hostname.isBlank()) {
            throw new FormActionException("Forward target URI has no valid hostname.", 400);
        }
        if (allowDevelopmentEndpoint && isAllowedDevelopmentEndpoint(uri)) {
            return;
        }
        try {
            InetAddress[] addresses = hostnameResolutionService.resolveAll(hostname);
            if (addresses.length == 0) {
                throw new FormActionException("Forward target hostname cannot be resolved.", 400);
            }
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress()) {
                    log.warn("[ForwardSubmissionFormAction] Rejected target '{}': '{}' resolves to a private/internal address ({}).",
                            uri, hostname, addr.getHostAddress());
                    throw new FormActionException(
                            "Forward target resolves to a private or internal address.", 403);
                }
            }
        } catch (TimeoutException e) {
            log.warn("[ForwardSubmissionFormAction] Rejected target '{}': hostname resolution timed out.", uri);
            throw new FormActionException("Forward target hostname resolution timed out.", 502, e);
        } catch (UnknownHostException e) {
            log.warn("[ForwardSubmissionFormAction] Rejected target '{}': hostname cannot be resolved.", uri);
            throw new FormActionException("Forward target hostname cannot be resolved.", 400);
        } catch (RuntimeException e) {
            log.error("[ForwardSubmissionFormAction] Failed to resolve hostname for target '{}'.", uri, e);
            throw new FormActionException("Failed to resolve forward target hostname.", 502, e);
        }
    }

    private static boolean isAllowedDevelopmentEndpoint(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "host.docker.internal".equalsIgnoreCase(host);
    }

    HttpClient getForwardHttpClient() {
        Duration desiredTimeout = configService.getForwardHttpConnectTimeout();
        HttpClient currentClient = forwardHttpClient;
        Duration currentTimeout = forwardHttpConnectTimeout;

        if (currentClient != null && desiredTimeout.equals(currentTimeout)) {
            return currentClient;
        }

        synchronized (this) {
            currentClient = forwardHttpClient;
            currentTimeout = forwardHttpConnectTimeout;
            if (currentClient == null || !desiredTimeout.equals(currentTimeout)) {
                forwardHttpClient = HttpClient.newBuilder()
                        .connectTimeout(desiredTimeout)
                        .build();
                forwardHttpConnectTimeout = desiredTimeout;
            }
            return forwardHttpClient;
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
            disposition += "; filename=\"" + ContentDispositionUtils.toAsciiFilenameFallback(filename) + "\"";
            disposition += "; filename*=UTF-8''" + ContentDispositionUtils.encodeRfc5987(filename);
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
