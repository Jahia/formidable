package org.jahia.test.modules.formidable.quarantine.stub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The calls the stub checks received, in order, for the specs to read. It holds names and hashes, never a byte of
 * a file. It keeps the last {@value #CAPACITY} calls.
 */
public final class StubCallLog {

    /** The count of calls the log keeps. */
    public static final int CAPACITY = 10_000;

    /** What the stub was asked. */
    public enum Kind {
        SUBMIT,
        POLL
    }

    /**
     * One call.
     *
     * @param nodeType  the node type of the stub that received the call
     * @param checkName the name of the check node, or null for a poll of an unknown ticket
     * @param sha256    the SHA-256 of the file, or null for a poll of an unknown ticket
     * @param kind      submit or poll
     */
    public record StubCall(String nodeType, String checkName, String sha256, Kind kind) {
    }

    private final Deque<StubCall> calls = new ArrayDeque<>();

    /** Records a call; past {@value #CAPACITY} calls, the oldest one goes. */
    public synchronized void record(StubCall call) {
        if (calls.size() == CAPACITY) {
            calls.removeFirst();
        }
        calls.addLast(call);
    }

    /** @return the calls, oldest first */
    public synchronized List<StubCall> calls() {
        return List.copyOf(calls);
    }

    /** @return the calls received for the check node of that name, oldest first */
    public synchronized List<StubCall> callsOf(String checkName) {
        List<StubCall> matching = new ArrayList<>();
        for (StubCall call : calls) {
            if (checkName.equals(call.checkName())) {
                matching.add(call);
            }
        }
        return matching;
    }

    /** Forgets every call. */
    public synchronized void clear() {
        calls.clear();
    }
}
