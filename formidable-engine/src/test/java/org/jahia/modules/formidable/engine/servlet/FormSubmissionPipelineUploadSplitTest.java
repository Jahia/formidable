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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The uploads at the dispatch of step 12 (ADR 0002): which actions of a form receive its files, by default and under
 * the operator setting {@code uploadsRequireQuarantine}.
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

    /** A contextual action that opts in to the sensitive values; it records the files it is handed. */
    private static final class Contextual implements ContextualFormAction {
        final String type;
        final AtomicReference<List<SubmittedFile>> receivedFiles = new AtomicReference<>();
        Contextual(String type) { this.type = type; }
        @Override public String getNodeType() { return type; }
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
    void aFormHandsItsUploadsToEveryAction() throws Exception {
        // Verifies a form keeps its behaviour by default: a plain action and a contextual one both receive the file.
        PlainAction mail = new PlainAction("t:mail");
        Contextual observer = new Contextual("t:observer");
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, observer), List.of("t:mail", "t:observer"),
                List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        assertEquals(List.of("cv.pdf"), names(mail.receivedFiles));
        assertEquals(List.of("cv.pdf"), names(observer.receivedFiles));
    }

    @Test
    void theUploadOfASensitiveFieldReachesNoAction() throws Exception {
        // Verifies the sensitive marker wins: an action that opts in to the sensitive values receives the upload of the
        // plain file field and not the one of the sensitive field.
        Contextual observer = new Contextual("t:observer");
        FormSubmissionPipeline pipeline = pipeline(List.of(observer), List.of("t:observer"),
                List.of(file("password", "secret.txt"), file("cv", "cv.pdf")), Set.of("password"));

        dispatch(pipeline);

        assertEquals(List.of("cv.pdf"), names(observer.receivedFiles));
    }

    @Test
    void aMandatoryQuarantineHandsTheUploadsToNoAction() throws Exception {
        // Verifies the operator setting: with uploadsRequireQuarantine, a form still runs its actions, and none of
        // them receives a file.
        when(uploads.isUploadsRequireQuarantine()).thenReturn(true);
        PlainAction mail = new PlainAction("t:mail");
        Contextual observer = new Contextual("t:observer");
        FormSubmissionPipeline pipeline = pipeline(List.of(mail, observer), List.of("t:mail", "t:observer"),
                List.of(file("cv", "cv.pdf")), Set.of());

        dispatch(pipeline);

        assertEquals(List.of(), names(mail.receivedFiles));
        assertEquals(List.of(), names(observer.receivedFiles));
    }

    @Test
    void anActionWithNoHandlerFailsAtItsPlace() throws Exception {
        // Verifies the failure of an action whose handler is not registered: FMDB-008 at the place of the action, with
        // the progress of the list.
        PlainAction mail = new PlainAction("t:mail");
        FormSubmissionPipeline pipeline = pipeline(List.of(mail), List.of("t:mail", "t:stopped"),
                List.of(file("cv", "cv.pdf")), Set.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(1, error.actionsCompleted);
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
