package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionProvider;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionSettings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class FieldActionGatewayImplTest {

    private static FieldActionProvider provider(String base) {
        return new FieldActionProvider("crm", "CRM", URI.create(base), "X-Api-Key", "s3cr3t");
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
    void aPathThatCouldLeaveTheProviderIsRefused() {
        // Verifies the SSRF guard, one shape at a time: an absolute URL, a scheme, a leading slash, a protocol-relative
        // URL, a climb with .., a control character — none of them turns the gateway toward another host.
        FieldActionProvider crm = provider("https://api.example.com/v1");
        for (String path : new String[] {"https://evil.example/x", "mailto:x", "/admin", "//evil.example/x", "../admin", "a/../../x", "a\nb", "a b", null}) {
            assertThrows(IllegalArgumentException.class, () -> FieldActionGatewayImpl.target(crm, path), String.valueOf(path));
        }
    }

    @Test
    void anUnknownProviderIsRefusedBeforeAnyCall() {
        // Verifies the id lookup: a node naming a provider the administrator never declared gets an
        // IllegalArgumentException, which the dispatcher reads as unavailable — no request leaves.
        FieldActionSettings settings = new FieldActionSettings(Map.of(), Duration.ofSeconds(5), Duration.ofSeconds(10),
                HttpClient.newHttpClient(), Duration.ZERO, 30, 512, 20);
        FieldActionGatewayImpl gateway = new FieldActionGatewayImpl(() -> settings);

        assertThrows(IllegalArgumentException.class, () -> gateway.post("nobody", "x", "{}"));
        assertThrows(IllegalArgumentException.class, () -> gateway.get(null, "x"));
    }

    @Test
    void aCredentialInTheQueryIsAppendedToTheTargetAndKeptOutOfTheHeaders() {
        // Verifies the second place a credential may go, for a provider that reads its key off the URL: appended after
        // the path's own query, before a fragment, encoded — and the header carries nothing then, which send() honours
        // through the same flag.
        FieldActionProvider zerobounce = new FieldActionProvider("zb", "ZeroBounce", URI.create("https://api.zerobounce.net"), "api_key", "s3c r&t", true);

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
    void theRequestThatLeavesCarriesTheCredentialWhereTheLineSaysAndTheBodyComesBackCut() throws Exception {
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
        FieldActionProvider zerobounce = new FieldActionProvider("zb", "ZeroBounce", URI.create("https://api.zerobounce.net"), "api_key", "s3cr3t", true);
        FieldActionSettings settings = new FieldActionSettings(Map.of("zb", zerobounce, "crm", provider("https://api.example.com/v1")),
                Duration.ofSeconds(5), Duration.ofSeconds(10), client, Duration.ZERO, 30, 512, 20);
        FieldActionGatewayImpl gateway = new FieldActionGatewayImpl(() -> settings);

        FieldActionGateway.Response response = gateway.get("zb", "v2/validate?email=ada%40example.com");
        HttpRequest onTheUrl = sent.getValue();
        assertEquals("https://api.zerobounce.net/v2/validate?email=ada%40example.com&api_key=s3cr3t", onTheUrl.uri().toString());
        assertEquals(Optional.empty(), onTheUrl.headers().firstValue("api_key"), onTheUrl.headers().map().toString());
        assertEquals(Optional.of("application/json"), onTheUrl.headers().firstValue("Accept"));
        assertEquals(200, response.status());
        assertEquals(FieldActionGatewayImpl.MAX_BODY_CHARS, response.body().length());

        gateway.post("crm", "email/validate", "{}");
        HttpRequest inTheHeader = sent.getValue();
        assertEquals("https://api.example.com/v1/email/validate", inTheHeader.uri().toString());
        assertEquals(Optional.of("s3cr3t"), inTheHeader.headers().firstValue("X-Api-Key"));
        assertEquals(Optional.of("application/json"), inTheHeader.headers().firstValue("Content-Type"));
        assertEquals(Optional.of("application/json"), inTheHeader.headers().firstValue("Accept"));
    }

    @Test
    void aProviderNeverPrintsItsCredential() {
        // Verifies the toString the logs may reach: the id and the base, the header's name, and a mask for the secret.
        String printed = provider("https://api.example.com/v1").toString();

        assertFalse(printed.contains("s3cr3t"), printed);
        assertTrue(printed.contains("X-Api-Key") && printed.contains("api.example.com"), printed);
        String query = new FieldActionProvider("zb", "ZeroBounce", URI.create("https://api.zerobounce.net"), "api_key", "s3cr3t", true).toString();
        assertFalse(query.contains("s3cr3t"), query);
        assertTrue(query.contains("credentialIn=query"), query);
    }
}
