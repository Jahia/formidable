package org.jahia.modules.formidable.accounts.activation;

import org.jahia.modules.formidable.accounts.account.AccountDirectory;
import org.jahia.modules.formidable.accounts.messages.VisitorText;
import org.jahia.modules.formidable.accounts.security.AccountsGuard;
import org.jahia.modules.formidable.engine.api.RedirectTargets;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * The endpoint an activation link points to: {@code GET /modules/formidable-accounts/verify?t=<token>}.
 *
 * <p>It is public by nature (a visitor clicks a link in an e-mail), so it is built to give nothing away: the token is
 * {@code <user node UUID>.<256 random bits>} and only its SHA-256 is stored; every failure (malformed, unknown,
 * expired, already used, rate-limited) answers the same generic page; a success unlocks the account, removes the token
 * and sends the visitor to the page the contributor chose (a same-origin path) with {@code fmdbActivation=done}, or shows
 * a short confirmation. Every answer is {@code no-store} with {@code Referrer-Policy: no-referrer}, so the token does
 * not leak through a cache or a click on the next page.</p>
 *
 * <p>A link is followed with GET, as every e-mail link is: a mail scanner that prefetches it consumes the token, and the
 * visitor can then sign in with the password they chose. The token is single use, which is also why the activation does
 * not sign the visitor in.</p>
 *
 * <p>Threading: a singleton with no state; the CSRF guard of Jahia only watches {@code *.do}, and a GET that is meant
 * to be followed from a mailbox cannot carry a token.</p>
 */
@Component(
        service = {HttpServlet.class, Servlet.class},
        property = {"alias=/formidable-accounts/verify"},
        immediate = true
)
public class VerifyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(VerifyServlet.class);

    /** Query parameter appended to the page the visitor lands on after a successful activation. */
    static final String RESULT_PARAMETER = "fmdbActivation";

    private transient AccountDirectory directory;
    private transient AccountsGuard guard;

    @Reference
    public void setDirectory(AccountDirectory directory) {
        this.directory = directory;
    }

    @Reference
    public void setGuard(AccountsGuard guard) {
        this.guard = guard;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        noStore(resp);
        Locale locale = req.getLocale();
        if (!guard.admitVerification(req)) {
            page(resp, 429, locale, VisitorText.TOO_MANY_ATTEMPTS);
            return;
        }
        Optional<ActivationToken> token = ActivationToken.parse(req.getParameter("t"));
        Optional<String> redirect = token.isEmpty() ? Optional.empty() : directory.activate(token.get(), Instant.now());
        if (redirect.isEmpty()) {
            log.info("[VerifyServlet] An activation link was refused.");
            page(resp, 400, locale, VisitorText.VERIFY_INVALID);
            return;
        }
        Optional<String> target = RedirectTargets.sameOriginPath(redirect.get());
        if (target.isPresent()) {
            resp.setStatus(HttpServletResponse.SC_FOUND);
            resp.setHeader("Location", target.get() + (target.get().contains("?") ? "&" : "?") + RESULT_PARAMETER + "=done");
            return;
        }
        page(resp, 200, locale, VisitorText.VERIFY_DONE);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Allow", "GET");
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    private static void noStore(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store, private, max-age=0");
        resp.setHeader("Vary", "Cookie");
        resp.setHeader("Referrer-Policy", "no-referrer");
        resp.setHeader("X-Content-Type-Options", "nosniff");
    }

    private static void page(HttpServletResponse resp, int status, Locale locale, VisitorText text) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/html");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String language = locale == null ? "en" : locale.getLanguage();
        resp.getWriter().write("<!doctype html><html lang=\"" + ActivationMail.escape(language.isEmpty() ? "en" : language)
                + "\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex\"><title>" + ActivationMail.escape(text.in(locale)) + "</title></head><body><main><p>"
                + ActivationMail.escape(text.in(locale)) + "</p></main></body></html>");
    }
}
