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
        // Given a public HTTPS target whose hostname resolves to a private address,
        // the forward action must reject it with a 403 response.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        when(resolver.resolveAll("api.example.com"))
                .thenReturn(new InetAddress[]{InetAddress.getByAddress("api.example.com", new byte[]{10, 0, 0, 5})});

        ForwardSubmissionFormAction action = new ForwardSubmissionFormAction();
        action.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> action.checkNotPrivateAddress(URI.create("https://api.example.com/forms"), false));

        assertEquals(403, exception.getHttpStatus());
    }

    @Test
    void checkNotPrivateAddressRejectsResolutionTimeout() throws Exception {
        // Given a target whose DNS resolution times out,
        // the forward action must fail with a 502 response.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);
        when(resolver.resolveAll("api.example.com")).thenThrow(new TimeoutException("timed out"));

        ForwardSubmissionFormAction action = new ForwardSubmissionFormAction();
        action.setHostnameResolutionService(resolver);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> action.checkNotPrivateAddress(URI.create("https://api.example.com/forms"), false));

        assertEquals(502, exception.getHttpStatus());
    }

    @Test
    void checkNotPrivateAddressAllowsExplicitDevelopmentLocalhostWithoutDnsLookup() {
        // Given an explicit localhost development endpoint,
        // the forward action must allow it and skip DNS resolution entirely.
        HostnameResolutionService resolver = mock(HostnameResolutionService.class);

        ForwardSubmissionFormAction action = new ForwardSubmissionFormAction();
        action.setHostnameResolutionService(resolver);

        assertDoesNotThrow(() -> action.checkNotPrivateAddress(URI.create("http://localhost:8081/ingest"), true));
        verifyNoInteractions(resolver);
    }
}
