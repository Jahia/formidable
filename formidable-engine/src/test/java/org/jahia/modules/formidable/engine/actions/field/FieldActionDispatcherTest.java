package org.jahia.modules.formidable.engine.actions.field;

import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.actions.field.ResolvedFieldAction.Severity;
import org.jahia.modules.formidable.engine.actions.field.ResolvedFieldAction.Trigger;
import org.jahia.modules.formidable.engine.actions.field.ResolvedFieldAction.Unavailable;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldActionDispatcherTest {

    private static final String TYPE = "myco:crmLookupAction";
    private static final FieldActionRequest REQUEST = new FieldActionRequest("form-1", "email", "<ada>@example.com", Locale.ENGLISH);

    /** A repository answering every system-session callback with the one session. */
    private static Supplier<JCRTemplate> repository(JCRSessionWrapper session) throws Exception {
        JCRTemplate template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(session));
        return () -> template;
    }

    /** A session holding action nodes by id, each carrying the given contributor message (null: none). */
    private static JCRSessionWrapper session(Map<String, String> messagesById) throws Exception {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        for (Map.Entry<String, String> entry : messagesById.entrySet()) {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            when(node.getIdentifier()).thenReturn(entry.getKey());
            if (entry.getValue() != null) {
                JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
                when(property.getString()).thenReturn(entry.getValue());
                when(node.hasProperty(FmdbProperty.REJECTION_MESSAGE)).thenReturn(true);
                when(node.getProperty(FmdbProperty.REJECTION_MESSAGE)).thenReturn(property);
            }
            when(session.getNodeByIdentifier(entry.getKey())).thenReturn(node);
        }
        return session;
    }

    private static ResolvedFieldAction action(String id, Trigger trigger, Severity severity, Unavailable unavailable) {
        return new ResolvedFieldAction(id, TYPE, trigger, severity, unavailable);
    }

    private static ResolvedFieldAction blocking(String id) {
        return action(id, Trigger.BLUR, Severity.BLOCK, Unavailable.ACCEPT);
    }

    /** A Java action for the type, answering the given result and counting its calls. */
    private static FieldAction javaAction(Supplier<FieldActionResult> result, AtomicInteger calls) {
        return new FieldAction() {
            @Override
            public String getNodeType() {
                return TYPE;
            }

            @Override
            public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
                calls.incrementAndGet();
                return result.get();
            }
        };
    }

    private static FieldActionDispatcher dispatcher(List<FieldAction> actions, Duration ttl, JCRSessionWrapper session) throws Exception {
        return new FieldActionDispatcher(() -> actions, new VerdictCache(), () -> ttl, repository(session));
    }

    @Test
    void anAcceptLeavesNothingBehind() throws Exception {
        // Verifies the nominal pass: an accepting action neither blocks nor writes a message.
        AtomicInteger calls = new AtomicInteger();
        FieldActionDispatcher dispatcher = dispatcher(List.of(javaAction(FieldActionResult::accept, calls)),
                Duration.ofSeconds(300), session(Map.of("a1", "Refused: ${value}")));

        FieldActionDispatcher.Outcome outcome = dispatcher.run(REQUEST, List.of(blocking("a1")),
                EnumSet.allOf(Trigger.class), false);

        assertFalse(outcome.blocked());
        assertTrue(outcome.messages().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void aBlockingRefusalStopsTheRunWithTheContributorsMessageInterpolatedAndEscaped() throws Exception {
        // Verifies the refusal that matters: the first blocking action refuses, the run stops before the next one,
        // and the one message is the contributor's text in which ${value} is interpolated with HTML escaping — the
        // rich text itself trusted, the value never.
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        FieldAction refusing = javaAction(() -> FieldActionResult.reject("unknown"), first);
        FieldAction accepting = new FieldAction() {
            @Override
            public String getNodeType() {
                return "myco:other";
            }

            @Override
            public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
                second.incrementAndGet();
                return FieldActionResult.accept();
            }
        };
        FieldActionDispatcher dispatcher = dispatcher(List.of(refusing, accepting), Duration.ofSeconds(300),
                session(Map.of("a1", "<p>Unknown address <b>${value}</b></p>", "a2", "never")));
        ResolvedFieldAction other = new ResolvedFieldAction("a2", "myco:other", Trigger.BLUR, Severity.BLOCK, Unavailable.ACCEPT);

        FieldActionDispatcher.Outcome outcome = dispatcher.run(REQUEST, List.of(blocking("a1"), other),
                EnumSet.allOf(Trigger.class), false);

        assertTrue(outcome.blocked());
        assertEquals(1, outcome.messages().size());
        FieldActionMessage message = outcome.messages().get(0);
        assertEquals(FieldActionMessage.Level.ERROR, message.level());
        assertEquals("<p>Unknown address <b>&lt;ada&gt;@example.com</b></p>", message.html());
        assertEquals("email", message.field());
        assertEquals("a1", message.actionId());
        assertEquals(TYPE, message.actionType());
        assertEquals(0, second.get(), "the run stops at the first blocking refusal");
    }

    @Test
    void aWarningRefusalAddsAWarningAndGoesOn() throws Exception {
        // Verifies the warn severity: the message is a warning, the run continues to the next action, nothing blocks.
        AtomicInteger calls = new AtomicInteger();
        FieldActionDispatcher dispatcher = dispatcher(List.of(javaAction(() -> FieldActionResult.reject("risky"), calls)),
                Duration.ofSeconds(300), session(Map.of("w1", "Check ${value}", "w2", "Check again")));

        FieldActionDispatcher.Outcome outcome = dispatcher.run(REQUEST,
                List.of(action("w1", Trigger.BLUR, Severity.WARN, Unavailable.ACCEPT), action("w2", Trigger.BLUR, Severity.WARN, Unavailable.ACCEPT)),
                EnumSet.allOf(Trigger.class), false);

        assertFalse(outcome.blocked());
        assertEquals(2, outcome.messages().size());
        assertEquals(FieldActionMessage.Level.WARNING, outcome.messages().get(0).level());
        assertEquals(2, calls.get());
    }

    @Test
    void anUnavailableCheckIsWhatTheContributorSaidItIs() throws Exception {
        // Verifies the outage setting, both ways: accept lets the value through silently, reject refuses it with the
        // message — and an action that throws is an unavailable check, never the visitor's stack trace.
        AtomicInteger calls = new AtomicInteger();
        FieldActionDispatcher unavailable = dispatcher(List.of(javaAction(() -> FieldActionResult.unavailable("provider 503"), calls)), Duration.ZERO, session(Map.of("a1", "Refused")));
        FieldActionDispatcher throwing = dispatcher(List.of(javaAction(() -> {
            throw new IllegalStateException("boom");
        }, calls)), Duration.ZERO, session(Map.of("a1", "Refused")));

        assertFalse(unavailable.run(REQUEST, List.of(action("a1", Trigger.BLUR, Severity.BLOCK, Unavailable.ACCEPT)),
                EnumSet.allOf(Trigger.class), false).blocked());
        FieldActionDispatcher.Outcome refused = unavailable.run(REQUEST,
                List.of(action("a1", Trigger.BLUR, Severity.BLOCK, Unavailable.REJECT)), EnumSet.allOf(Trigger.class), false);
        assertTrue(refused.blocked());
        assertEquals("Refused", refused.messages().get(0).html());
        assertFalse(throwing.run(REQUEST, List.of(action("a1", Trigger.BLUR, Severity.BLOCK, Unavailable.ACCEPT)),
                EnumSet.allOf(Trigger.class), false).blocked());
        assertTrue(throwing.run(REQUEST, List.of(action("a1", Trigger.BLUR, Severity.BLOCK, Unavailable.REJECT)),
                EnumSet.allOf(Trigger.class), false).blocked());
    }

    @Test
    void theTriggerAndTheBlockingOnlyFilterDecideWhatRuns() throws Exception {
        // Verifies the two filters: a submit-triggered action does not run on a blur pre-check, and under the
        // pipeline's blockingOnly rule a warning action does not run at all — they warned.
        AtomicInteger calls = new AtomicInteger();
        FieldActionDispatcher dispatcher = dispatcher(List.of(javaAction(() -> FieldActionResult.reject("x"), calls)),
                Duration.ZERO, session(Map.of("s1", "m", "w1", "m")));

        FieldActionDispatcher.Outcome blur = dispatcher.run(REQUEST,
                List.of(action("s1", Trigger.SUBMIT, Severity.BLOCK, Unavailable.ACCEPT)), EnumSet.of(Trigger.BLUR), false);
        FieldActionDispatcher.Outcome pipeline = dispatcher.run(REQUEST,
                List.of(action("w1", Trigger.BLUR, Severity.WARN, Unavailable.ACCEPT)), EnumSet.allOf(Trigger.class), true);

        assertFalse(blur.blocked());
        assertFalse(pipeline.blocked());
        assertTrue(pipeline.messages().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void theCacheAnswersTheSecondRunOnTheSameValueUnlessTheTtlIsZero() throws Exception {
        // Verifies the one-provider-call promise: the pre-check's verdict serves the submission's run, and an
        // administrator's TTL of 0 switches that off — two runs, two calls.
        AtomicInteger cached = new AtomicInteger();
        FieldActionDispatcher withCache = dispatcher(List.of(javaAction(FieldActionResult::accept, cached)),
                Duration.ofSeconds(300), session(Map.of("a1", "m")));
        AtomicInteger uncached = new AtomicInteger();
        FieldActionDispatcher withoutCache = dispatcher(List.of(javaAction(FieldActionResult::accept, uncached)),
                Duration.ZERO, session(Map.of("a1", "m")));

        withCache.run(REQUEST, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), false);
        withCache.run(REQUEST, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), true);
        withoutCache.run(REQUEST, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), false);
        withoutCache.run(REQUEST, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), true);

        assertEquals(1, cached.get());
        assertEquals(2, uncached.get());
    }

    @Test
    void aTypeNoDeployedModuleImplementsIsAnUnavailableCheck() throws Exception {
        // Verifies what a node whose module is gone — or whose type declares no Java service — does to the visitor:
        // nothing by itself. The check could not run, and the contributor's setting decides, as for a provider outage.
        FieldActionDispatcher dispatcher = dispatcher(List.of(), Duration.ZERO, session(Map.of("v1", "No")));

        assertFalse(dispatcher.run(REQUEST, List.of(blocking("v1")), EnumSet.allOf(Trigger.class), false).blocked());
        assertTrue(dispatcher.run(REQUEST, List.of(action("v1", Trigger.BLUR, Severity.BLOCK, Unavailable.REJECT)),
                EnumSet.allOf(Trigger.class), false).blocked());
    }

    @Test
    void theBundlesDefaultServesWhenTheNodeCarriesNoMessage() throws Exception {
        // Verifies the fallback: a node saved without the feedback property still gives the visitor a sentence.
        FieldActionDispatcher dispatcher = dispatcher(List.of(javaAction(() -> FieldActionResult.reject("x"), new AtomicInteger())),
                Duration.ZERO, session(Collections.singletonMap("a1", null)));

        FieldActionDispatcher.Outcome outcome = dispatcher.run(REQUEST, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), false);

        assertEquals(FieldActionDispatcher.DEFAULT_MESSAGE, outcome.messages().get(0).html());
    }

    @Test
    void aMessageInterpolatesTheValueAndNothingElseSoBothEntryPointsReadTheSame() {
        // Verifies the one interpolation contract: ${value} is filled, any other name resolves to nothing — the same
        // string whether the browser asked about this one field or the pipeline judged a whole submission. Naming
        // another field would otherwise render complete at submission and full of holes at the pre-check, where the
        // browser sends one field and the others simply are not there.
        try {
            FieldActionDispatcher dispatcher = dispatcher(List.of(javaAction(() -> FieldActionResult.reject("x"), new AtomicInteger())), Duration.ZERO, session(Map.of("a1", "No: ${value} for ${firstName}")));

            FieldActionDispatcher.Outcome outcome = dispatcher.run(REQUEST, List.of(blocking("a1")),
                    EnumSet.allOf(Trigger.class), false);

            assertEquals("No: &lt;ada&gt;@example.com for ", outcome.messages().get(0).html());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void whatTheVisitorTypedNeverReachesTheOperatorsLog() throws Exception {
        // Verifies the guarantee, not the comment that states it: a failing action and an unavailable one both name
        // themselves in the log, and neither carries the value — an exception message quotes it by construction
        // (NumberFormatException: For input string: "…"), and an action's detail may too. Put either back on the INFO or
        // the WARN line and this test fails. DEBUG is off in the tests, so what is captured is exactly what an
        // operator sees at the default level.
        String secret = "ada@example.com";
        FieldActionRequest request = new FieldActionRequest("form-1", "email", secret, Locale.ENGLISH);
        FieldActionDispatcher throwing = dispatcher(List.of(javaAction(() -> {
            throw new IllegalStateException("cannot parse " + secret);
        }, new AtomicInteger())), Duration.ZERO, session(Map.of("a1", "No")));
        FieldActionDispatcher unavailable = dispatcher(List.of(javaAction(() -> FieldActionResult.unavailable("provider said " + secret), new AtomicInteger())), Duration.ZERO, session(Map.of("a1", "No")));

        PrintStream previous = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            throwing.run(request, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), false);
            unavailable.run(request, List.of(action("a1", Trigger.BLUR, Severity.BLOCK, Unavailable.REJECT)),
                    EnumSet.allOf(Trigger.class), false);
        } finally {
            System.setErr(previous);
        }

        String logged = captured.toString(StandardCharsets.UTF_8);
        assertFalse(logged.contains(secret), logged);
        assertTrue(logged.contains("IllegalStateException"), "the operator still learns what failed: " + logged);
        assertTrue(logged.contains("could not run on field 'email'"), logged);
    }

    @Test
    void theCacheKeysTheLocaleSoAnAcceptInOneLanguageDoesNotServeAnother() throws Exception {
        // Verifies the locale in the key: the same action and value asked in two locales run twice — a localized
        // check may answer differently — while the same locale asked twice runs once.
        AtomicInteger calls = new AtomicInteger();
        FieldActionDispatcher dispatcher = dispatcher(List.of(javaAction(FieldActionResult::accept, calls)),
                Duration.ofSeconds(300), session(Map.of("a1", "m")));
        FieldActionRequest inFrench = new FieldActionRequest(REQUEST.formId(), REQUEST.fieldName(), REQUEST.value(), Locale.FRENCH);

        dispatcher.run(REQUEST, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), false);
        dispatcher.run(inFrench, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), false);
        dispatcher.run(REQUEST, List.of(blocking("a1")), EnumSet.allOf(Trigger.class), true);

        assertEquals(2, calls.get());
    }
}
