package org.jahia.test.modules.formidable.quarantine.stub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jahia.test.modules.formidable.quarantine.check.CheckSettings;
import org.jahia.test.modules.formidable.quarantine.check.Outcome;
import org.jahia.test.modules.formidable.quarantine.check.QuarantinedFile;
import org.jahia.test.modules.formidable.quarantine.check.Scope;
import org.jahia.test.modules.formidable.quarantine.check.Submitted;
import org.jahia.test.modules.formidable.quarantine.stub.StubCallLog.Kind;
import org.jahia.test.modules.formidable.quarantine.stub.StubCallLog.StubCall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubQualifierTest {

    private static final String REMOTE_TYPE = "fmdbquar:remoteStubCheck";

    private final StubCallLog log = new StubCallLog();
    private final OutageSwitch outage = new OutageSwitch();
    private final StubQualifier remote = new RemoteStubQualifier(log, outage);
    private final CheckSettings settings = CheckSettings.withDefaults(REMOTE_TYPE, "scan");

    record TextFile(String sha256, String mimeType, String text) implements QuarantinedFile {
        @Override
        public long size() {
            return text.length();
        }

        @Override
        public String fileName() {
            return "claim.txt";
        }

        @Override
        public InputStream open() throws IOException {
            if (text == null) {
                throw new IOException("gone");
            }
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static TextFile text(String content) {
        return new TextFile("abc", "text/plain", content);
    }

    private Outcome answerAtOnce(String content) {
        Submitted submitted = remote.submit(text(content), settings);
        return assertInstanceOf(Submitted.Answered.class, submitted).outcome();
    }

    @Test
    void theTwoStubsServeTheirTypesAndScopes() {
        assertEquals(REMOTE_TYPE, remote.getNodeType());
        assertEquals(Scope.REMOTE, remote.scope());
        StubQualifier inProcess = new InProcessStubQualifier(log, outage);
        assertEquals("fmdbquar:inProcessStubCheck", inProcess.getNodeType());
        assertEquals(Scope.IN_PROCESS, inProcess.scope());
    }

    @Test
    void theStubAppliesToTextFilesAlone() {
        assertTrue(remote.appliesTo(text("VERDICT:PASS"), settings));
        assertFalse(remote.appliesTo(new TextFile("abc", "image/png", "VERDICT:PASS"), settings));
    }

    @Test
    void eachVerdictMarkerGivesItsResult() {
        assertEquals(Outcome.Verdict.PASS, verdictOf(answerAtOnce("VERDICT:PASS")));
        assertEquals(Outcome.Verdict.FAIL, verdictOf(answerAtOnce("VERDICT:FAIL")));
        assertEquals(Outcome.Verdict.INCONCLUSIVE, verdictOf(answerAtOnce("INCONCLUSIVE")));
    }

    @Test
    void anUnmarkedFileIsInconclusive() {
        assertEquals(Outcome.inconclusive("stub.unmarked", null), answerAtOnce("hello"));
    }

    @Test
    void pendingAnswersPendingForItsPollsThenPass() {
        Submitted submitted = remote.submit(text("PENDING:2"), settings);
        String ticket = assertInstanceOf(Submitted.Ticket.class, submitted).id();
        assertEquals(Outcome.PENDING, remote.poll(ticket));
        assertEquals(Outcome.PENDING, remote.poll(ticket));
        assertEquals(Outcome.Verdict.PASS, verdictOf(remote.poll(ticket)));
        assertEquals(new Outcome.Failed(false, "stub.unknownTicket"), remote.poll(ticket));
    }

    @Test
    void pendingForNoPollPassesAtOnce() {
        assertEquals(Outcome.Verdict.PASS, verdictOf(answerAtOnce("PENDING:0")));
    }

    @Test
    void downFailsWhileTheOutageIsOnThenPasses() {
        assertTrue(outage.isDown());
        assertEquals(new Outcome.Failed(true, "stub.down"), answerAtOnce("DOWN"));
        assertEquals(new Outcome.Failed(true, "stub.down"), answerAtOnce("DOWN"));
        outage.lift();
        assertEquals(Outcome.Verdict.PASS, verdictOf(answerAtOnce("DOWN")));
        outage.set();
        assertEquals(new Outcome.Failed(true, "stub.down"), answerAtOnce("DOWN"));
    }

    @Test
    void anUnreadableFileIsARetryableFailure() {
        assertEquals(new Outcome.Failed(true, "stub.unreadable"), answerAtOnce(null));
    }

    @Test
    void aFileTheStubDoesNotApplyToIsNeverPassed() {
        Submitted submitted = remote.submit(new TextFile("abc", "image/png", "VERDICT:PASS"), settings);
        assertEquals(new Outcome.Failed(false, "stub.notApplicable"),
                assertInstanceOf(Submitted.Answered.class, submitted).outcome());
    }

    @Test
    void eachCallIsLoggedWithTheNameOfItsCheckNode() {
        Submitted submitted = remote.submit(text("PENDING:1"), settings);
        remote.poll(((Submitted.Ticket) submitted).id());
        remote.submit(text("VERDICT:PASS"), CheckSettings.withDefaults(REMOTE_TYPE, "other"));
        assertEquals(List.of(new StubCall(REMOTE_TYPE, "scan", "abc", Kind.SUBMIT),
                        new StubCall(REMOTE_TYPE, "scan", "abc", Kind.POLL)),
                log.callsOf("scan"));
        assertEquals(3, log.calls().size());
        log.clear();
        assertTrue(log.calls().isEmpty());
    }

    @Test
    void theLogKeepsTheLastCallsOnly() {
        for (int i = 0; i <= StubCallLog.CAPACITY; i++) {
            log.record(new StubCall(REMOTE_TYPE, "n" + i, "abc", Kind.SUBMIT));
        }
        assertEquals(StubCallLog.CAPACITY, log.calls().size());
        assertEquals("n1", log.calls().get(0).checkName());
    }

    private static Outcome.Verdict verdictOf(Outcome outcome) {
        return assertInstanceOf(Outcome.Result.class, outcome).verdict();
    }
}
