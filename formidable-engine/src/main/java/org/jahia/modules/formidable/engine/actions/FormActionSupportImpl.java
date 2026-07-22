package org.jahia.modules.formidable.engine.actions;

import org.jahia.modules.formidable.engine.actions.forward.HostnameResolutionService;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionSupport;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Default {@link FormActionSupport}. Hosts the security-sensitive pieces shared with
 * out-of-bundle (TypeScript) form actions.
 *
 * Forwarding: the target URL is never stored in JCR nor exposed to callers. The
 * {@code targetId} is resolved to a URI via operator configuration (forwardTargets and,
 * optionally, devForwardTargets in org.jahia.modules.formidable.cfg). Defence in depth:
 * at execution time, the resolved hostname is checked once and the request is rejected
 * if any resolved address is loopback, site-local, link-local, any-local or multicast.
 * This catches operator misconfiguration but does not provide hard guarantees against
 * DNS rebinding because HttpClient resolves the hostname again when sending the request.
 * The operator allowlist remains the trust boundary.
 */
@Component(service = FormActionSupport.class)
public class FormActionSupportImpl implements FormActionSupport {

    private static final Logger log = LoggerFactory.getLogger(FormActionSupportImpl.class);

    private FormidableConfigService configService;
    private HostnameResolutionService hostnameResolutionService;

    @Reference
    public void setConfigService(FormidableConfigService service) {
        this.configService = service;
    }

    @Reference
    public void setHostnameResolutionService(HostnameResolutionService service) {
        this.hostnameResolutionService = service;
    }

    @Override
    public long getUploadMaxFileSizeBytes() {
        return configService.getUploadMaxFileSizeBytes();
    }

    @Override
    public Map<String, DataHandler> buildEmailAttachments(List<SubmittedFile> files, long maxAttachmentSizeBytes) {
        long effectiveMaxBytes = Math.min(maxAttachmentSizeBytes, configService.getUploadMaxFileSizeBytes());
        Map<String, DataHandler> attachments = new LinkedHashMap<>();

        for (SubmittedFile file : files) {
            if (file.data().length > effectiveMaxBytes) {
                log.info(
                        "Skipping attachment '{}' ({} bytes): exceeds effective limit {} bytes.",
                        file.originalName(),
                        file.data().length,
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
                log.warn("Could not build email attachment for file '{}': {}", file.originalName(), e.getMessage());
            }
        }

        return attachments;
    }

    @Override
    public void forwardSubmission(String targetId, Map<String, List<String>> parameters, List<SubmittedFile> files)
            throws FormActionException {
        if (targetId == null || targetId.isBlank()) {
            throw FormActionException.badRequest("Forward target id must not be blank.");
        }

        FormidableConfigService.ForwardTarget target = configService.resolveForwardTarget(targetId).orElseThrow(() -> {
            log.warn("[FormActionSupport] targetId '{}' does not match any configured forward target.", targetId);
            return new FormActionException("Forward target '" + targetId + "' is not configured.", 403);
        });
        URI targetUri = target.uri();

        checkNotPrivateAddress(targetUri, target.development());

        String boundary = UUID.randomUUID().toString();
        byte[] body;
        try {
            body = buildMultipartBody(parameters, files, boundary);
        } catch (IOException e) {
            throw new FormActionException("Failed to build multipart form payload for forward target '" + targetId + "'.", 500, e);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .timeout(configService.getForwardHttpRequestTimeout())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<Void> response = configService.getForwardHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[FormActionSupport] Target '{}' returned HTTP {}", targetUri, response.statusCode());
                throw new FormActionException("Forward target returned HTTP " + response.statusCode(), 502);
            }
        } catch (FormActionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FormActionException("Forward request to target '" + targetUri + "' was interrupted.", 502, e);
        } catch (Exception e) {
            throw new FormActionException("Failed to forward form data to target '" + targetUri + "'.", 502, e);
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
                    log.warn("[FormActionSupport] Rejected target '{}': '{}' resolves to a private/internal address ({}).",
                            uri, hostname, addr.getHostAddress());
                    throw new FormActionException(
                            "Forward target resolves to a private or internal address.", 403);
                }
            }
        } catch (TimeoutException e) {
            throw new FormActionException("Forward target hostname resolution timed out.", 502, e);
        } catch (UnknownHostException e) {
            throw new FormActionException("Forward target hostname cannot be resolved.", 400, e);
        } catch (RuntimeException e) {
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

    private static byte[] buildMultipartBody(
            Map<String, List<String>> parameters,
            List<SubmittedFile> files,
            String boundary
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MultipartMarkers markers = new MultipartMarkers(
                "--".getBytes(StandardCharsets.UTF_8),
                boundary.getBytes(StandardCharsets.UTF_8),
                "\r\n".getBytes(StandardCharsets.UTF_8)
        );

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                writePart(out, markers, name, null, null, value.getBytes(StandardCharsets.UTF_8));
            }
        }

        for (SubmittedFile file : files) {
            writePart(out, markers,
                    file.fieldName(), file.originalName(), file.mimeType(), file.data());
        }

        out.write(markers.dashdash());
        out.write(markers.boundary());
        out.write(markers.dashdash());
        out.write(markers.crlf());

        return out.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream out, MultipartMarkers markers,
                                   String name, String filename, String contentType, byte[] data) throws IOException {
        out.write(markers.dashdash());
        out.write(markers.boundary());
        out.write(markers.crlf());

        String disposition = "Content-Disposition: form-data; name=\""
                + ContentDispositionUtils.escapeFormFieldName(name) + "\"";
        if (filename != null && !filename.isEmpty()) {
            disposition += "; filename=\"" + ContentDispositionUtils.toRfc6266FilenameFallback(filename) + "\"";
            disposition += "; filename*=UTF-8''" + ContentDispositionUtils.encodeRfc5987(filename);
        }
        out.write(disposition.getBytes(StandardCharsets.UTF_8));
        out.write(markers.crlf());

        if (contentType != null) {
            out.write(("Content-Type: " + contentType).getBytes(StandardCharsets.UTF_8));
            out.write(markers.crlf());
        }

        out.write(markers.crlf());
        out.write(data);
        out.write(markers.crlf());
    }

    private record MultipartMarkers(byte[] dashdash, byte[] boundary, byte[] crlf) {
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MultipartMarkers that)) {
                return false;
            }
            return Arrays.equals(dashdash, that.dashdash)
                    && Arrays.equals(boundary, that.boundary)
                    && Arrays.equals(crlf, that.crlf);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Arrays.hashCode(dashdash),
                    Arrays.hashCode(boundary),
                    Arrays.hashCode(crlf)
            );
        }

        @Override
        public String toString() {
            return "MultipartMarkers[dashdash=" + Arrays.toString(dashdash)
                    + ", boundary=" + Arrays.toString(boundary)
                    + ", crlf=" + Arrays.toString(crlf) + "]";
        }
    }
}
