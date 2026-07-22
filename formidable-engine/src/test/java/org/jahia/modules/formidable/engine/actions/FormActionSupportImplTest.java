package org.jahia.modules.formidable.engine.actions;

import org.jahia.modules.formidable.engine.actions.forward.HostnameResolutionService;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.junit.jupiter.api.Test;

import javax.activation.DataHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FormActionSupportImplTest {

    @Test
    void checkNotPrivateAddressRejectsPrivateAddress() throws Exception {
        // Verifies that HTTPS forward targets resolving to private IP space are rejected.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        when(resolver.resolveAll("api.example.com"))
                .thenReturn(new InetAddress[]{InetAddress.getByAddress("api.example.com", new byte[]{10, 0, 0, 5})});

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> support.checkNotPrivateAddress(URI.create("https://api.example.com/forms"), false));

        // Expected outcome: the action fails closed with HTTP 403.
        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void checkNotPrivateAddressRejectsResolutionTimeout() throws Exception {
        // Verifies timeout hardening when DNS resolution cannot complete in time.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        when(resolver.resolveAll("api.example.com")).thenThrow(new TimeoutException("timed out"));

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> support.checkNotPrivateAddress(URI.create("https://api.example.com/forms"), false));

        // Expected outcome: the action surfaces the resolution failure as HTTP 502.
        assertEquals(502, exception.getHttpStatus());
    }

    @Test
    void checkNotPrivateAddressAllowsExplicitDevelopmentLocalhostWithoutDnsLookup() {
        // Verifies the localhost development bypass for explicitly allowed dev targets.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setHostnameResolutionService(resolver);

        // Expected outcome: localhost is accepted and DNS resolution is never invoked.
        assertDoesNotThrow(() -> support.checkNotPrivateAddress(URI.create("http://localhost:8081/ingest"), true));
        verifyNoInteractions(resolver);
    }

    @Test
    void forwardSubmissionFailsWith403WhenTargetIsNotConfigured() {
        FormidableConfigService configService = mock(FormidableConfigService.class);
        when(configService.resolveForwardTarget("unknown")).thenReturn(Optional.empty());

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setConfigService(configService);
        support.setHostnameResolutionService(mock(HostnameResolutionService.class));

        FormActionException exception = assertThrows(FormActionException.class,
                () -> support.forwardSubmission("unknown", Map.of(), List.of()));

        // Expected outcome: unknown target ids fail closed with HTTP 403.
        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void forwardSubmissionFailsWith502WhenUpstreamTimesOut() throws Exception {
        // Scenario 8.3: the forward target accepts the connection but does not respond
        // within the configured request timeout. The call must surface a bounded failure.
        FormidableConfigService configService = mock(FormidableConfigService.class);
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        HttpClient httpClient = mock(HttpClient.class);

        URI targetUri = URI.create("https://api.example.com/forms/intake");
        when(configService.resolveForwardTarget("crm"))
                .thenReturn(Optional.of(new FormidableConfigService.ForwardTarget("crm", "CRM", targetUri, false)));
        when(configService.getForwardHttpRequestTimeout()).thenReturn(Duration.ofSeconds(5));
        when(configService.getForwardHttpClient()).thenReturn(httpClient);
        when(resolver.resolveAll("api.example.com"))
                .thenReturn(new InetAddress[]{InetAddress.getByAddress("api.example.com", new byte[]{(byte) 203, 0, 113, 10})});
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setConfigService(configService);
        support.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> support.forwardSubmission("crm", Map.of("fullName", List.of("Alice")), List.of()));

        // Expected outcome: timeout surfaces as HTTP 502.
        assertEquals(502, exception.getHttpStatus());
        assertInstanceOf(HttpTimeoutException.class, exception.getCause());
    }

    @Test
    void forwardSubmissionFailsWith502WhenUpstreamIsUnreachable() throws Exception {
        // Scenario 8.4: the forward target host/port is unreachable.
        // The connection failure must surface as a bounded 502 error.
        FormidableConfigService configService = mock(FormidableConfigService.class);
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        HttpClient httpClient = mock(HttpClient.class);

        URI targetUri = URI.create("https://api.example.com/forms/intake");
        when(configService.resolveForwardTarget("crm"))
                .thenReturn(Optional.of(new FormidableConfigService.ForwardTarget("crm", "CRM", targetUri, false)));
        when(configService.getForwardHttpRequestTimeout()).thenReturn(Duration.ofSeconds(5));
        when(configService.getForwardHttpClient()).thenReturn(httpClient);
        when(resolver.resolveAll("api.example.com"))
                .thenReturn(new InetAddress[]{InetAddress.getByAddress("api.example.com", new byte[]{(byte) 203, 0, 113, 10})});
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setConfigService(configService);
        support.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> support.forwardSubmission("crm", Map.of("fullName", List.of("Alice")), List.of()));

        // Expected outcome: connection failure surfaces as HTTP 502.
        assertEquals(502, exception.getHttpStatus());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void forwardSubmissionFailsWith502WhenUpstreamReturnsNonSuccessStatus() throws Exception {
        // Verifies that non-2xx responses from the forward target are surfaced as 502.
        FormidableConfigService configService = mock(FormidableConfigService.class);
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Void> httpResponse = mock(HttpResponse.class);

        URI targetUri = URI.create("https://api.example.com/forms/intake");
        when(configService.resolveForwardTarget("crm"))
                .thenReturn(Optional.of(new FormidableConfigService.ForwardTarget("crm", "CRM", targetUri, false)));
        when(configService.getForwardHttpRequestTimeout()).thenReturn(Duration.ofSeconds(5));
        when(configService.getForwardHttpClient()).thenReturn(httpClient);
        when(resolver.resolveAll("api.example.com"))
                .thenReturn(new InetAddress[]{InetAddress.getByAddress("api.example.com", new byte[]{(byte) 203, 0, 113, 10})});
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setConfigService(configService);
        support.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> support.forwardSubmission("crm", Map.of("fullName", List.of("Alice")), List.of()));

        // Expected outcome: non-2xx upstream response surfaces as HTTP 502.
        assertEquals(502, exception.getHttpStatus());
    }

    @Test
    void buildEmailAttachmentsSkipsFilesOverTheCapAndKeepsOrder() {
        FormidableConfigService configService = mock(FormidableConfigService.class);
        when(configService.getUploadMaxFileSizeBytes()).thenReturn(1024L * 1024L);

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setConfigService(configService);

        SubmittedFile small = new SubmittedFile("cv", "cv.pdf", "application/pdf",
                "small".getBytes(StandardCharsets.UTF_8));
        SubmittedFile large = new SubmittedFile("photo", "photo.png", "image/png", new byte[64]);

        // Cap of 16 bytes: 'small' (5 bytes) is kept, 'photo' (64 bytes) is skipped.
        Map<String, DataHandler> attachments = support.buildEmailAttachments(List.of(small, large), 16L);

        assertEquals(1, attachments.size());
        assertTrue(attachments.containsKey("cv.pdf"));
        assertFalse(attachments.containsKey("photo.png"));
    }

    @Test
    void buildEmailAttachmentsClampsToGlobalUploadCap() {
        FormidableConfigService configService = mock(FormidableConfigService.class);
        // Global cap (8 bytes) is smaller than the requested cap (1 MB) and must win.
        when(configService.getUploadMaxFileSizeBytes()).thenReturn(8L);

        FormActionSupportImpl support = new FormActionSupportImpl();
        support.setConfigService(configService);

        SubmittedFile file = new SubmittedFile("cv", "cv.pdf", "application/pdf", new byte[32]);

        Map<String, DataHandler> attachments = support.buildEmailAttachments(List.of(file), 1024L * 1024L);

        assertTrue(attachments.isEmpty());
    }
}
