package org.jahia.modules.formidable.quarantine.run;

import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A test double of a check: it answers the first marker line of a text file, as the stub checks of the quarantine test
 * module do. {@code VERDICT:PASS}, {@code VERDICT:FAIL}, {@code INCONCLUSIVE}, {@code PENDING:<n>} (pending for
 * {@code n} polls, then {@code PASS}) or {@code DOWN} (a retryable failure while the outage is on, then {@code PASS}).
 * A file with no marker is inconclusive. It applies to {@code text/plain} files alone, and logs each call.
 */
final class MarkerQualifier implements Qualifier {

    static final String REMOTE_TYPE = "test:remoteCheck";
    static final String IN_PROCESS_TYPE = "test:inProcessCheck";
    static final String REASON = "stub.marker";

    private static final Pattern PENDING = Pattern.compile("PENDING:(\\d{1,4})");
    private static final Pattern LINES = Pattern.compile("\\R");

    /** What the check was asked. */
    enum Kind {
        SUBMIT,
        POLL
    }

    /** One call: the check node and the SHA-256 of the file, both null for a poll of an unknown ticket. */
    record Call(String nodeType, String checkName, String sha256, Kind kind) {
    }

    /** The calls of every double, oldest first. */
    static final class Calls {
        private final List<Call> received = new ArrayList<>();

        synchronized void add(Call call) {
            received.add(call);
        }

        synchronized List<Call> calls() {
            return List.copyOf(received);
        }

        synchronized List<Call> callsOf(String checkName) {
            return received.stream().filter(call -> checkName.equals(call.checkName())).toList();
        }
    }

    /** The outage of the services behind the doubles: on until lifted. */
    static final class Outage {
        private final AtomicBoolean down = new AtomicBoolean(true);

        boolean isDown() {
            return down.get();
        }

        void lift() {
            down.set(false);
        }
    }

    private record PendingTicket(String checkName, String sha256, int remainingPolls) {
    }

    private final String nodeType;
    private final Scope scope;
    private final Calls calls;
    private final Outage outage;
    private final Map<String, PendingTicket> tickets = new ConcurrentHashMap<>();

    private MarkerQualifier(String nodeType, Scope scope, Calls calls, Outage outage) {
        this.nodeType = nodeType;
        this.scope = scope;
        this.calls = calls;
        this.outage = outage;
    }

    static MarkerQualifier remote(Calls calls, Outage outage) {
        return new MarkerQualifier(REMOTE_TYPE, Scope.REMOTE, calls, outage);
    }

    static MarkerQualifier inProcess(Calls calls, Outage outage) {
        return new MarkerQualifier(IN_PROCESS_TYPE, Scope.IN_PROCESS, calls, outage);
    }

    @Override
    public String getNodeType() {
        return nodeType;
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public boolean appliesTo(QuarantinedFile file, CheckSettings settings) {
        String type = file.mimeType() == null ? "" : file.mimeType().split(";", 2)[0].trim();
        return "text/plain".equals(type.toLowerCase(Locale.ROOT));
    }

    @Override
    public Submitted submit(QuarantinedFile file, CheckSettings settings) {
        calls.add(new Call(nodeType, settings.name(), file.sha256(), Kind.SUBMIT));
        if (!appliesTo(file, settings)) {
            return Submitted.answered(new Outcome.Failed(false, "stub.notApplicable"));
        }
        String marker;
        try (InputStream in = file.open()) {
            marker = marker(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Submitted.answered(new Outcome.Failed(true, "stub.unreadable"));
        }
        Matcher pending = PENDING.matcher(marker);
        if (pending.matches() && Integer.parseInt(pending.group(1)) > 0) {
            String ticket = UUID.randomUUID().toString();
            tickets.put(ticket, new PendingTicket(settings.name(), file.sha256(), Integer.parseInt(pending.group(1))));
            return Submitted.ticket(ticket);
        }
        return Submitted.answered(answer(marker));
    }

    @Override
    public Outcome poll(String ticket) {
        PendingTicket pending = tickets.get(ticket);
        if (pending == null) {
            calls.add(new Call(nodeType, null, null, Kind.POLL));
            return new Outcome.Failed(false, "stub.unknownTicket");
        }
        calls.add(new Call(nodeType, pending.checkName(), pending.sha256(), Kind.POLL));
        PendingTicket after = tickets.computeIfPresent(ticket, (id, current) -> current.remainingPolls() == 0
                ? null
                : new PendingTicket(current.checkName(), current.sha256(), current.remainingPolls() - 1));
        return after == null ? Outcome.pass(REASON, "PASS") : Outcome.PENDING;
    }

    /** The first line that is a marker, trimmed, or the empty string. */
    private static String marker(String text) {
        for (String line : LINES.split(text)) {
            String trimmed = line.trim();
            if (trimmed.equals("VERDICT:PASS") || trimmed.equals("VERDICT:FAIL") || trimmed.equals("INCONCLUSIVE")
                    || trimmed.equals("DOWN") || PENDING.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return "";
    }

    private Outcome answer(String marker) {
        switch (marker) {
            case "VERDICT:FAIL":
                return Outcome.fail(REASON, "FAIL");
            case "INCONCLUSIVE":
                return Outcome.inconclusive(REASON, "INCONCLUSIVE");
            case "":
                return Outcome.inconclusive("stub.unmarked", null);
            case "DOWN":
                return outage.isDown() ? new Outcome.Failed(true, "stub.down") : Outcome.pass(REASON, "PASS");
            default:
                return Outcome.pass(REASON, "PASS");
        }
    }
}
