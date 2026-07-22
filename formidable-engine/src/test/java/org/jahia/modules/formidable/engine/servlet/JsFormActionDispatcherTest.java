package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.javascript.modules.engine.sdk.JSServerExtensionInvoker;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsFormActionDispatcherTest {

    private static final String NODE_TYPE = "fmdb:emailNotificationAction";

    /** Wires a mocked invoker whose registry contains the given entries. */
    private static JsFormActionDispatcher dispatcherWithEntries(List<Map<String, Object>> entries,
                                                                JSServerExtensionInvoker.Invoker callable) {
        JSServerExtensionInvoker invoker = mock(JSServerExtensionInvoker.class);
        when(invoker.forEach(eq(JsFormActionDispatcher.REGISTRY_TYPE), any())).thenAnswer(invocation -> {
            JSServerExtensionInvoker.ExtensionHandler<Object> handler = invocation.getArgument(1);
            List<Object> results = new ArrayList<>();
            for (Map<String, Object> entry : entries) {
                Object result = handler.handle(entry, callable);
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
        });
        JsFormActionDispatcher dispatcher = new JsFormActionDispatcher();
        dispatcher.setInvoker(invoker);
        return dispatcher;
    }

    private static boolean tryExecute(JsFormActionDispatcher dispatcher) throws FormActionException {
        return dispatcher.tryExecute(NODE_TYPE, mock(JCRNodeWrapper.class), mock(HttpServletRequest.class),
                mock(JCRSessionWrapper.class), Map.of(), List.of());
    }

    @Test
    void returnsFalseWhenNoEntryMatchesTheNodeType() throws Exception {
        JsFormActionDispatcher dispatcher = dispatcherWithEntries(
                List.of(Map.of("nodeType", "fmdb:otherAction", "execute", "fn")),
                (callable, args) -> Map.of("ok", true));

        assertFalse(tryExecute(dispatcher));
    }

    @Test
    void succeedsWhenTheMatchingHandlerReturnsOk() throws Exception {
        JsFormActionDispatcher dispatcher = dispatcherWithEntries(
                List.of(Map.of("nodeType", NODE_TYPE, "execute", "fn")),
                (callable, args) -> Map.of("ok", true));

        assertTrue(tryExecute(dispatcher));
    }

    @Test
    void usesTheFirstEntryWhenSeveralMatch() throws Exception {
        // Two handlers for the same node type: only the first result is decoded, so the
        // failing second entry must not affect the outcome.
        List<Map<String, Object>> entries = List.of(
                Map.of("nodeType", NODE_TYPE, "execute", "first"),
                Map.of("nodeType", NODE_TYPE, "execute", "second"));
        JsFormActionDispatcher dispatcher = dispatcherWithEntries(entries,
                (callable, args) -> "first".equals(callable)
                        ? Map.of("ok", true)
                        : Map.of("ok", false, "status", 500L, "message", "second handler"));

        assertTrue(tryExecute(dispatcher));
    }

    @Test
    void mapsAFailureResultToFormActionExceptionWithItsStatus() {
        JsFormActionDispatcher dispatcher = dispatcherWithEntries(
                List.of(Map.of("nodeType", NODE_TYPE, "execute", "fn")),
                // The SDK invoker converts JS numbers to Long.
                (callable, args) -> Map.of("ok", false, "status", 403L, "message", "not allowed"));

        FormActionException exception = assertThrows(FormActionException.class,
                () -> tryExecute(dispatcher));

        assertEquals(403, exception.getHttpStatus());
        assertEquals("not allowed", exception.getMessage());
    }

    @Test
    void failsClosedWith500OnMalformedResults() {
        JsFormActionDispatcher dispatcher = dispatcherWithEntries(
                List.of(Map.of("nodeType", NODE_TYPE, "execute", "fn")),
                (callable, args) -> "not a map");

        FormActionException exception = assertThrows(FormActionException.class,
                () -> tryExecute(dispatcher));

        assertEquals(500, exception.getHttpStatus());
    }

    @Test
    void failsClosedWith500WhenStatusOrMessageAreMissing() {
        JsFormActionDispatcher dispatcher = dispatcherWithEntries(
                List.of(Map.of("nodeType", NODE_TYPE, "execute", "fn")),
                (callable, args) -> Map.of("ok", false));

        FormActionException exception = assertThrows(FormActionException.class,
                () -> tryExecute(dispatcher));

        assertEquals(500, exception.getHttpStatus());
    }

    @Test
    void failsClosedWith500WhenTheInvokerThrows() {
        JSServerExtensionInvoker invoker = mock(JSServerExtensionInvoker.class);
        when(invoker.forEach(eq(JsFormActionDispatcher.REGISTRY_TYPE), any()))
                .thenThrow(new RuntimeException("GraalVM context error"));
        JsFormActionDispatcher dispatcher = new JsFormActionDispatcher();
        dispatcher.setInvoker(invoker);

        FormActionException exception = assertThrows(FormActionException.class,
                () -> tryExecute(dispatcher));

        assertEquals(500, exception.getHttpStatus());
    }
}
