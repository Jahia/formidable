package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionGateway.Endpoint;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionSettings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class FieldActionGatewayImplTest {

    private static Endpoint provider(String base) {
        return new Endpoint("crm", URI.create(base), "X-Api-Key", "s3cr3t", false, false);
    }

    @Test
    void aRelativePathIsAppendedUnderTheBaseWhetherOrNotTheBaseEndsWithASlash() {
        // Verifies the resolution the contract promises: the path lands under the base, and a base without a trailing
        // slash keeps its last segment — URI.resolve alone would drop it — with a query string carried through.
        assertEquals(URI.create("https://api.example.com/v1/email/validate"),
                FieldActionGatewayImpl.target(provider("https://api.example.com/v1"), "email/validate"));
        assertEquals(URI.create("https://api.example.com/v1/email/validate?strict=1"),
                FieldActionGatewayImpl.target(provider("https://api.example.com/v1/"), "email/validate?strict=1"));
        assertEquals(URI.create("https://api.example.com/ping"),
                FieldActionGatewayImpl.target(provider("https://api.example.com"), "ping"));
    }

    @Test
    void aPathThatCouldLeaveTheEndpointIsRefused() {
        // Verifies the SSRF guard, one shape at a time: an absolute URL, a scheme, a leading slash, a protocol-relative
        // URL, a climb with .., a control character — none of them turns the gateway toward another host.
        Endpoint crm = provider("https://api.example.com/v1");
        for (String path : new String[] {"https://evil.example/x", "mailto:x", "/admin", "//evil.example/x", "../admin", "a/../../x", "a\nb", "a b", null}) {
            assertThrows(IllegalArgumentException.class, () -> FieldActionGatewayImpl.target(crm, path), String.valueOf(path));
        }
    }

    @Test
    void anEndpointTheRuleRefusesIsRefusedBeforeAnyCall() {
        // Verifies the gateway checks the URL itself, whoever built the endpoint: no endpoint, plain HTTP outside
        // development, a development endpoint on another host, credentials in the URL — an IllegalArgumentException,
        // which the base class reads as unavailable, and no request leaves (the client is never touched).
        HttpClient client = mock(HttpClient.class);
        FieldActionSettings settings = new FieldActionSettings(Duration.ofSeconds(5), Duration.ofSeconds(10),
                client, Duration.ZERO, 30, 512, 20);
        FieldActionGatewayImpl gateway = new FieldActionGatewayImpl(() -> settings);

        assertThrows(IllegalArgumentException.class, () -> gateway.get(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> gateway.post(new Endpoint("plain", URI.create("http://api.example.com"), "", "", false, false), "x", "{}"));
        assertThrows(IllegalArgumentException.class, () -> gateway.get(new Endpoint("dev", URI.create("http://api.example.com"), "", "", false, true), "x"));
        assertThrows(IllegalArgumentException.class, () -> gateway.get(new Endpoint("creds", URI.create("https://u:p@api.example.com"), "", "", false, false), "x"));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void anEndpointIsBuiltFromAConfigurationOnlyWhenItDescribesAUsableOne() {
        // Verifies Endpoint.of, what a module calls with its configuration's values: the URL, the credential's name and
        // value together, a placement that is header or query, a query credential with its value, the endpoint rule —
        // and the message names what is wrong, never the credential.
        Endpoint header = Endpoint.of("Experian", " https://api.experianaperture.io ", "Auth-Token", " t0k3n ", "", false);
        assertEquals(URI.create("https://api.experianaperture.io"), header.baseUri());
        assertEquals("t0k3n", header.credential());
        assertFalse(header.credentialInQuery());
        assertTrue(Endpoint.of("ZeroBounce", "https://api.zerobounce.net", "api_key", "k", "QUERY", false).credentialInQuery());
        assertTrue(Endpoint.of("stub", "http://localhost:8080/stub", "api_key", "k", "query", true).development());

        for (String[] wrong : new String[][] {
                {"", "Auth-Token", "t0k3n", "header"}, {"https://api.example.com", "Auth-Token", "", "header"},
                {"https://api.example.com", "", "t0k3n", "header"}, {"https://api.example.com", "Auth-Token", "t0k3n", "body"},
                {"http://api.example.com", "Auth-Token", "t0k3n", "header"}, {"https://api example.com", "Auth-Token", "t0k3n", "header"}}) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> Endpoint.of("svc", wrong[0], wrong[1], wrong[2], wrong[3], false), String.join("|", wrong));
            assertFalse(refused.getMessage().contains("t0k3n"), refused.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> Endpoint.of("dev", "http://api.example.com", "", "", "", true));
    }

    @Test
    void aCredentialInTheQueryIsAppendedToTheTargetAndKeptOutOfTheHeaders() {
        // Verifies the second place a credential may go, for a provider that reads its key off the URL: appended after
        // the path's own query, before a fragment, encoded — and the header carries nothing then, which send() honours
        // through the same flag.
        Endpoint zerobounce = new Endpoint("zb", URI.create("https://api.zerobounce.net"), "api_key", "s3c r&t", true, false);

        assertEquals(URI.create("https://api.zerobounce.net/v2/validate?api_key=s3c+r%26t"),
                FieldActionGatewayImpl.target(zerobounce, "v2/validate"));
        assertEquals(URI.create("https://api.zerobounce.net/v2/validate?email=ada%40example.com&api_key=s3c+r%26t"),
                FieldActionGatewayImpl.target(zerobounce, "v2/validate?email=ada%40example.com"));
        assertEquals(URI.create("https://api.zerobounce.net/v2/validate?api_key=s3c+r%26t#top"),
                FieldActionGatewayImpl.target(zerobounce, "v2/validate#top"));
        assertEquals(URI.create("https://api.example.com/v1/email/validate"),
                FieldActionGatewayImpl.target(provider("https://api.example.com/v1"), "email/validate"));
    }

    @Test
    void theRequestThatLeavesCarriesTheCredentialWhereTheEndpointSaysAndTheBodyComesBackCut() throws Exception {
        // Verifies send(), the one place the credential is handled: a query-placed credential rides the URL and no
        // header of that name is sent, a header-placed one is a header and the URL stays bare, Accept is JSON on
        // both — and a body past the cap comes back cut, the status kept. Drop the flag in send() or the cap, and
        // this test fails; the target alone proves neither.
        HttpClient client = mock(HttpClient.class);
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> answer = mock(HttpResponse.class);
        doReturn(200).when(answer).statusCode();
        doReturn("x".repeat(FieldActionGatewayImpl.MAX_BODY_CHARS + 1)).when(answer).body();
        doReturn(answer).when(client).send(sent.capture(), any());
        Endpoint zerobounce = new Endpoint("zb", URI.create("https://api.zerobounce.net"), "api_key", "s3cr3t", true, false);
        FieldActionSettings settings = new FieldActionSettings(Duration.ofSeconds(5), Duration.ofSeconds(10), client,
                Duration.ZERO, 30, 512, 20);
        FieldActionGatewayImpl gateway = new FieldActionGatewayImpl(() -> settings);

        FieldActionGateway.Response response = gateway.get(zerobounce, "v2/validate?email=ada%40example.com");
        HttpRequest onTheUrl = sent.getValue();
        assertEquals("https://api.zerobounce.net/v2/validate?email=ada%40example.com&api_key=s3cr3t", onTheUrl.uri().toString());
        assertEquals(Optional.empty(), onTheUrl.headers().firstValue("api_key"), onTheUrl.headers().map().toString());
        assertEquals(Optional.of("application/json"), onTheUrl.headers().firstValue("Accept"));
        assertEquals(200, response.status());
        assertEquals(FieldActionGatewayImpl.MAX_BODY_CHARS, response.body().length());

        gateway.post(provider("https://api.example.com/v1"), "email/validate", "{}");
        HttpRequest inTheHeader = sent.getValue();
        assertEquals("https://api.example.com/v1/email/validate", inTheHeader.uri().toString());
        assertEquals(Optional.of("s3cr3t"), inTheHeader.headers().firstValue("X-Api-Key"));
        assertEquals(Optional.of("application/json"), inTheHeader.headers().firstValue("Content-Type"));
        assertEquals(Optional.of("application/json"), inTheHeader.headers().firstValue("Accept"));
    }

    @Test
    void anEndpointNeverPrintsItsCredential() {
        // Verifies the toString the logs may reach: the id and the base, the header's name, and a mask for the secret.
        String printed = provider("https://api.example.com/v1").toString();

        assertFalse(printed.contains("s3cr3t"), printed);
        assertTrue(printed.contains("X-Api-Key") && printed.contains("api.example.com"), printed);
        String query = new Endpoint("zb", URI.create("https://api.zerobounce.net"), "api_key", "s3cr3t", true, false).toString();
        assertFalse(query.contains("s3cr3t"), query);
        assertTrue(query.contains("credentialIn=query"), query);
    }
}
