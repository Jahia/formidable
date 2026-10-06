package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FieldActionGateway;
import org.jahia.modules.formidable.engine.api.FieldActionGateway.Endpoint;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionSettings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class FieldActionGatewayImplTest {

    private static final String SWITCH_OFF_REASON = "Experian is a development endpoint, and they are switched off";

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
        FieldActionSettings settings = new FieldActionSettings(false, Duration.ofSeconds(5), Duration.ofSeconds(10),
                client, Duration.ZERO, 30, 512, 20);
        FieldActionGatewayImpl gateway = new FieldActionGatewayImpl(() -> settings);

        Endpoint plain = new Endpoint("plain", URI.create("http://api.example.com"), "", "", false, false);
        Endpoint devElsewhere = new Endpoint("dev", URI.create("http://api.example.com"), "", "", false, true);
        Endpoint withCredentials = new Endpoint("creds", URI.create("https://u:p@api.example.com"), "", "", false, false);
        Endpoint devLocal = new Endpoint("stub", URI.create("http://localhost:8080/stub"), "", "", false, true);

        assertThrows(IllegalArgumentException.class, () -> gateway.get(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> gateway.post(plain, "x", "{}"));
        assertThrows(IllegalArgumentException.class, () -> gateway.get(devElsewhere, "x"));
        assertThrows(IllegalArgumentException.class, () -> gateway.get(withCredentials, "x"));
        // A double on this machine, its own file saying development=true: refused while the administrator's switch is off.
        assertThrows(IllegalArgumentException.class, () -> gateway.get(devLocal, "x"));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void aDevelopmentEndpointIsCalledOnlyWhileTheAdministratorsSwitchIsOn() throws Exception {
        // Verifies the gate the forward targets have too: the action's own development=true is not enough, the
        // engine's enableDevFieldActionEndpoints must be on for the call to leave.
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> answer = mock(HttpResponse.class);
        doReturn(200).when(answer).statusCode();
        doReturn("{}").when(answer).body();
        doReturn(answer).when(client).send(any(), any());
        FieldActionSettings on = new FieldActionSettings(true, Duration.ofSeconds(5), Duration.ofSeconds(10), client,
                Duration.ZERO, 30, 512, 20);
        Endpoint stub = new Endpoint("stub", URI.create("http://localhost:8080/stub"), "", "", false, true);

        assertEquals(200, new FieldActionGatewayImpl(() -> on).get(stub, "x").status());
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
        // A key put in the URL would be dropped by the gateway on every call: refused where it is configured.
        IllegalArgumentException query = assertThrows(IllegalArgumentException.class,
                () -> Endpoint.of("svc", "https://api.example.com/v1?key=t0k3n", "", "", "", false));
        assertTrue(query.getMessage().contains("query"), query.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Endpoint.of("svc", "https://api.example.com/v1#top", "", "", "", false));
        // A malformed URL is not quoted, nor its parse error kept: a key pasted into it would reach the log.
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> Endpoint.of("svc", "https://api example.com/?key=s3cr3t", "", "", "", false));
        assertFalse(malformed.getMessage().contains("s3cr3t"), malformed.getMessage());
        assertEquals(null, malformed.getCause());
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
        FieldActionSettings settings = new FieldActionSettings(false, Duration.ofSeconds(5), Duration.ofSeconds(10), client,
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

    @Test
    void anEndpointRefusalIsLoggedOnceWithItsReason() {
        // Verifies the administrator learns why a check does not run: the gateway's own refusal — the switch off, here
        // — is logged at WARN with its reason, once, however many values are checked; the base class logs only the
        // exception's type.
        FieldActionSettings off = switchedOff();
        FieldActionGatewayImpl gateway = new FieldActionGatewayImpl(() -> off);
        Endpoint stub = developmentStub();

        String logged = stderrWhile(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThrows(IllegalArgumentException.class, () -> gateway.get(stub, "x"));
            }
        });

        assertEquals(1, occurrences(logged, SWITCH_OFF_REASON), logged);
    }

    @Test
    void anEndpointRefusalIsLoggedAgainOnceTheConfigurationChanged() {
        // Verifies a refusal said once is said again under new settings: an administrator who turns the switch off
        // again, long after the first refusal, reads why the check does not run — the same reason, a new configuration.
        AtomicReference<FieldActionSettings> settings = new AtomicReference<>(switchedOff());
        FieldActionGatewayImpl gateway = new FieldActionGatewayImpl(settings::get);
        Endpoint stub = developmentStub();

        String logged = stderrWhile(() -> {
            assertThrows(IllegalArgumentException.class, () -> gateway.get(stub, "x"));
            assertThrows(IllegalArgumentException.class, () -> gateway.get(stub, "x"));
            settings.set(switchedOff());
            assertThrows(IllegalArgumentException.class, () -> gateway.get(stub, "x"));
        });

        assertEquals(2, occurrences(logged, SWITCH_OFF_REASON), logged);
    }

    /** The settings with the development endpoints switched off — a fresh snapshot each time, as a configuration change gives. */
    private static FieldActionSettings switchedOff() {
        return new FieldActionSettings(false, Duration.ofSeconds(5), Duration.ofSeconds(10), mock(HttpClient.class),
                Duration.ZERO, 30, 512, 20);
    }

    private static Endpoint developmentStub() {
        return new Endpoint("Experian", URI.create("http://localhost:8080/stub"), "", "", false, true);
    }

    /** What the test logging wrote on System.err while the action ran. */
    private static String stderrWhile(Runnable action) {
        PrintStream previous = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(previous);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }
}
