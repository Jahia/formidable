package org.jahia.modules.formidable.engine.api;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shape every provider-backed check inherits: the node read, the values it declines to judge, and the failures
 * that are the check not running rather than a verdict.
 */
class ProviderFieldActionTest {

    /** A gateway that answers what the test decided, or throws, and remembers what it was asked. */
    private static final class Gateway implements FieldActionGateway {
        final List<String> asked = new ArrayList<>();
        private final IOException failure;
        private final RuntimeException refusal;

        Gateway(IOException failure, RuntimeException refusal) {
            this.failure = failure;
            this.refusal = refusal;
        }

        @Override
        public Response post(String providerId, String path, String jsonBody) throws IOException {
            asked.add(providerId + " " + path);
            if (failure != null) {
                throw failure;
            }
            if (refusal != null) {
                throw refusal;
            }
            return new Response(200, "{\"ok\":true}");
        }

        @Override
        public Response get(String providerId, String path) throws IOException {
            return post(providerId, path, null);
        }
    }

    /** The least an action writes: the call and the reading of the answer. */
    private static final class Lookup extends ProviderFieldAction {
        private final Gateway gateway;

        Lookup(Gateway gateway) {
            this.gateway = gateway;
        }

        @Override
        public String getNodeType() {
            return "test:lookup";
        }

        @Override
        protected FieldActionResult ask(String providerId, FieldActionRequest request) throws IOException {
            return json(gateway().post(providerId, "lookup", "{}"))
                    .map(body -> body.optBoolean("ok") ? FieldActionResult.accept() : FieldActionResult.reject("no"))
                    .orElse(FieldActionResult.unavailable("not json"));
        }

        @Override
        protected FieldActionGateway gateway() {
            return gateway;
        }
    }

    private static FieldActionRequest of(String value) {
        return new FieldActionRequest("form-1", "code", value, Locale.ENGLISH);
    }

    private static JCRNodeWrapper nodeNaming(String providerId) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(node.hasProperty("providerId")).thenReturn(providerId != null);
        when(node.getProperty("providerId")).thenReturn(property);
        when(property.getString()).thenReturn(providerId);
        return node;
    }

    @Test
    void theProviderIsReadOffTheNodeAndTheAnswerJudged() throws RepositoryException {
        // Verifies the nominal path end to end: the id the contributor picked reaches the gateway, the answer reaches
        // the action's own reading — and nothing else stands between a module's ask() and the visitor.
        Gateway gateway = new Gateway(null, null);

        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway).execute(nodeNaming("crm"), of("42")).verdict());

        assertEquals(List.of("crm lookup"), gateway.asked);
    }

    @Test
    void aNodeNamingNoProviderIsAnUnavailableCheckWithoutACall() throws RepositoryException {
        // Verifies the node side: no node, no property, a blank one, a node that cannot be read — the action cannot know
        // whom to ask, which is the contributor's setting to fix, and the gateway is not asked to guess.
        Gateway gateway = new Gateway(null, null);
        Lookup lookup = new Lookup(gateway);
        JCRNodeWrapper unreadable = mock(JCRNodeWrapper.class);
        when(unreadable.hasProperty("providerId")).thenThrow(new RepositoryException("gone"));

        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, lookup.execute(null, of("42")).verdict());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, lookup.execute(nodeNaming(null), of("42")).verdict());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, lookup.execute(nodeNaming("  "), of("42")).verdict());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, lookup.execute(unreadable, of("42")).verdict());
        assertNull(ProviderFieldAction.providerIdOf(unreadable));

        assertEquals(List.of(), gateway.asked);
    }

    @Test
    void aValueTheActionDoesNotJudgeIsAcceptedWithoutACall() {
        // Verifies the default of concerns(): a blank value is the required-field validation's business, and every
        // answer of a provider has a price — so nothing leaves.
        Gateway gateway = new Gateway(null, null);

        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway).judge("crm", of("")).verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway).judge("crm", of("   ")).verdict());
        assertEquals(FieldActionResult.Verdict.ACCEPT, new Lookup(gateway).judge(null, of(null)).verdict());

        assertEquals(List.of(), gateway.asked);
    }

    @Test
    void aProviderThatDoesNotAnswerOrIsNotDeclaredIsAnUnavailableCheckNeverARefusal() {
        // Verifies the outage rule at the base, once for every module: the network failing, and an id the configuration
        // no longer declares (the gateway's IllegalArgumentException) — neither is a verdict on the value.
        FieldActionResult down = new Lookup(new Gateway(new IOException("connection refused"), null)).judge("crm", of("42"));
        FieldActionResult gone = new Lookup(new Gateway(null, new IllegalArgumentException("No field action provider is configured under the id 'crm'"))).judge("crm", of("42"));

        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, down.verdict());
        assertTrue(down.detail().contains("IOException"), down.detail());
        assertEquals(FieldActionResult.Verdict.UNAVAILABLE, gone.verdict());
    }

    @Test
    void theAnswerIsReadAsOneJsonObjectOrNotAtAll() {
        // Verifies the helper every module reads its answer through: a body that is not one object — empty, a
        // maintenance page, an array — is nothing to judge, which the module turns into an unavailable check.
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, "{\"ok\":true}")).isPresent());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, "")).isEmpty());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, null)).isEmpty());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(503, "<html>maintenance</html>")).isEmpty());
        assertTrue(ProviderFieldAction.json(new FieldActionGateway.Response(200, "[1,2]")).isEmpty());
        assertTrue(ProviderFieldAction.json(null).isEmpty());
    }
}
