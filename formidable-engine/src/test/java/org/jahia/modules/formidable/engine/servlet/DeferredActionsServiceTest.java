package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.DeferredActionException;
import org.jahia.modules.formidable.engine.api.DetachedRequestException;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SensitiveFields;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The run of the actions after release, with no HTTP request (ADR 0002): what each action receives, which list is
 * refused, and where a run stops and resumes.
 */
class DeferredActionsServiceTest {

    private static final String FORM_ID = "8f7e2a10-0000-4000-8000-000000000003";
    private static final String SECRET = "correct horse battery staple";

    private JCRSessionWrapper systemSession;
    private JCRNodeWrapper formNode;
    private DeferredActionsService service;
    /** The type of each action that ran, in order. */
    private List<String> ran;
    /** The indexes the service reported as completed, in order. */
    private List<Integer> completed;

    @BeforeEach
    void setUp() throws Exception {
        systemSession = mock(JCRSessionWrapper.class);
        formNode = mock(JCRNodeWrapper.class);
        when(systemSession.getNodeByIdentifier(FORM_ID)).thenReturn(formNode);
        JCRTemplate template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(systemSession));
        service = new DeferredActionsService(() -> template);
        ran = new ArrayList<>();
        completed = new ArrayList<>();
    }

    /** A plain action that records what it is handed, and fails when the test says so. */
    private final class PlainAction implements FormAction {
        final String type;
        final AtomicReference<List<SubmittedFile>> receivedFiles = new AtomicReference<>();
        final AtomicReference<HttpServletRequest> receivedRequest = new AtomicReference<>();
        final AtomicReference<Map<String, List<String>>> receivedValues = new AtomicReference<>();
        Exception failure;
        PlainAction(String type) { this.type = type; }
        @Override public String getNodeType() { return type; }
        @Override public void execute(JCRNodeWrapper n, HttpServletRequest r, JCRSessionWrapper s,
                                      Map<String, List<String>> p, List<SubmittedFile> f) throws FormActionException {
            ran.add(type);
            receivedFiles.set(f);
            receivedRequest.set(r);
            receivedValues.set(p);
            if (failure instanceof FormActionException expected) {
                throw expected;
            }
            if (failure instanceof RuntimeException unexpected) {
                throw unexpected;
            }
        }
    }

    /** A contextual action that opts in to the sensitive values or not, and records its context. */
    private final class Contextual implements ContextualFormAction {
        final String type;
        final boolean consumes;
        final AtomicReference<FormActionContext> received = new AtomicReference<>();
        Contextual(String type, boolean consumes) { this.type = type; this.consumes = consumes; }
        @Override public String getNodeType() { return type; }
        @Override public SensitiveFields sensitiveFieldsConsumed(JCRNodeWrapper actionNode) {
            return consumes ? SensitiveFields.all() : SensitiveFields.none();
        }
        @Override public FormActionResult execute(FormActionContext context) {
            ran.add(type);
            received.set(context);
            return new FormActionResult("/sites/mysite/thanks.html", null, null);
        }
    }

    /** Declares the nodes of the {@code deferredActions} list of the form, each of a type that is deferrable or not. */
    private void deferredList(Map<String, Boolean> deferrableByType) throws Exception {
        List<JCRNodeWrapper> nodes = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Boolean> entry : deferrableByType.entrySet()) {
            JCRNodeWrapper node = mock(JCRNodeWrapper.class);
            String id = "deferred-" + index++;
            when(node.getIdentifier()).thenReturn(id);
            when(node.getPath()).thenReturn("/form/deferredActions/" + id);
            when(node.getPrimaryNodeTypeName()).thenReturn(entry.getKey());
            when(node.isNodeType("fmdbmix:deferrableAction")).thenReturn(entry.getValue());
            when(systemSession.getNodeByIdentifier(id)).thenReturn(node);
            nodes.add(node);
        }
        JCRNodeWrapper list = mock(JCRNodeWrapper.class);
        Iterator<JCRNodeWrapper> backing = nodes.iterator();
        JCRNodeIteratorWrapper iterator = mock(JCRNodeIteratorWrapper.class);
        when(iterator.hasNext()).thenAnswer(call -> backing.hasNext());
        when(iterator.nextNode()).thenAnswer(call -> backing.next());
        when(list.getNodes()).thenReturn(iterator);
        when(formNode.hasNode("deferredActions")).thenReturn(true);
        when(formNode.getNode("deferredActions")).thenReturn(list);
    }

    private static Map<String, Boolean> deferrable(String... types) {
        Map<String, Boolean> byType = new java.util.LinkedHashMap<>();
        for (String type : types) {
            byType.put(type, true);
        }
        return byType;
    }

    private static HeldSubmission released() {
        return new HeldSubmission(FORM_ID, "mysite", Locale.FRENCH, Map.of("email", List.of("ada@example.com")),
                Map.of("password", List.of(SECRET)),
                List.of(new SubmittedFile("cv", "cv.pdf", "application/pdf", new byte[] {1})),
                new SubmissionOrigin("fr", "https://example.com/claim", "Europe/Paris"));
    }

    private void bind(FormAction... actions) {
        for (FormAction action : actions) {
            service.bindFormAction(action);
        }
    }

    @Test
    void eachActionOfTheListRunsInOrderWithTheReleasedFiles() throws Exception {
        // Verifies the release: every action of the second list runs once, in the order of the list, with the files
        // the hold released and the public values, and the service reports each one as it completes.
        PlainAction store = new PlainAction("t:store");
        PlainAction mail = new PlainAction("t:mail");
        bind(mail, store);
        deferredList(deferrable("t:store", "t:mail"));

        service.run(released(), 0, completed::add);

        assertEquals(List.of("t:store", "t:mail"), ran);
        assertEquals(List.of(0, 1), completed);
        assertEquals(List.of("cv.pdf"), store.receivedFiles.get().stream().map(SubmittedFile::originalName).toList());
        assertEquals(Map.of("email", List.of("ada@example.com")), mail.receivedValues.get());
    }

    @Test
    void anActionReceivesTheStandInOfTheRequest() throws Exception {
        // Verifies an action after release reads the three values kept of the request, and that anything else fails by
        // the named exception.
        PlainAction store = new PlainAction("t:store");
        bind(store);
        deferredList(deferrable("t:store"));

        service.run(released(), 0, completed::add);

        HttpServletRequest standIn = store.receivedRequest.get();
        assertEquals("fr", standIn.getParameter("lang"));
        assertEquals("https://example.com/claim", standIn.getHeader("Referer"));
        assertThrows(DetachedRequestException.class, () -> standIn.getHeader("Cookie"));
    }

    @Test
    void theSensitiveSplitAppliesAfterRelease() throws Exception {
        // Verifies the sensitive values reach the contextual action that opts in, and no other action: the plain
        // action and the contextual one that keeps the default read the public values alone.
        PlainAction store = new PlainAction("t:store");
        Contextual consumer = new Contextual("t:consumer", true);
        Contextual passive = new Contextual("t:passive", false);
        bind(store, consumer, passive);
        deferredList(deferrable("t:store", "t:consumer", "t:passive"));

        service.run(released(), 0, completed::add);

        assertFalse(store.receivedValues.get().containsKey("password"));
        assertEquals(SECRET, consumer.received.get().sensitiveParameter("password"));
        assertTrue(passive.received.get().sensitiveParameters().isEmpty());
        assertNull(consumer.received.get().session());
        assertThrows(DetachedRequestException.class, () -> consumer.received.get().response().setHeader("X-Sample", "1"));
    }

    @Test
    void aTypeThatIsNotDeferrableRefusesTheListBeforeAnyActionRuns() throws Exception {
        // Verifies a list that holds a type without the marker runs nothing, the deferrable action placed before it
        // included: such a node was written around the repository, which refuses to create it there.
        PlainAction store = new PlainAction("t:store");
        PlainAction redirect = new PlainAction("t:redirect");
        bind(store, redirect);
        Map<String, Boolean> types = deferrable("t:store");
        types.put("t:redirect", false);
        deferredList(types);

        DeferredActionException error = assertThrows(DeferredActionException.class,
                () -> service.run(released(), 0, completed::add));

        assertEquals(DeferredActionException.NO_ACTION, error.failedAction());
        assertTrue(error.getMessage().contains("t:redirect"), error.getMessage());
        assertEquals(List.of(), ran);
        assertEquals(List.of(), completed);
    }

    @Test
    void anActionThatFailsStopsTheRunAndReportsItsIndex() throws Exception {
        // Verifies a failure stops the run where it happens: the action before it is reported as completed, the
        // action after it does not run, and the exception names the index to resume from.
        PlainAction store = new PlainAction("t:store");
        PlainAction mail = new PlainAction("t:mail");
        mail.failure = FormActionException.serverError("the mail server refused the mail");
        PlainAction forward = new PlainAction("t:forward");
        bind(store, mail, forward);
        deferredList(deferrable("t:store", "t:mail", "t:forward"));

        DeferredActionException error = assertThrows(DeferredActionException.class,
                () -> service.run(released(), 0, completed::add));

        assertEquals(1, error.failedAction());
        assertEquals(3, error.total());
        assertEquals(List.of("t:store", "t:mail"), ran);
        assertEquals(List.of(0), completed);
    }

    @Test
    void aRunResumesAtTheActionThatFailedAndRepeatsNoAction() throws Exception {
        // Verifies the resume: from the index of the failure, the actions before it do not run again.
        PlainAction store = new PlainAction("t:store");
        PlainAction mail = new PlainAction("t:mail");
        PlainAction forward = new PlainAction("t:forward");
        bind(store, mail, forward);
        deferredList(deferrable("t:store", "t:mail", "t:forward"));

        service.run(released(), 1, completed::add);

        assertEquals(List.of("t:mail", "t:forward"), ran);
        assertEquals(List.of(1, 2), completed);
    }

    @Test
    void anActionThatThrowsAnUnexpectedExceptionReportsItsIndexToo() throws Exception {
        // Verifies a runtime exception of an action is a failure of that action after release, with its index: an
        // action that asks the request for what it does not have fails this way.
        PlainAction store = new PlainAction("t:store");
        PlainAction broken = new PlainAction("t:broken");
        broken.failure = new DetachedRequestException("no HTTP request");
        bind(store, broken);
        deferredList(deferrable("t:store", "t:broken"));

        DeferredActionException error = assertThrows(DeferredActionException.class,
                () -> service.run(released(), 0, completed::add));

        assertEquals(1, error.failedAction());
        assertEquals(List.of(0), completed);
    }

    @Test
    void anActionWithNoHandlerStopsTheRunAtItsIndex() throws Exception {
        // Verifies an action whose module is stopped fails as an action does: the run stops there, to be resumed.
        PlainAction store = new PlainAction("t:store");
        bind(store);
        deferredList(deferrable("t:store", "t:stopped"));

        DeferredActionException error = assertThrows(DeferredActionException.class,
                () -> service.run(released(), 0, completed::add));

        assertEquals(1, error.failedAction());
        assertEquals(List.of(0), completed);
    }

    @Test
    void anIndexOutsideTheListIsRefused() throws Exception {
        // Verifies a resume from an index the list does not have runs nothing.
        bind(new PlainAction("t:store"));
        deferredList(deferrable("t:store"));

        DeferredActionException error = assertThrows(DeferredActionException.class,
                () -> service.run(released(), 2, completed::add));

        assertEquals(DeferredActionException.NO_ACTION, error.failedAction());
        assertEquals(List.of(), ran);
    }

    @Test
    void aFailureOfAnActionThatHeldSensitiveValuesCarriesNoValue() throws Exception {
        // Verifies the text of a failure is removed when the action held a sensitive value, as it is in a request.
        ContextualFormAction leaking = new ContextualFormAction() {
            @Override public String getNodeType() { return "t:leaking"; }
            @Override public SensitiveFields sensitiveFieldsConsumed(JCRNodeWrapper actionNode) { return SensitiveFields.all(); }
            @Override public FormActionResult execute(FormActionContext context) {
                throw new IllegalStateException("bad value " + context.sensitiveParameter("password"));
            }
        };
        bind(leaking);
        deferredList(deferrable("t:leaking"));

        DeferredActionException error = assertThrows(DeferredActionException.class,
                () -> service.run(released(), 0, completed::add));

        assertEquals(0, error.failedAction());
        assertFalse(error.getMessage().contains(SECRET), error.getMessage());
        assertFalse(error.getCause().getMessage().contains(SECRET), error.getCause().getMessage());
    }
}
