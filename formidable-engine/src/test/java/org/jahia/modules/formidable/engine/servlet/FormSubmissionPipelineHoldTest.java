package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.DetachedRequestException;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SensitiveFields;
import org.jahia.modules.formidable.engine.api.SubmissionHold;
import org.jahia.modules.formidable.engine.api.SubmissionHoldException;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.engine.config.captcha.CaptchaConfigService;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The hold of the uploads at the dispatch of step 12 (ADR 0002): which actions of a form receive its files, with the
 * quarantine switch on and off, with and without a hold service, and under the operator setting
 * {@code uploadsRequireQuarantine}.
 */
class FormSubmissionPipelineHoldTest {

    private static final String FORM_ID = "8f7e2a10-0000-4000-8000-000000000002";
    private static final String SECRET = "correct horse battery staple";

    private UploadsConfigService uploads;
    private JCRSessionWrapper userSession;
    private JCRNodeWrapper userFormNode;
    private HttpServletRequest request;
    private JCRTemplate template;
    /** The node that every identifier resolves to in the system session, the form included. */
    private JCRNodeWrapper systemNode;
    /** What ran, in order: the type of each action, and {@code hold} for the hold service. */
    private List<String> ran;

    @BeforeEach
    void setUp() throws Exception {
        JCRSessionWrapper systemSession = mock(JCRSessionWrapper.class);
        systemNode = mock(JCRNodeWrapper.class);
        when(systemSession.getNodeByIdentifier(any())).thenReturn(systemNode);
        template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(systemSession));
        userSession = mock(JCRSessionWrapper.class);
        userFormNode = mock(JCRNodeWrapper.class);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("mysite");
        when(userFormNode.getResolveSite()).thenReturn(site);
        request = mock(HttpServletRequest.class);
        when(request.getParameter("lang")).thenReturn("fr");
        when(request.getHeader("Referer")).thenReturn("https://example.com/claim");
        when(request.getHeader("X-Formidable-Time-Zone")).thenReturn("Europe/Paris");
        uploads = mock(UploadsConfigService.class);
        ran = new ArrayList<>();
    }

    /** A plain action, as the e-mail and the forward actions are: it records the files and the request it is handed. */
    private final class PlainAction implements FormAction {
        final String type;
        final AtomicReference<List<SubmittedFile>> receivedFiles = new AtomicReference<>();
        final AtomicReference<HttpServletRequest> receivedRequest = new AtomicReference<>();
        final AtomicReference<JCRSessionWrapper> receivedSession = new AtomicReference<>();
        FormActionException failure;
        PlainAction(String type) { this.type = type; }
        @Override public String getNodeType() { return type; }
        @Override public void execute(JCRNodeWrapper n, HttpServletRequest r, JCRSessionWrapper s,
                                      Map<String, List<String>> p, List<SubmittedFile> f) throws FormActionException {
            ran.add(type);
            receivedFiles.set(f);
            receivedRequest.set(r);
            receivedSession.set(s);
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** A contextual action that opts in to the sensitive values; it records the files it is handed. */
    private final class Contextual implements ContextualFormAction {
        final String type;
        final AtomicReference<List<SubmittedFile>> receivedFiles = new AtomicReference<>();
        Contextual(String type) { this.type = type; }
        @Override public String getNodeType() { return type; }
        @Override public SensitiveFields sensitiveFieldsConsumed(JCRNodeWrapper actionNode) { return SensitiveFields.all(); }
        @Override public FormActionResult execute(FormActionContext context) {
            ran.add(type);
            receivedFiles.set(context.files());
            return FormActionResult.none();
        }
    }

    /** A hold that records the submission it is handed. */
    private final class RecordingHold implements SubmissionHold {
        final AtomicReference<HeldSubmission> held = new AtomicReference<>();
        @Override public void hold(HeldSubmission submission) {
            ran.add("hold");
            held.set(submission);
        }
    }

    private static FormDataParser.FormFile file(String fieldName, String name) {
        return new FormDataParser.FormFile(fieldName, name, "application/pdf", new byte[] {1});
    }

    private static List<String> names(List<SubmittedFile> files) {
        return files.stream().map(SubmittedFile::originalName).toList();
    }

    private static List<String> names(AtomicReference<List<SubmittedFile>> received) {
        return names(received.get());
    }

    private static List<FormSubmissionPipeline.ResolvedAction> resolved(String list, List<String> types) {
        List<FormSubmissionPipeline.ResolvedAction> resolved = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            // a type named "…write" is presumed to write to the repository, as Save to JCR is
            resolved.add(new FormSubmissionPipeline.ResolvedAction(list + "-" + (i + 1), "/form/" + list + "/a" + i,
                    types.get(i), !types.get(i).endsWith("write")));
        }
        return resolved;
    }

    /** A pipeline at step 12, on a form whose switch is on or off, with its two lists and its parsed files. */
    private FormSubmissionPipeline pipeline(boolean switchOn, List<FormAction> handlers, List<String> actionTypes,
                                            List<String> deferredTypes, List<FormDataParser.FormFile> files) throws Exception {
        return pipeline(switchOn, handlers, actionTypes, deferredTypes, files, false);
    }

    private FormSubmissionPipeline pipeline(boolean switchOn, List<FormAction> handlers, List<String> actionTypes,
                                            List<String> deferredTypes, List<FormDataParser.FormFile> files,
                                            boolean readOnly) throws Exception {
        when(userFormNode.isNodeType("fmdbmix:quarantine")).thenReturn(switchOn);
        Set<String> sensitive = Set.of("password");
        Map<String, FormDataParser.FieldInfo> infos = new LinkedHashMap<>();
        infos.put("password", new FormDataParser.FieldInfo("fmdbext:password", false, false, false, false, false, false,
                false, Set.of(), Set.of(), null));
        FormFieldMetadataCollector.Result metadata =
                new FormFieldMetadataCollector.Result(infos, Map.of(), Map.of(), Map.of(), Map.of(), sensitive);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                new FormSubmissionPipeline.Themes(uploads, mock(CaptchaConfigService.class), mock(FieldActionsConfigService.class)),
                handlers,
                (formId, locale) -> metadata,
                () -> template,
                (req, config, md) -> null,
                locale -> userSession,
                () -> readOnly);
        pipeline.useResponse(mock(HttpServletResponse.class));
        set(pipeline, "formId", FORM_ID);
        set(pipeline, "locale", Locale.FRENCH);
        set(pipeline, "session", userSession);
        set(pipeline, "formNode", userFormNode);
        set(pipeline, "fieldMetadata", metadata);
        set(pipeline, "parsed", new FormDataParser.ParseResult(
                Map.of("email", List.of("ada@example.com"), "password", List.of(SECRET)), files));
        set(pipeline, "resolvedActions", resolved("actions", actionTypes));
        set(pipeline, "resolvedDeferredActions", resolved("deferredActions", deferredTypes));
        return pipeline;
    }

    @Test
    void aFormWithTheSwitchOffHandsItsUploadsToEveryAction() throws Exception {
        // Verifies a form with the switch off keeps its behaviour: a plain action and a contextual one both receive
        // the file, and the hold service that is installed is not called.
        PlainAction mail = new PlainAction("t:mail");
        Contextual observer = new Contextual("t:observer");
        RecordingHold hold = new RecordingHold();
        FormSubmissionPipeline pipeline = pipeline(false, List.of(mail, observer), List.of("t:mail", "t:observer"),
                List.of(), List.of(file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(hold);

        dispatch(pipeline);

        assertEquals(List.of("cv.pdf"), names(mail.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(observer.receivedFiles));
        assertNull(hold.held.get());
    }

    @Test
    void theSwitchOnHandsTheUploadsToNoActionOfTheRequestAndToTheHold() throws Exception {
        // Verifies the hold: with the switch on and a file, the plain action and the contextual one receive no file,
        // whatever their code, and the hold service receives the file once they ran.
        PlainAction mail = new PlainAction("t:mail");
        Contextual observer = new Contextual("t:observer");
        RecordingHold hold = new RecordingHold();
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail, observer), List.of("t:mail", "t:observer"),
                List.of(), List.of(file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(hold);

        dispatch(pipeline);

        assertEquals(List.of(), names(mail.receivedFiles));
        assertEquals(List.of(), names(observer.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(hold.held.get().files()));
        assertEquals(List.of("t:mail", "t:observer", "hold"), ran);
    }

    @Test
    void theHoldReceivesWhatTheActionsAfterReleaseNeed() throws Exception {
        // Verifies the held submission carries the form, the site, the locale, the public and the sensitive values
        // apart, and the three values kept of the request.
        RecordingHold hold = new RecordingHold();
        FormSubmissionPipeline pipeline = pipeline(true, List.of(), List.of(), List.of(), List.of(file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(hold);

        dispatch(pipeline);

        HeldSubmission held = hold.held.get();
        assertEquals(FORM_ID, held.formId());
        assertEquals("mysite", held.siteKey());
        assertEquals(Locale.FRENCH, held.locale());
        assertEquals(Map.of("email", List.of("ada@example.com")), held.parameters());
        assertEquals(Map.of("password", List.of(SECRET)), held.sensitiveParameters());
        assertEquals(new SubmissionOrigin("fr", "https://example.com/claim", "Europe/Paris"), held.origin());
        assertFalse(held.toString().contains(SECRET), held.toString());
    }

    @Test
    void aHeldSubmissionRunsNoActionAfterReleaseInTheRequest() throws Exception {
        // Verifies the second list waits for the release: with a file, the pipeline runs none of its actions.
        PlainAction mail = new PlainAction("t:mail");
        PlainAction store = new PlainAction("t:store");
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail, store), List.of("t:mail"), List.of("t:store"),
                List.of(file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(new RecordingHold());

        dispatch(pipeline);

        assertNull(store.receivedFiles.get());
        assertEquals(List.of("t:mail", "hold"), ran);
    }

    @Test
    void aSecondListThatCannotBeReadRefusesAHeldSubmissionBeforeAnyActionRuns() throws Exception {
        // Verifies the second list is read at intake for a held submission too: a list that the engine cannot read is
        // found before the first action, and not at a release, after the visitor read a success.
        PlainAction mail = new PlainAction("t:mail");
        RecordingHold hold = new RecordingHold();
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail), List.of("t:mail"), List.of(),
                List.of(file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(hold);
        set(pipeline, "resolvedDeferredActions", null);
        when(systemNode.hasNode("deferredActions")).thenReturn(true);
        when(systemNode.getNode("deferredActions")).thenThrow(new javax.jcr.PathNotFoundException("deferredActions"));

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_012, error.errorCode);
        assertEquals(List.of(), ran);
        assertNull(hold.held.get());
    }

    @Test
    void aSubmissionWithNoUploadRunsTheActionsAfterReleaseInTheRequest() throws Exception {
        // Verifies decision D-i: with the switch on and no file, nothing is held, and the second list runs after the
        // first, inside the request.
        PlainAction mail = new PlainAction("t:mail");
        PlainAction store = new PlainAction("t:store");
        RecordingHold hold = new RecordingHold();
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail, store), List.of("t:mail"), List.of("t:store"),
                List.of());
        pipeline.useSubmissionHold(hold);

        dispatch(pipeline);

        assertEquals(List.of("t:mail", "t:store"), ran);
        assertEquals(List.of(), names(store.receivedFiles));
        assertNull(hold.held.get());
    }

    @Test
    void anActionAfterReleaseReceivesTheStandInOfTheRequestEvenInsideARequest() throws Exception {
        // Verifies an action of the second list is handed one request whenever it runs: the stand-in, which answers
        // the three values kept and refuses the rest, and no session of the visitor. The action of the first list
        // receives the request and the session of the visitor.
        PlainAction mail = new PlainAction("t:mail");
        PlainAction store = new PlainAction("t:store");
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail, store), List.of("t:mail"), List.of("t:store"),
                List.of());

        dispatch(pipeline);

        assertSame(request, mail.receivedRequest.get());
        assertSame(userSession, mail.receivedSession.get());
        HttpServletRequest standIn = store.receivedRequest.get();
        assertEquals("fr", standIn.getParameter("lang"));
        assertEquals("Europe/Paris", standIn.getHeader("X-Formidable-Time-Zone"));
        assertThrows(DetachedRequestException.class, standIn::getRemoteAddr);
        assertNull(store.receivedSession.get());
    }

    @Test
    void noHoldServiceRefusesASubmissionWithAnUploadBeforeAnyActionRuns() throws Exception {
        // Verifies the hold fails closed: with the switch on, a file and no hold service installed, the submission is
        // refused with FMDB-018, and no action ran, so no acknowledgement left for a submission that is lost.
        PlainAction mail = new PlainAction("t:mail");
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail), List.of("t:mail"), List.of(),
                List.of(file("cv", "cv.pdf")));

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_018, error.errorCode);
        assertEquals(503, error.httpStatus());
        assertEquals(List.of(), ran);
    }

    @Test
    void noHoldServiceStillAcceptsASubmissionWithNoUpload() throws Exception {
        // Verifies the refusal bears on the uploads alone: with no hold service and no file, both lists run.
        PlainAction mail = new PlainAction("t:mail");
        PlainAction store = new PlainAction("t:store");
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail, store), List.of("t:mail"), List.of("t:store"),
                List.of());

        assertDoesNotThrow(() -> dispatch(pipeline));

        assertEquals(List.of("t:mail", "t:store"), ran);
    }

    @Test
    void theUploadOfASensitiveFieldReachesNeitherAnActionNorTheHold() throws Exception {
        // Verifies the sensitive marker wins on a form with the switch on: the action that opts in to the sensitive
        // values receives no file, and the hold receives the upload of the plain file field alone.
        Contextual observer = new Contextual("t:observer");
        RecordingHold hold = new RecordingHold();
        FormSubmissionPipeline pipeline = pipeline(true, List.of(observer), List.of("t:observer"), List.of(),
                List.of(file("password", "secret.txt"), file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(hold);

        dispatch(pipeline);

        assertEquals(List.of(), names(observer.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(hold.held.get().files()));
    }

    @Test
    void aSubmissionWhoseOnlyUploadIsSensitiveIsNotHeld() throws Exception {
        // Verifies an upload that reaches nobody is no upload to hold: the submission runs as one with no file.
        PlainAction store = new PlainAction("t:store");
        RecordingHold hold = new RecordingHold();
        FormSubmissionPipeline pipeline = pipeline(true, List.of(store), List.of(), List.of("t:store"),
                List.of(file("password", "secret.txt")));
        pipeline.useSubmissionHold(hold);

        dispatch(pipeline);

        assertNull(hold.held.get());
        assertEquals(List.of(), names(store.receivedFiles));
    }

    @Test
    void aMandatoryQuarantineHandsTheUploadsOfAFormWithTheSwitchOffToNoAction() throws Exception {
        // Verifies the operator setting: with uploadsRequireQuarantine, a form with the switch off still runs its
        // actions, and none of them receives a file.
        when(uploads.isUploadsRequireQuarantine()).thenReturn(true);
        PlainAction mail = new PlainAction("t:mail");
        Contextual observer = new Contextual("t:observer");
        FormSubmissionPipeline pipeline = pipeline(false, List.of(mail, observer), List.of("t:mail", "t:observer"),
                List.of(), List.of(file("cv", "cv.pdf")));

        dispatch(pipeline);

        assertEquals(List.of(), names(mail.receivedFiles));
        assertEquals(List.of(), names(observer.receivedFiles));
    }

    @Test
    void aHoldThatCannotStoreRefusesTheSubmissionWithItsOwnMessage() throws Exception {
        // Verifies a failure the hold names refuses the submission with FMDB-018, and keeps the message of the hold.
        FormSubmissionPipeline pipeline = pipeline(true, List.of(), List.of(), List.of(), List.of(file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(submission -> {
            throw new SubmissionHoldException("the quarantine tree is full");
        });

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_018, error.errorCode);
        assertEquals("The hold service could not store the submission of form " + FORM_ID
                + ": the quarantine tree is full", error.getMessage());
    }

    @Test
    void aHoldThatThrowsLeavesNoValueInTheFailure() throws Exception {
        // Verifies the text of an exception the hold did not write is removed: the hold was handed every value, and a
        // library may quote one. The failure keeps the class of the exception and its stack.
        FormSubmissionPipeline pipeline = pipeline(true, List.of(), List.of(), List.of(), List.of(file("cv", "cv.pdf")));
        pipeline.useSubmissionHold(submission -> {
            throw new IllegalStateException("could not write " + submission.sensitiveParameters());
        });

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_018, error.errorCode);
        assertFalse(error.getMessage().contains(SECRET), error.getMessage());
        Throwable cause = assertInstanceOf(FormSubmissionPipeline.RedactedThrowable.class, error.getCause());
        assertFalse(cause.getMessage().contains(SECRET), cause.getMessage());
    }

    @Test
    void anActionAfterReleaseThatFailsInTheRequestIsCountedAfterTheActionsOfTheRequest() throws Exception {
        // Verifies the progress the visitor's browser reads: with one action in each list and the second failing,
        // one action of two completed.
        PlainAction mail = new PlainAction("t:mail");
        PlainAction store = new PlainAction("t:store");
        store.failure = FormActionException.serverError("the store is down");
        FormSubmissionPipeline pipeline = pipeline(true, List.of(mail, store), List.of("t:mail"), List.of("t:store"),
                List.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(1, error.actionsCompleted);
        assertEquals(2, error.actionsTotal);
    }

    @Test
    void anActionWithNoHandlerFailsAtItsPlace() throws Exception {
        // Verifies the failure of an action whose handler is not registered: FMDB-008 at the place of the action, with
        // the progress of the list.
        PlainAction mail = new PlainAction("t:mail");
        FormSubmissionPipeline pipeline = pipeline(false, List.of(mail), List.of("t:mail", "t:stopped"), List.of(),
                List.of(file("cv", "cv.pdf")));

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(1, error.actionsCompleted);
    }

    @Test
    void readOnlyMaintenanceCountsTheActionsAfterReleaseOfAFormWithTheSwitchOn() throws Exception {
        // Verifies the read-only rule reads both lists when the switch is on: an action after release that is
        // presumed to write may run in the request, so the form answers FMDB-014.
        FormSubmissionPipeline pipeline = pipeline(true, List.of(), List.of("t:mail"), List.of("t:write"), List.of(), true);

        SubmissionException error = assertThrows(SubmissionException.class, () -> verifyPlatformWritable(pipeline));

        assertEquals(ErrorCode.FMDB_014, error.errorCode);
    }

    @Test
    void readOnlyMaintenanceIgnoresTheSecondListOfAFormWithTheSwitchOff() throws Exception {
        // Verifies the second list counts for nothing when the switch is off: it never runs.
        FormSubmissionPipeline pipeline = pipeline(false, List.of(), List.of("t:mail"), List.of("t:write"), List.of(), true);

        assertDoesNotThrow(() -> verifyPlatformWritable(pipeline));
    }

    /** Steps 9a and 12, in the order {@link FormSubmissionPipeline#run} calls them. */
    private void dispatch(FormSubmissionPipeline pipeline) throws Exception {
        invoke(pipeline, FormSubmissionPipeline.class.getDeclaredMethod("separateSensitiveValues"));
        invoke(pipeline, FormSubmissionPipeline.class.getDeclaredMethod("dispatchActions", HttpServletRequest.class), request);
    }

    private static void verifyPlatformWritable(FormSubmissionPipeline pipeline) throws Exception {
        invoke(pipeline, FormSubmissionPipeline.class.getDeclaredMethod("verifyPlatformWritable"));
    }

    private static void invoke(FormSubmissionPipeline pipeline, Method method, Object... args) throws Exception {
        method.setAccessible(true);
        try {
            method.invoke(pipeline, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
