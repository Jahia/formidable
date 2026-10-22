package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dispatch of step 12 for the actions that answer the browser: what each kind of action receives, and what a
 * contextual action's result becomes.
 */
class FormSubmissionPipelineContextualActionsTest {

    private static final String FORM_ID = "8f7e2a10-0000-4000-8000-000000000001";

    private JCRNodeWrapper actionNode;
    private JCRNodeWrapper systemFormNode;
    private JCRNodeWrapper userFormNode;
    private JCRSessionWrapper userSession;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private JCRTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        actionNode = mock(JCRNodeWrapper.class);
        systemFormNode = mock(JCRNodeWrapper.class);
        JCRSessionWrapper systemSession = mock(JCRSessionWrapper.class);
        when(systemSession.getNodeByIdentifier(any())).thenReturn(actionNode);
        when(systemSession.getNodeByIdentifier(FORM_ID)).thenReturn(systemFormNode);
        template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(systemSession));
        userSession = mock(JCRSessionWrapper.class);
        userFormNode = mock(JCRNodeWrapper.class);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("mysite");
        when(userFormNode.getResolveSite()).thenReturn(site);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    /** A recording legacy action: what it received as parameters. */
    private static final class LegacyAction implements FormAction {
        final AtomicReference<Map<String, List<String>>> received = new AtomicReference<>();
        @Override public String getNodeType() { return "t:legacy"; }
        @Override public void execute(JCRNodeWrapper n, HttpServletRequest r, JCRSessionWrapper s,
                                      Map<String, List<String>> p, List<SubmittedFile> f) { received.set(p); }
    }

    /** A contextual action answering what the test decided, recording its context. */
    private static final class Contextual implements ContextualFormAction {
        final String type;
        final FormActionResult result;
        final FormActionException failure;
        final AtomicReference<FormActionContext> received = new AtomicReference<>();

        Contextual(String type, FormActionResult result, FormActionException failure) {
            this.type = type; this.result = result; this.failure = failure;
        }
        @Override public String getNodeType() { return type; }
        @Override public FormActionResult execute(FormActionContext context) throws FormActionException {
            received.set(context);
            if (failure != null) throw failure;
            return result;
        }
    }

    private static FormFieldMetadataCollector.Result metadata() {
        return new FormFieldMetadataCollector.Result(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private FormSubmissionPipeline pipeline(List<FormAction> actions, List<FormSubmissionPipeline.ResolvedAction> resolved,
                                            Map<String, List<String>> parameters) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                new FormSubmissionPipeline.Themes(mock(UploadsConfigService.class), mock(CaptchaConfigService.class), mock(FieldActionsConfigService.class)),
                actions,
                (formId, locale) -> metadata(),
                () -> template,
                (req, uploads, md) -> null,
                locale -> userSession,
                () -> false);
        pipeline.useResponse(response);
        set(pipeline, "formId", FORM_ID);
        set(pipeline, "locale", Locale.FRENCH);
        set(pipeline, "session", userSession);
        set(pipeline, "formNode", userFormNode);
        set(pipeline, "fieldMetadata", metadata());
        set(pipeline, "parsed", new FormDataParser.ParseResult(parameters, List.of()));
        set(pipeline, "resolvedActions", resolved);
        return pipeline;
    }

    private static List<FormSubmissionPipeline.ResolvedAction> resolved(String... types) {
        List<FormSubmissionPipeline.ResolvedAction> list = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            list.add(new FormSubmissionPipeline.ResolvedAction("action-" + (i + 1), "/form/actions/a" + i, types[i], false));
        }
        return list;
    }

    private static Map<String, List<String>> values() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("email", List.of("ada@example.com"));
        values.put("name", List.of("Ada"));
        return values;
    }

    @Test
    void aLegacyActionAndAContextualActionReceiveTheSameValues() throws Exception {
        // Verifies the SPI changes nothing for the existing actions: a plain action and a contextual one, side by side,
        // read the same accepted values; no field is sensitive yet, so the sensitive map stays empty.
        LegacyAction legacy = new LegacyAction();
        Contextual contextual = new Contextual("t:ctx", FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(legacy, contextual), resolved("t:legacy", "t:ctx"), values());

        dispatch(pipeline);

        assertEquals(values(), legacy.received.get());
        assertEquals(values(), contextual.received.get().parameters());
        assertTrue(contextual.received.get().sensitiveParameters().isEmpty());
    }

    @Test
    void theContextCarriesTheRequestTheResponseTheLocaleAndTheSite() throws Exception {
        // Verifies what a contextual action is handed besides the values: the response to set a cookie on, the
        // submission's locale, the form's site key, the action node and the form read in the same system session.
        Contextual action = new Contextual("t:ctx", FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(action), resolved("t:ctx"), values());

        dispatch(pipeline);

        FormActionContext context = action.received.get();
        assertSame(request, context.request());
        assertSame(response, context.response());
        assertSame(userSession, context.session());
        assertSame(actionNode, context.actionNode());
        assertSame(systemFormNode, context.formNode());
        assertEquals(Locale.FRENCH, context.locale());
        assertEquals("mysite", context.siteKey());
    }

    @Test
    void theFirstRedirectAndTheFirstMessageWinInListOrderAndAnUnsafeRedirectIsDropped() throws Exception {
        // Verifies what the pipeline keeps of the actions' answers: an unsafe redirect (another host) is dropped and
        // never reaches the browser; the first safe redirect and the first message stay, later ones are ignored.
        Contextual first = new Contextual("t:first", new FormActionResult("https://evil.example/", null, "Bienvenue"), null);
        Contextual second = new Contextual("t:second", new FormActionResult("/sites/mysite/home.html", null, "Ignored"), null);
        Contextual third = new Contextual("t:third", FormActionResult.redirect("/other.html"), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(first, second, third), resolved("t:first", "t:second", "t:third"),
                values());

        dispatch(pipeline);

        assertEquals("/sites/mysite/home.html", pipeline.browserInstruction().redirectPath());
        assertEquals("Bienvenue", pipeline.browserInstruction().message());
    }

    @Test
    void aUrlFromTheConfigurationIsKeptAndTheFirstRedirectOfEitherKindWins() throws Exception {
        // Verifies the second kind: an absolute http(s) URL passes its own rule, and a path returned after it loses.
        Contextual url = new Contextual("t:url", FormActionResult.redirectToUrl("https://www.example.com/merci"), null);
        Contextual path = new Contextual("t:path", FormActionResult.redirect("/sites/mysite/home.html"), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(url, path), resolved("t:url", "t:path"), values());

        dispatch(pipeline);

        assertEquals("https://www.example.com/merci", pipeline.browserInstruction().redirectUrl());
        assertNull(pipeline.browserInstruction().redirectPath());
    }

    @Test
    void eachKindIsHeldToItsOwnRule() throws Exception {
        // Verifies an absolute URL handed to redirect(path) is still dropped (a target computed from the request must
        // stay on this server), and a URL that is not http(s) is dropped too, so the next redirect wins.
        Contextual hostPath = new Contextual("t:hostPath", FormActionResult.redirect("https://www.example.com/"), null);
        Contextual script = new Contextual("t:script", FormActionResult.redirectToUrl("javascript:alert(1)"), null);
        Contextual userInfo = new Contextual("t:userInfo", FormActionResult.redirectToUrl("https://www.example.com@evil.example/"), null);
        Contextual path = new Contextual("t:path", FormActionResult.redirect("/sites/mysite/home.html"), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(hostPath, script, userInfo, path),
                resolved("t:hostPath", "t:script", "t:userInfo", "t:path"), values());

        dispatch(pipeline);

        assertEquals("/sites/mysite/home.html", pipeline.browserInstruction().redirectPath());
        assertNull(pipeline.browserInstruction().redirectUrl());
    }

    @Test
    void aPipelineWithoutContextualActionsHasNoInstruction() throws Exception {
        // Verifies the default: a form of plain actions answers the browser exactly as before.
        FormSubmissionPipeline pipeline = pipeline(List.of(new LegacyAction()), resolved("t:legacy"), values());

        dispatch(pipeline);

        assertTrue(pipeline.browserInstruction().isEmpty());
    }

    @Test
    void aContextualActionFailureStaysOpaque() throws Exception {
        // Verifies a contextual action fails as a plain one does: FMDB-008, with the progress of the actions.
        Contextual failing = new Contextual("t:fail", null, FormActionException.serverError("boom"));
        FormSubmissionPipeline pipeline = pipeline(List.of(failing), resolved("t:fail"), values());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(0, error.actionsCompleted);
        assertNull(pipeline.browserInstruction().redirectPath());
    }

    @Test
    void theContextNeverPrintsAValue() {
        // Verifies the log hygiene of the context: a log line that prints it names the fields, never their content.
        FormActionContext context = new FormActionContext(actionNode, null, request, response, userSession, Locale.ENGLISH,
                "mysite", Map.of("email", List.of("ada@example.com")), Map.of(), List.of());

        String printed = context.toString();

        assertFalse(printed.contains("ada@example.com"));
        assertTrue(printed.contains("email"));
    }

    private void dispatch(FormSubmissionPipeline pipeline) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("dispatchActions", HttpServletRequest.class);
        method.setAccessible(true);
        try {
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
