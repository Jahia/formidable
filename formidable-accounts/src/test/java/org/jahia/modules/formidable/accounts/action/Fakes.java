package org.jahia.modules.formidable.accounts.action;

import org.jahia.modules.formidable.accounts.account.AccountDirectory;
import org.jahia.modules.formidable.accounts.account.Authenticator;
import org.jahia.modules.formidable.accounts.activation.ActivationMailer;
import org.jahia.modules.formidable.accounts.activation.ActivationToken;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** In-memory stand-ins for the platform seams of the two actions. */
final class Fakes {

    private Fakes() {
    }

    static final class Directory implements AccountDirectory {
        static final Pattern SYNTAX = Pattern.compile("[0-9a-z_A-Z\\-.@]{2,}");
        final List<NewAccount> created = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        Pending lastPending;
        AccountException failure;
        final Set<String> backOffice = new HashSet<>();
        final Set<String> withoutPermission = new HashSet<>();

        @Override
        public boolean isValidUsername(String username) {
            return SYNTAX.matcher(username).matches();
        }

        @Override
        public boolean isBackOfficeUser(String username) {
            return backOffice.contains(username);
        }

        @Override
        public boolean holdsPermission(String username, String path, String workspace, String permission) {
            return username != null && !withoutPermission.contains(username);
        }

        @Override
        public Created create(NewAccount account, Function<String, Pending> pending) throws AccountException {
            if (failure != null) {
                throw failure;
            }
            created.add(account);
            String identifier = "11111111-2222-4333-8444-" + String.format("%012d", created.size());
            lastPending = pending == null ? null : pending.apply(identifier);
            return new Created(identifier, "/users/" + account.username());
        }

        @Override
        public void delete(String identifier) {
            deleted.add(identifier);
        }

        @Override
        public Optional<String> activate(ActivationToken token, Instant now) {
            return Optional.empty();
        }
    }

    static final class Auth implements Authenticator {
        Outcome outcome = Outcome.SIGNED_IN;
        final List<String> calls = new ArrayList<>();

        @Override
        public Outcome signIn(String username, String password, String siteKey, HttpServletRequest request, HttpServletResponse response) {
            calls.add(username + "|" + siteKey);
            return outcome;
        }
    }

    static final class Mailer implements ActivationMailer {
        boolean available = true;
        boolean failSend;
        final List<String[]> sent = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void send(String from, String to, String subject, String text, String html) throws MailUnavailableException {
            if (failSend) {
                throw new MailUnavailableException("smtp down");
            }
            sent.add(new String[]{from, to, subject, text, html});
        }
    }
}
