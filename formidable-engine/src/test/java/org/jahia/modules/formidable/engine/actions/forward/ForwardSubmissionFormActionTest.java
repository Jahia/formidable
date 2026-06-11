package org.jahia.modules.formidable.engine.actions.forward;

import org.jahia.modules.formidable.engine.api.FormActionException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForwardSubmissionFormActionTest {

    @Test
    void checkNotPrivateAddressRejectsPrivateAddress() throws Exception {
        // Verifies that HTTPS forward targets resolving to private IP space are rejected.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        when(resolver.resolveAll("api.example.com"))
                .thenReturn(new InetAddress[]{InetAddress.getByAddress("api.example.com", new byte[]{10, 0, 0, 5})});

        ForwardSubmissionFormAction action = new ForwardSubmissionFormAction();
        action.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> action.checkNotPrivateAddress(URI.create("https://api.example.com/forms"), false));

        // Expected outcome: the action fails closed with HTTP 403.
        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void checkNotPrivateAddressRejectsResolutionTimeout() throws Exception {
        // Verifies timeout hardening when DNS resolution cannot complete in time.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        when(resolver.resolveAll("api.example.com")).thenThrow(new TimeoutException("timed out"));

        ForwardSubmissionFormAction action = new ForwardSubmissionFormAction();
        action.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> action.checkNotPrivateAddress(URI.create("https://api.example.com/forms"), false));

        // Expected outcome: the action surfaces the resolution failure as HTTP 502.
        assertEquals(502, exception.getHttpStatus());
    }

    @Test
    void checkNotPrivateAddressAllowsExplicitDevelopmentLocalhostWithoutDnsLookup() {
        // Verifies the localhost development bypass for explicitly allowed dev targets.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);

        ForwardSubmissionFormAction action = new ForwardSubmissionFormAction();
        action.setHostnameResolutionService(resolver);

        // Expected outcome: localhost is accepted and DNS resolution is never invoked.
        assertDoesNotThrow(() -> action.checkNotPrivateAddress(URI.create("http://localhost:8081/ingest"), true));
        verifyNoInteractions(resolver);
    }
}
