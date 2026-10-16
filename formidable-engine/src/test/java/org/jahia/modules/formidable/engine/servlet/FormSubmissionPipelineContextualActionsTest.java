package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
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
import java.util.Set;
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
 * Step 11a and the dispatch of step 12 for the actions that answer the browser: the split of the sensitive values,
 * what each kind of action receives, what a contextual action's result and refusal become.
 */
class FormSubmissionPipelineContextualActionsTest {

    private static final String FORM_ID = "8f7e2a10-0000-4000-8000-000000000001";
    private static final String SECRET = "Sup3r-Secret!";

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
        final boolean consumes;
        final FormActionResult result;
        final FormActionException failure;
        final AtomicReference<FormActionContext> received = new AtomicReference<>();

        Contextual(String type, boolean consumes, FormActionResult result, FormActionException failure) {
            this.type = type; this.consumes = consumes; this.result = result; this.failure = failure;
        }
        @Override public String getNodeType() { return type; }
        @Override public boolean consumesSensitiveValues() { return consumes; }
        @Override public FormActionResult execute(FormActionContext context) throws FormActionException {
            received.set(context);
            if (failure != null) throw failure;
            return result;
        }
    }

    private static FormFieldMetadataCollector.Result metadata(Set<String> sensitive) {
        return new FormFieldMetadataCollector.Result(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), sensitive);
    }

    private FormSubmissionPipeline pipeline(List<FormAction> actions, List<FormSubmissionPipeline.ResolvedAction> resolved,
                                            Map<String, List<String>> parameters, Set<String> sensitive) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                new FormSubmissionPipeline.Themes(mock(UploadsConfigService.class), mock(CaptchaConfigService.class), mock(FieldActionsConfigService.class)),
                actions,
                (formId, locale) -> metadata(sensitive),
                () -> template,
                (req, uploads, md) -> null,
                locale -> userSession,
                () -> false);
        pipeline.useResponse(response);
        set(pipeline, "formId", FORM_ID);
        set(pipeline, "locale", Locale.FRENCH);
        set(pipeline, "session", userSession);
        set(pipeline, "formNode", userFormNode);
        set(pipeline, "fieldMetadata", metadata(sensitive));
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

    private static Map<String, List<String>> signUpValues() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("email", List.of("ada@example.com"));
        values.put("password", List.of(SECRET));
        return values;
    }

    @Test
    void aSensitiveValueReachesOnlyTheActionThatDeclaredItConsumesIt() throws Exception {
        // Verifies the guarantee of fmdbmix:sensitiveField: the legacy action (save to JCR, e-mail, forward are of this
        // kind) and a contextual action that does not consume get the public map only; the consuming action gets the
        // secret in its own map, never in the public one.
        LegacyAction legacy = new LegacyAction();
        Contextual passive = new Contextual("t:passive", false, FormActionResult.none(), null);
        Contextual consuming = new Contextual("t:consuming", true, FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(legacy, passive, consuming),
                resolved("t:legacy", "t:passive", "t:consuming"), signUpValues(), Set.of("password"));

        dispatch(pipeline);

        assertEquals(Map.of("email", List.of("ada@example.com")), legacy.received.get());
        assertEquals(Map.of("email", List.of("ada@example.com")), passive.received.get().parameters());
        assertTrue(passive.received.get().sensitiveParameters().isEmpty());
        assertEquals(Map.of("email", List.of("ada@example.com")), consuming.received.get().parameters());
        assertEquals(SECRET, consuming.received.get().sensitiveParameter("password"));
        assertNull(consuming.received.get().parameter("password"));
    }

    @Test
    void theContextCarriesTheRequestTheResponseTheLocaleAndTheSite() throws Exception {
        // Verifies what a contextual action is handed besides the values: the response to set a cookie on, the
        // submission's locale, the form's site key, the action node and the form read in the same system session.
        Contextual action = new Contextual("t:ctx", false, FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(action), resolved("t:ctx"), signUpValues(), Set.of());

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
    void theAcceptedSubmissionTheEnrichersReadNeverHoldsASensitiveValue() throws Exception {
        // Verifies the jExperience boundary: the enrichers (jExperience's among them) receive AcceptedSubmission, built
        // from the public map, so a sensitive value cannot be sent to jCustomer whatever the field is mapped to.
        FormSubmissionPipeline pipeline = pipeline(List.of(), resolved(), signUpValues(), Set.of("password"));
        dispatch(pipeline);

        AcceptedSubmission accepted = pipeline.accepted();

        assertEquals(Map.of("email", List.of("ada@example.com")), accepted.parameters());
        assertFalse(accepted.toString().contains(SECRET));
    }

    @Test
    void theFirstRedirectAndTheFirstMessageWinInListOrderAndAnUnsafeRedirectIsDropped() throws Exception {
        // Verifies what the pipeline keeps of the actions' answers: an unsafe redirect (another host) is dropped and
        // never reaches the browser; the first safe redirect and the first message stay, later ones are ignored.
        Contextual first = new Contextual("t:first", false, new FormActionResult("https://evil.example/", "Bienvenue"), null);
        Contextual second = new Contextual("t:second", false, new FormActionResult("/sites/mysite/home.html", "Ignored"), null);
        Contextual third = new Contextual("t:third", false, FormActionResult.redirect("/other.html"), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(first, second, third), resolved("t:first", "t:second", "t:third"),
                signUpValues(), Set.of());

        dispatch(pipeline);

        assertEquals("/sites/mysite/home.html", pipeline.browserInstruction().redirectPath());
        assertEquals("Bienvenue", pipeline.browserInstruction().message());
    }

    @Test
    void aRefusalWithAVisitorMessageBecomesFmdb018WithTheActionsStatus() throws Exception {
        // Verifies the user-facing failure: the action's own status and text reach the servlet, as FMDB-018 and not
        // the opaque FMDB-008, and the progress says how many actions ran before.
        Contextual refusing = new Contextual("t:refuse", false, null,
                FormActionException.refusal(429, "rate limit", "Trop de tentatives."));
        FormSubmissionPipeline pipeline = pipeline(List.of(refusing), resolved("t:refuse"), signUpValues(), Set.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_018, error.errorCode);
        assertEquals(429, error.httpStatus());
        assertEquals("Trop de tentatives.", error.visitorMessage());
        assertEquals(0, error.actionsCompleted);
    }

    @Test
    void anOrdinaryActionFailureStaysOpaque() throws Exception {
        // Verifies the SPI stays backward compatible: an exception without a visitor message is FMDB-008 and carries
        // no text, exactly as before.
        Contextual failing = new Contextual("t:fail", false, null, FormActionException.serverError("boom"));
        FormSubmissionPipeline pipeline = pipeline(List.of(failing), resolved("t:fail"), signUpValues(), Set.of());

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertNull(error.visitorMessage());
    }

    @Test
    void acceptedReadsTheFormAgainWhenAnActionClosedTheGuestSession() throws Exception {
        // Verifies the sign-in side effect: replacing the HTTP session closes the guest's JCR session, and the
        // enrichers must still be handed a readable form, re-read as whoever the request is now.
        FormSubmissionPipeline pipeline = pipeline(List.of(), resolved(), signUpValues(), Set.of());
        JCRSessionWrapper closed = mock(JCRSessionWrapper.class);
        when(closed.isLive()).thenReturn(false);
        when(userFormNode.getSession()).thenReturn(closed);
        JCRSessionWrapper fresh = mock(JCRSessionWrapper.class);
        JCRNodeWrapper freshForm = mock(JCRNodeWrapper.class);
        JCRSiteNode site = mock(JCRSiteNode.class);
        when(site.getSiteKey()).thenReturn("mysite");
        when(freshForm.getResolveSite()).thenReturn(site);
        when(fresh.getNodeByIdentifier(FORM_ID)).thenReturn(freshForm);
        FormSubmissionPipeline reopening = new FormSubmissionPipeline(
                new FormSubmissionPipeline.Themes(mock(UploadsConfigService.class), mock(CaptchaConfigService.class), mock(FieldActionsConfigService.class)),
                List.of(), (formId, locale) -> metadata(Set.of()), () -> template, (req, uploads, md) -> null,
                locale -> fresh, () -> false);
        set(reopening, "formId", FORM_ID);
        set(reopening, "locale", Locale.ENGLISH);
        set(reopening, "formNode", userFormNode);
        set(reopening, "parsed", new FormDataParser.ParseResult(signUpValues(), List.of()));

        AcceptedSubmission accepted = reopening.accepted();

        assertSame(freshForm, accepted.formNode());
        assertEquals("mysite", accepted.siteKey());
        assertNull(pipeline.browserInstruction().redirectPath());
    }

    @Test
    void theContextNeverPrintsAValue() {
        // Verifies the log hygiene of the context: a log line that prints it names the fields, never their content.
        FormActionContext context = new FormActionContext(actionNode, null, request, response, userSession, Locale.ENGLISH,
                "mysite", Map.of("email", List.of("ada@example.com")), Map.of("password", List.of(SECRET)), List.of());

        String printed = context.toString();

        assertFalse(printed.contains(SECRET));
        assertFalse(printed.contains("ada@example.com"));
        assertTrue(printed.contains("password"));
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
