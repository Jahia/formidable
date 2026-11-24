package org.jahia.modules.formidable.quarantine.chain;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.jahia.modules.formidable.quarantine.api.CheckSettings;
import org.jahia.modules.formidable.quarantine.api.OnInconclusive;
import org.jahia.modules.formidable.quarantine.api.OnUnavailable;
import org.jahia.modules.formidable.quarantine.api.Outcome;
import org.jahia.modules.formidable.quarantine.api.QuarantinedFile;
import org.jahia.modules.formidable.quarantine.api.Qualifier;
import org.jahia.modules.formidable.quarantine.api.Scope;
import org.jahia.modules.formidable.quarantine.api.Submitted;

/** Files and qualifiers for the tests of the chain. No qualifier here is ever called to submit or poll. */
final class ChainFixtures {

    static final String TEXT = "text/plain";
    static final String IMAGE = "image/png";

    private ChainFixtures() {
    }

    record TestFile(String sha256, String mimeType) implements QuarantinedFile {
        @Override
        public long size() {
            return 1;
        }

        @Override
        public String fileName() {
            return sha256 + ".bin";
        }

        @Override
        public InputStream open() {
            return new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** A qualifier that applies to the files of one MIME type, or to every file when the type is null. */
    record TestQualifier(String getNodeType, Scope scope, String appliesToMimeType) implements Qualifier {
        @Override
        public boolean appliesTo(QuarantinedFile file, CheckSettings settings) {
            return appliesToMimeType == null || appliesToMimeType.equals(file.mimeType());
        }

        @Override
        public Submitted submit(QuarantinedFile file, CheckSettings settings) {
            throw new AssertionError("The chain never calls a check");
        }

        @Override
        public Outcome poll(String ticket) {
            throw new AssertionError("The chain never calls a check");
        }
    }

    static TestFile text(String sha256) {
        return new TestFile(sha256, TEXT);
    }

    static TestFile image(String sha256) {
        return new TestFile(sha256, IMAGE);
    }

    static ChainCheck remote(String name) {
        return check(name, Scope.REMOTE, TEXT, OnInconclusive.REVIEW, OnUnavailable.RETRY);
    }

    static ChainCheck inProcess(String name) {
        return check(name, Scope.IN_PROCESS, TEXT, OnInconclusive.REVIEW, OnUnavailable.RETRY);
    }

    static ChainCheck check(String name, Scope scope, String appliesTo, OnInconclusive onInconclusive,
                            OnUnavailable onUnavailable) {
        String nodeType = "test:" + scope.name().toLowerCase() + "Check";
        return new ChainCheck(new CheckSettings(nodeType, name, onInconclusive, onUnavailable),
                new TestQualifier(nodeType, scope, appliesTo));
    }

    static RecordedOutcome passed() {
        return new RecordedOutcome(Outcome.pass("ok", null), null, 0);
    }

    static RecordedOutcome failed() {
        return new RecordedOutcome(Outcome.fail("bad", null), null, 0);
    }

    static RecordedOutcome inconclusive() {
        return new RecordedOutcome(Outcome.inconclusive("unsure", null), null, 0);
    }

    static RecordedOutcome pending(String ticket) {
        return new RecordedOutcome(Outcome.PENDING, ticket, 0);
    }

    static RecordedOutcome down(int attempts, String ticket) {
        return new RecordedOutcome(new Outcome.Failed(true, "down"), ticket, attempts);
    }

    static RecordedOutcome broken() {
        return new RecordedOutcome(new Outcome.Failed(false, "broken"), null, 1);
    }

    static OutcomeKey key(QuarantinedFile file, ChainCheck check) {
        return new OutcomeKey(file.sha256(), check.name());
    }
}
