package org.jahia.modules.formidable.accounts.activation;

import org.jahia.modules.formidable.accounts.TestSupport;
import org.jahia.modules.formidable.accounts.account.AccountDirectory;
import org.jahia.modules.formidable.accounts.messages.VisitorText;
import org.jahia.modules.formidable.accounts.security.AccountsGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerifyServletTest {

    private static final ActivationToken TOKEN = ActivationToken.generate(UUID.randomUUID().toString());

    private AccountDirectory directory;
    private VerifyServlet servlet;
    private HttpServletResponse response;
    private StringWriter body;

    @BeforeEach
    void setUp() throws Exception {
        directory = mock(AccountDirectory.class);
        AccountsGuard guard = new AccountsGuard();
        guard.setConfig(TestSupport.config(true, 5, 5));
        guard.setDirectory(TestSupport.directory(java.util.Set.of()));
        servlet = new VerifyServlet();
        servlet.setDirectory(directory);
        servlet.setGuard(guard);
        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    private HttpServletRequest request(String token, Locale locale) {
        HttpServletRequest request = TestSupport.request("203.0.113.7", null);
        when(request.getParameter("t")).thenReturn(token);
        when(request.getLocale()).thenReturn(locale);
        return request;
    }

    @Test
    void aValidLinkActivatesTheAccountAndSendsTheVisitorToTheChosenPage() throws Exception {
        // Verifies the success with a chosen page: 302 to the stored path with the result parameter, the secret never echoed.
        when(directory.activate(any(ActivationToken.class), any(Instant.class))).thenReturn(Optional.of("/sites/mysite/active.html"));

        servlet.doGet(request(TOKEN.asParameter(), Locale.ENGLISH), response);

        verify(response).setStatus(HttpServletResponse.SC_FOUND);
        verify(response).setHeader("Location", "/sites/mysite/active.html?fmdbActivation=done");
        ArgumentCaptor<ActivationToken> used = ArgumentCaptor.forClass(ActivationToken.class);
        verify(directory).activate(used.capture(), any(Instant.class));
        assertEquals(TOKEN, used.getValue());
    }

    @Test
    void aPageWithAQueryGetsTheParameterAppended() throws Exception {
        // Verifies the Location is well formed whether the page already has a query.
        when(directory.activate(any(ActivationToken.class), any(Instant.class))).thenReturn(Optional.of("/p.html?lang=fr"));

        servlet.doGet(request(TOKEN.asParameter(), Locale.ENGLISH), response);

        verify(response).setHeader("Location", "/p.html?lang=fr&fmdbActivation=done");
    }

    @Test
    void aValidLinkWithoutAChosenPageShowsAShortConfirmationInTheVisitorsLanguage() throws Exception {
        // Verifies the fallback page: 200, localised, escaped, no redirect.
        when(directory.activate(any(ActivationToken.class), any(Instant.class))).thenReturn(Optional.of(""));

        servlet.doGet(request(TOKEN.asParameter(), Locale.FRENCH), response);

        verify(response).setStatus(200);
        verify(response, never()).setHeader(org.mockito.ArgumentMatchers.eq("Location"), org.mockito.ArgumentMatchers.anyString());
        assertTrue(body.toString().contains(VisitorText.VERIFY_DONE.in(Locale.FRENCH).replace("'", "&#39;")), body.toString());
        assertTrue(body.toString().contains("lang=\"fr\""));
    }

    @Test
    void aStoredPathThatIsNotSameOriginIsNotFollowed() throws Exception {
        // Verifies defence in depth: even a poisoned record cannot redirect off the site; the confirmation page shows.
        when(directory.activate(any(ActivationToken.class), any(Instant.class))).thenReturn(Optional.of("https://evil.example/"));

        servlet.doGet(request(TOKEN.asParameter(), Locale.ENGLISH), response);

        verify(response, never()).setHeader(org.mockito.ArgumentMatchers.eq("Location"), org.mockito.ArgumentMatchers.anyString());
        verify(response).setStatus(200);
    }

    @Test
    void everyFailureIsOneGenericPage() throws Exception {
        // Verifies the no-oracle rule: unknown, expired or used (the directory says empty) and a malformed link all
        // answer the same 400 page; a malformed link never reaches the directory.
        when(directory.activate(any(ActivationToken.class), any(Instant.class))).thenReturn(Optional.empty());

        servlet.doGet(request(TOKEN.asParameter(), Locale.ENGLISH), response);
        String unknown = body.toString();
        body.getBuffer().setLength(0);
        servlet.doGet(request("garbage", Locale.ENGLISH), response);

        assertEquals(unknown, body.toString());
        assertTrue(unknown.contains(VisitorText.VERIFY_INVALID.english()));
        verify(response, org.mockito.Mockito.times(2)).setStatus(400);
        verify(directory, org.mockito.Mockito.times(1)).activate(any(ActivationToken.class), any(Instant.class));
    }

    @Test
    void everyAnswerIsNotCachedAndDoesNotLeakTheTokenThroughTheReferrer() throws Exception {
        // Verifies the headers on every path: no-store, Vary on cookie, no referrer.
        when(directory.activate(any(ActivationToken.class), any(Instant.class))).thenReturn(Optional.empty());

        servlet.doGet(request(TOKEN.asParameter(), Locale.ENGLISH), response);

        verify(response).setHeader("Cache-Control", "no-store, private, max-age=0");
        verify(response).setHeader("Vary", "Cookie");
        verify(response).setHeader("Referrer-Policy", "no-referrer");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }

    @Test
    void anAddressOverItsLimitIsAnsweredWith429AndTheDirectoryIsNotAsked() throws Exception {
        // Verifies the brake: the sixth hit of one address inside the window.
        when(directory.activate(any(ActivationToken.class), any(Instant.class))).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            servlet.doGet(request(TOKEN.asParameter(), Locale.ENGLISH), response);
        }

        servlet.doGet(request(TOKEN.asParameter(), Locale.ENGLISH), response);

        verify(response).setStatus(429);
        verify(directory, org.mockito.Mockito.times(5)).activate(any(ActivationToken.class), any(Instant.class));
        assertFalse(body.toString().isBlank());
    }

    @Test
    void onlyGetIsAllowed() throws Exception {
        // Verifies the endpoint changes state on the link only.
        HttpServletRequest request = request(TOKEN.asParameter(), Locale.ENGLISH);

        servlet.doPost(request, response);

        verify(response).setHeader("Allow", "GET");
        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(directory, never()).activate(any(ActivationToken.class), any(Instant.class));
    }
}
