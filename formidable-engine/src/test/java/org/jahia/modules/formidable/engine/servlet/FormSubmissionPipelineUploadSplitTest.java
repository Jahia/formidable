package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.SensitiveFields;
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

import javax.jcr.ItemNotFoundException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The split of the uploads at the dispatch of step 12 (ADR 0002): which actions of a form receive its files, with and
 * without a quarantining action, and under the operator setting {@code uploadsRequireQuarantine}.
 */
class FormSubmissionPipelineUploadSplitTest {

    private static final String FORM_ID = "8f7e2a10-0000-4000-8000-000000000002";

    private UploadsConfigService uploads;
    private JCRSessionWrapper userSession;
    private JCRNodeWrapper userFormNode;
    private HttpServletRequest request;
    private JCRTemplate template;
    private JCRSessionWrapper systemSession;

    @BeforeEach
    void setUp() throws Exception {
        systemSession = mock(JCRSessionWrapper.class);
        JCRNodeWrapper anyNode = mock(JCRNodeWrapper.class);
        when(systemSession.getNodeByIdentifier(any())).thenReturn(anyNode);
        template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(systemSession));
        userSession = mock(JCRSessionWrapper.class);
        userFormNode = mock(JCRNodeWrapper.class);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("mysite");
        when(userFormNode.getResolveSite()).thenReturn(site);
        request = mock(HttpServletRequest.class);
        uploads = mock(UploadsConfigService.class);
    }

    /** A plain action, as the e-mail and the forward actions are: it records the files it is handed. */
    private static final class PlainAction implements FormAction {
        final String type;
        final AtomicReference<List<SubmittedFile>> receivedFiles = new AtomicReference<>();
        PlainAction(String type) { this.type = type; }
        @Override public String getNodeType() { return type; }
        @Override public void execute(JCRNodeWrapper n, HttpServletRequest r, JCRSessionWrapper s,
                                      Map<String, List<String>> p, List<SubmittedFile> f) { receivedFiles.set(f); }
    }

    /** A contextual action that quarantines the uploads or not, as the test decided; it records the files it is handed. */
    private static class Contextual implements ContextualFormAction {
        final String type;
        final boolean quarantines;
        final AtomicInteger asked = new AtomicInteger();
        final AtomicReference<List<SubmittedFile>> receivedFiles = new AtomicReference<>();
        Contextual(String type, boolean quarantines) { this.type = type; this.quarantines = quarantines; }
        @Override public String getNodeType() { return type; }
        @Override public boolean quarantinesUploads(JCRNodeWrapper actionNode) {
            asked.incrementAndGet();
            return quarantines;
        }
        @Override public SensitiveFields sensitiveFieldsConsumed(JCRNodeWrapper actionNode) { return SensitiveFields.all(); }
        @Override public FormActionResult execute(FormActionContext context) {
            receivedFiles.set(context.files());
            return FormActionResult.none();
        }
    }

    private static FormDataParser.FormFile file(String fieldName, String name) {
        return new FormDataParser.FormFile(fieldName, name, "application/pdf", new byte[] {1});
    }

    private static List<String> names(AtomicReference<List<SubmittedFile>> received) {
        return received.get().stream().map(SubmittedFile::originalName).toList();
    }

    private FormSubmissionPipeline pipeline(List<FormAction> handlers, List<String> actionTypes,
                                            List<FormDataParser.FormFile> files, Set<String> sensitive) throws Exception {
        Map<String, FormDataParser.FieldInfo> infos = new LinkedHashMap<>();
        for (String name : sensitive) {
            infos.put(name, new FormDataParser.FieldInfo("fmdbext:password", false, false, false, false, false, false,
                    false, Set.of(), Set.of(), null));
        }
        FormFieldMetadataCollector.Result metadata =
                new FormFieldMetadataCollector.Result(infos, Map.of(), Map.of(), Map.of(), Map.of(), sensitive);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                new FormSubmissionPipeline.Themes(uploads, mock(CaptchaConfigService.class), mock(FieldActionsConfigService.class)),
                handlers,
                (formId, locale) -> metadata,
                () -> template,
                (req, config, md) -> null,
                locale -> userSession,
                () -> false);
        pipeline.useResponse(mock(HttpServletResponse.class));
        List<FormSubmissionPipeline.ResolvedAction> resolved = new ArrayList<>();
        for (int i = 0; i < actionTypes.size(); i++) {
            resolved.add(new FormSubmissionPipeline.ResolvedAction("action-" + (i + 1), "/form/actions/a" + i, actionTypes.get(i), false));
        }
        set(pipeline, "formId", FORM_ID);
        set(pipeline, "locale", Locale.FRENCH);
        set(pipeline, "session", userSession);
        set(pipeline, "formNode", userFormNode);
        set(pipeline, "fieldMetadata", metadata);
        set(pipeline, "parsed", new FormDataParser.ParseResult(Map.of("email", List.of("ada@example.com")), files));
        set(pipeline, "resolvedActions", resolved);
        return pipeline;
    }

    @Test
    void aFormWithNoQuarantiningActionHandsItsUploadsToEveryAction() throws Exception {
        // Verifies a form with no quarantine keeps its behaviour: a plain action and a contextual one that does not opt
        // in both receive the file.
        PlainAction mail = new PlainAction("t:mail");
        Contextual passive = new Contextual("t:passive", false);
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, passive), List.of("t:mail", "t:passive"),
                List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        assertEquals(List.of("cv.pdf"), names(mail.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(passive.receivedFiles));
    }

    @Test
    void theUploadsOfAQuarantinedFormReachOnlyItsQuarantiningActions() throws Exception {
        // Verifies the split, whatever the order of the list: the plain action runs first, before the pipeline meets a
        // quarantining action, and still receives no file. The contextual action that does not opt in receives none
        // either, and each quarantining action receives the file.
        PlainAction mail = new PlainAction("t:mail");
        Contextual passive = new Contextual("t:passive", false);
        Contextual quarantine = new Contextual("t:quarantine", true);
        Contextual secondQuarantine = new Contextual("t:quarantine2", true);
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, passive, quarantine, secondQuarantine),
                List.of("t:mail", "t:passive", "t:quarantine", "t:quarantine2"), List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        assertEquals(List.of(), names(mail.receivedFiles));
        assertEquals(List.of(), names(passive.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(quarantine.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(secondQuarantine.receivedFiles));
    }

    @Test
    void theUploadOfASensitiveFieldReachesNoQuarantiningActionEither() throws Exception {
        // Verifies the sensitive marker still wins on a quarantined form: the quarantining action, which also opts in
        // to the sensitive values, receives the upload of the plain file field and not the one of the sensitive field.
        Contextual quarantine = new Contextual("t:quarantine", true);
        FormSubmissionPipeline pipeline = pipeline(List.of(quarantine), List.of("t:quarantine"),
                List.of(file("password", "secret.txt"), file("cv", "cv.pdf")), Set.of("password"));

        dispatch(pipeline);

        assertEquals(List.of("cv.pdf"), names(quarantine.receivedFiles));
    }

    @Test
    void aMandatoryQuarantineHandsTheUploadsOfAFormWithoutOneToNoAction() throws Exception {
        // Verifies the operator setting: with uploadsRequireQuarantine, a form with no quarantining action still runs
        // its actions, and none of them receives a file.
        when(uploads.isUploadsRequireQuarantine()).thenReturn(true);
        PlainAction mail = new PlainAction("t:mail");
        Contextual passive = new Contextual("t:passive", false);
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, passive), List.of("t:mail", "t:passive"),
                List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        assertEquals(List.of(), names(mail.receivedFiles));
        assertEquals(List.of(), names(passive.receivedFiles));
    }

    @Test
    void aMandatoryQuarantineChangesNothingForAQuarantinedForm() throws Exception {
        // Verifies the setting only bears on a form with no quarantining action: a quarantined form splits as before.
        when(uploads.isUploadsRequireQuarantine()).thenReturn(true);
        PlainAction mail = new PlainAction("t:mail");
        Contextual quarantine = new Contextual("t:quarantine", true);
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, quarantine), List.of("t:mail", "t:quarantine"),
                List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        assertEquals(List.of(), names(mail.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(quarantine.receivedFiles));
    }

    @Test
    void anActionWithNoHandlerWithholdsTheUploadsFromTheActionsBeforeIt() throws Exception {
        // Verifies the split fails closed: an action whose handler is not registered may be the quarantining one of the
        // form (its module is stopped), so the action that runs before the failure receives no file. The failure itself
        // is unchanged: FMDB-008 at the place of the action, with the progress of the list.
        PlainAction mail = new PlainAction("t:mail");
        FormSubmissionPipeline pipeline = pipeline(List.of(mail), List.of("t:mail", "t:stopped"),
                List.of(file("cv", "cv.pdf")), Set.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(1, error.actionsCompleted);
        assertEquals(List.of(), names(mail.receivedFiles));
    }

    @Test
    void aSubmissionWithNoUploadAsksNoAction() throws Exception {
        // Verifies the split costs nothing to a submission that carries no file: no action is asked whether it
        // quarantines uploads, and each one runs with an empty list.
        Contextual quarantine = new Contextual("t:quarantine", true);
        FormSubmissionPipeline pipeline = pipeline(List.of(quarantine), List.of("t:quarantine"), List.of(), Set.of());

        dispatch(pipeline);

        assertEquals(0, quarantine.asked.get());
        assertEquals(List.of(), names(quarantine.receivedFiles));
    }

    @Test
    void anActionIsAskedOnceForASubmission() throws Exception {
        // Verifies the split is decided once, before the first action runs: each contextual action is asked one time.
        Contextual passive = new Contextual("t:passive", false);
        Contextual quarantine = new Contextual("t:quarantine", true);
        PlainAction never = new PlainAction("t:unused");
        FormSubmissionPipeline pipeline = pipeline(List.of(passive, quarantine, never), List.of("t:passive", "t:quarantine"),
                List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        assertEquals(1, passive.asked.get());
        assertEquals(1, quarantine.asked.get());
        assertNull(never.receivedFiles.get());
    }

    @Test
    void anActionWithNoHandlerWithholdsTheUploadsFromAQuarantiningActionToo() throws Exception {
        // Verifies the split fails closed on a quarantined form as well: with an action whose handler is not
        // registered, neither the plain action nor the quarantining one receives the file, and the failure is FMDB-008.
        PlainAction mail = new PlainAction("t:mail");
        Contextual quarantine = new Contextual("t:quarantine", true);
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, quarantine), List.of("t:mail", "t:quarantine", "t:stopped"),
                List.of(file("cv", "cv.pdf")), Set.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(2, error.actionsCompleted);
        assertEquals(List.of(), names(mail.receivedFiles));
        assertEquals(List.of(), names(quarantine.receivedFiles));
    }

    @Test
    void anActionThatFailsToAnswerRefusesTheSubmissionBeforeAnyActionRuns() throws Exception {
        // Verifies an action whose quarantinesUploads throws fails as an action does: FMDB-008 with no action
        // completed, and the action before it in the list never ran, so no file left through it.
        PlainAction mail = new PlainAction("t:mail");
        Contextual broken = new Contextual("t:broken", true) {
            @Override public boolean quarantinesUploads(JCRNodeWrapper actionNode) {
                throw new IllegalStateException("no such property");
            }
        };
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, broken), List.of("t:mail", "t:broken"),
                List.of(file("cv", "cv.pdf")), Set.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(0, error.actionsCompleted);
        assertEquals(2, error.actionsTotal);
        assertNull(mail.receivedFiles.get());
    }

    @Test
    void anActionNodeThatCannotBeReadRefusesTheSubmissionBeforeAnyActionRuns() throws Exception {
        // Verifies a repository failure of the ask never opens the uploads to every action: the submission is refused
        // with FMDB-012, and no action ran.
        when(systemSession.getNodeByIdentifier(any())).thenThrow(new ItemNotFoundException("action-2"));
        PlainAction mail = new PlainAction("t:mail");
        Contextual quarantine = new Contextual("t:quarantine", true);
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, quarantine), List.of("t:mail", "t:quarantine"),
                List.of(file("cv", "cv.pdf")), Set.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_012, error.errorCode);
        assertNull(mail.receivedFiles.get());
        assertNull(quarantine.receivedFiles.get());
    }

    @Test
    void aFormOfPlainActionsOpensNoSessionToAskThem() throws Exception {
        // Verifies the ask costs nothing to a form that holds no contextual action: the pipeline opens one system
        // session for each action it runs, and none to ask.
        PlainAction mail = new PlainAction("t:mail");
        PlainAction forward = new PlainAction("t:forward");
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, forward), List.of("t:mail", "t:forward"),
                List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        verify(template, times(2)).doExecuteWithSystemSessionAsUser(any(), any(), any(), any());
        assertEquals(List.of("cv.pdf"), names(mail.receivedFiles));
    }

    /** Steps 9a and 12, in the order {@link FormSubmissionPipeline#run} calls them. */
    private void dispatch(FormSubmissionPipeline pipeline) throws Exception {
        Method separate = FormSubmissionPipeline.class.getDeclaredMethod("separateSensitiveValues");
        separate.setAccessible(true);
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("dispatchActions", HttpServletRequest.class);
        method.setAccessible(true);
        try {
            separate.invoke(pipeline);
            method.invoke(pipeline, request);
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
