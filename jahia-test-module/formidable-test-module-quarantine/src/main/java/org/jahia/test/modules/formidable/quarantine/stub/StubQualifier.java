package org.jahia.test.modules.formidable.quarantine.stub;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jahia.test.modules.formidable.quarantine.check.CheckSettings;
import org.jahia.test.modules.formidable.quarantine.check.Outcome;
import org.jahia.test.modules.formidable.quarantine.check.QuarantinedFile;
import org.jahia.test.modules.formidable.quarantine.check.Qualifier;
import org.jahia.test.modules.formidable.quarantine.check.Scope;
import org.jahia.test.modules.formidable.quarantine.check.Submitted;
import org.jahia.test.modules.formidable.quarantine.stub.StubCallLog.Kind;
import org.jahia.test.modules.formidable.quarantine.stub.StubCallLog.StubCall;

/**
 * A stub check: it answers what the marker of the file asks, as {@link StubVerdicts} reads it, and logs each call.
 * It applies to text files alone. The tickets of pending answers live in memory.
 */
public abstract class StubQualifier implements Qualifier {

    /** The reason code of every answer of a stub. */
    public static final String REASON = "stub.marker";

    private final String nodeType;
    private final Scope scope;
    private final StubCallLog log;
    private final OutageSwitch outage;
    private final Map<String, PendingTicket> tickets = new ConcurrentHashMap<>();

    private record PendingTicket(String checkName, String sha256, int remainingPolls) {
    }

    protected StubQualifier(String nodeType, Scope scope, StubCallLog log, OutageSwitch outage) {
        this.nodeType = Objects.requireNonNull(nodeType, "nodeType");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.log = Objects.requireNonNull(log, "log");
        this.outage = Objects.requireNonNull(outage, "outage");
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
        return StubVerdicts.appliesTo(file.mimeType());
    }

    @Override
    public Submitted submit(QuarantinedFile file, CheckSettings settings) {
        log.add(new StubCall(nodeType, settings.name(), file.sha256(), Kind.SUBMIT));
        if (!appliesTo(file, settings)) {
            return Submitted.answered(new Outcome.Failed(false, "stub.notApplicable"));
        }
        StubVerdict verdict;
        try (InputStream in = file.open()) {
            verdict = StubVerdicts.read(in);
        } catch (IOException e) {
            return Submitted.answered(new Outcome.Failed(true, "stub.unreadable"));
        }
        if (verdict instanceof StubVerdict.Pending pending && pending.polls() > 0) {
            String ticket = UUID.randomUUID().toString();
            tickets.put(ticket, new PendingTicket(settings.name(), file.sha256(), pending.polls()));
            return Submitted.ticket(ticket);
        }
        return Submitted.answered(answer(verdict));
    }

    @Override
    public Outcome poll(String ticket) {
        PendingTicket pending = tickets.get(ticket);
        if (pending == null) {
            log.add(new StubCall(nodeType, null, null, Kind.POLL));
            return new Outcome.Failed(false, "stub.unknownTicket");
        }
        log.add(new StubCall(nodeType, pending.checkName(), pending.sha256(), Kind.POLL));
        PendingTicket after = tickets.computeIfPresent(ticket, (id, current) -> current.remainingPolls() == 0
                ? null
                : new PendingTicket(current.checkName(), current.sha256(), current.remainingPolls() - 1));
        return after == null ? Outcome.pass(REASON, "PASS") : Outcome.PENDING;
    }

    private Outcome answer(StubVerdict verdict) {
        if (verdict instanceof StubVerdict.Fail) {
            return Outcome.fail(REASON, "FAIL");
        }
        if (verdict instanceof StubVerdict.Inconclusive) {
            return Outcome.inconclusive(REASON, "INCONCLUSIVE");
        }
        if (verdict instanceof StubVerdict.Unmarked) {
            return Outcome.inconclusive("stub.unmarked", null);
        }
        if (verdict instanceof StubVerdict.Down && outage.isDown()) {
            return new Outcome.Failed(true, "stub.down");
        }
        return Outcome.pass(REASON, "PASS");
    }
}
