package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.field.FieldActionDispatcher;
import org.jahia.modules.formidable.engine.actions.field.ResolvedFieldAction;
import org.jahia.modules.formidable.engine.actions.field.VerdictCache;
import org.jahia.modules.formidable.engine.actions.form.email.SendEmailContentFormAction;
import org.jahia.modules.formidable.engine.actions.form.email.SendEmailNotificationFormAction;
import org.jahia.modules.formidable.engine.actions.form.forward.ForwardSubmissionFormAction;
import org.jahia.modules.formidable.engine.actions.form.storage.SaveToJcrFormAction;
import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
import org.jahia.modules.formidable.engine.api.ContextualFormAction;
import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionException;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.SensitiveFields;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.modules.formidable.engine.config.captcha.CaptchaConfigService;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService.FieldActionSettings;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicEvaluator;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicRule;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step 9a and the dispatch of step 12 for the actions that answer the browser: the split of the sensitive values, what
 * each kind of action receives, and what a contextual action's result becomes.
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

    /** A recording legacy action: what it received as parameters and files. */
    private static final class LegacyAction implements FormAction {
        final AtomicReference<Map<String, List<String>>> received = new AtomicReference<>();
        final AtomicReference<List<SubmittedFile>> receivedFiles = new AtomicReference<>();
        @Override public String getNodeType() { return "t:legacy"; }
        @Override public void execute(JCRNodeWrapper n, HttpServletRequest r, JCRSessionWrapper s,
                                      Map<String, List<String>> p, List<SubmittedFile> f) { received.set(p); receivedFiles.set(f); }
    }

    /** A contextual action answering what the test decided, selecting the sensitive fields it was told to, recording its context. */
    private static final class Contextual implements ContextualFormAction {
        final String type;
        final FormActionResult result;
        final FormActionException failure;
        final SensitiveFields consumed;
        final AtomicReference<FormActionContext> received = new AtomicReference<>();
        final AtomicReference<JCRNodeWrapper> askedAbout = new AtomicReference<>();

        Contextual(String type, FormActionResult result, FormActionException failure) {
            this(type, SensitiveFields.none(), result, failure);
        }
        Contextual(String type, SensitiveFields consumed, FormActionResult result, FormActionException failure) {
            this.type = type; this.consumed = consumed; this.result = result; this.failure = failure;
        }
        @Override public String getNodeType() { return type; }
        @Override public SensitiveFields sensitiveFieldsConsumed(JCRNodeWrapper actionNode) {
            askedAbout.set(actionNode);
            return consumed;
        }
        @Override public FormActionResult execute(FormActionContext context) throws FormActionException {
            received.set(context);
            if (failure != null) throw failure;
            return result;
        }
    }

    /** A legacy action that writes into the map it is handed, as an action written against the engine before 9a may. */
    private static final class WritingAction implements FormAction {
        final AtomicReference<Map<String, List<String>>> received = new AtomicReference<>();
        @Override public String getNodeType() { return "t:writing"; }
        @Override public void execute(JCRNodeWrapper n, HttpServletRequest r, JCRSessionWrapper s,
                                      Map<String, List<String>> p, List<SubmittedFile> f) {
            p.put("computed", List.of("42"));
            received.set(p);
        }
    }

    /** A legacy action that crashes with the exception the test decided. */
    private static final class CrashingLegacyAction implements FormAction {
        final RuntimeException crash;
        CrashingLegacyAction(RuntimeException crash) { this.crash = crash; }
        @Override public String getNodeType() { return "t:crash"; }
        @Override public void execute(JCRNodeWrapper n, HttpServletRequest r, JCRSessionWrapper s,
                                      Map<String, List<String>> p, List<SubmittedFile> f) { throw crash; }
    }

    /** A contextual action that selects the sensitive fields it was told to, then crashes with a runtime exception. */
    private static final class CrashingContextual implements ContextualFormAction {
        final SensitiveFields consumed;
        final RuntimeException crash;
        CrashingContextual(SensitiveFields consumed, RuntimeException crash) { this.consumed = consumed; this.crash = crash; }
        @Override public String getNodeType() { return "t:crash"; }
        @Override public SensitiveFields sensitiveFieldsConsumed(JCRNodeWrapper actionNode) { return consumed; }
        @Override public FormActionResult execute(FormActionContext context) { throw crash; }
    }

    private static FormFieldMetadataCollector.Result metadata() {
        return metadata(Set.of());
    }

    /** The metadata of a form whose named fields are sensitive, the way the collector reads fmdbmix:sensitiveField. */
    private static FormFieldMetadataCollector.Result metadata(Set<String> sensitive) {
        Map<String, FormDataParser.FieldInfo> infos = new LinkedHashMap<>();
        for (String name : sensitive) {
            infos.put(name, new FormDataParser.FieldInfo("fmdbext:password", false, false, false, false, false, false,
                    false, Set.of(), Set.of(), null));
        }
        return new FormFieldMetadataCollector.Result(infos, Map.of(), Map.of(), Map.of(), Map.of(), sensitive);
    }

    private FormSubmissionPipeline pipeline(List<FormAction> actions, List<FormSubmissionPipeline.ResolvedAction> resolved,
                                            Map<String, List<String>> parameters) throws Exception {
        return pipeline(actions, resolved, parameters, Set.of());
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

    private static Map<String, List<String>> signUpValues() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("email", List.of("ada@example.com"));
        values.put("password", List.of(SECRET));
        return values;
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
        // read the same accepted values; on a form without a sensitive field the sensitive map stays empty.
        LegacyAction legacy = new LegacyAction();
        Contextual contextual = new Contextual("t:ctx", FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(legacy, contextual), resolved("t:legacy", "t:ctx"), values());

        dispatch(pipeline);

        assertEquals(values(), legacy.received.get());
        assertEquals(values(), contextual.received.get().parameters());
        assertTrue(contextual.received.get().sensitiveParameters().isEmpty());
    }

    @Test
    void aSensitiveValueReachesOnlyTheActionThatSelectsIt() throws Exception {
        // Verifies the guarantee of fmdbmix:sensitiveField (Case 4): the plain action (save to JCR, e-mail and forward
        // are of this kind) and a contextual action that does not opt in get the public map only; the consumer gets the
        // secret in its own map, never in the public one, and is asked about its selection with its own node.
        LegacyAction legacy = new LegacyAction();
        Contextual passive = new Contextual("t:passive", FormActionResult.none(), null);
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(legacy, passive, consumer),
                resolved("t:legacy", "t:passive", "t:consumer"), signUpValues(), Set.of("password"));

        dispatch(pipeline);

        Map<String, List<String>> open = Map.of("email", List.of("ada@example.com"));
        assertEquals(open, legacy.received.get());
        assertFalse(legacy.received.get().containsKey("password"));
        assertEquals(open, passive.received.get().parameters());
        assertTrue(passive.received.get().sensitiveParameters().isEmpty());
        assertEquals(open, consumer.received.get().parameters());
        assertEquals(SECRET, consumer.received.get().sensitiveParameter("password"));
        assertNull(consumer.received.get().parameter("password"));
        assertSame(actionNode, consumer.askedAbout.get());
    }

    @Test
    void theSelectionGivesEverySensitiveValueOrNone() throws Exception {
        // Verifies the selection table of sensitiveFieldsConsumed, one row per assertion: none gives nothing, all gives
        // every sensitive value the form received, and a null selection gives nothing (fail-closed).
        Map<String, List<String>> received = new LinkedHashMap<>();
        for (SensitiveFields selection : List.of(SensitiveFields.none(), SensitiveFields.all())) {
            Contextual action = new Contextual("t:ctx", selection, FormActionResult.none(), null);
            FormSubmissionPipeline pipeline = pipeline(List.of(action), resolved("t:ctx"), signUpValues(), Set.of("password", "pin"));
            dispatch(pipeline);
            received.put(selection.toString(), List.copyOf(action.received.get().sensitiveParameters().keySet()));
        }

        assertEquals(List.of(), received.get(SensitiveFields.none().toString()));
        assertEquals(List.of("password"), received.get(SensitiveFields.all().toString()));
        assertTrue(FormSubmissionPipeline.select(Map.of("password", List.of(SECRET)), null).isEmpty());
    }

    @Test
    void theUploadsOfASensitiveFieldReachNoAction() throws Exception {
        // Verifies decision D4 fails closed: a type that declares fmdbmix:sensitiveField and fmdbmix:fileField is
        // forbidden, and should one still send a file, the sensitive marker wins. Neither a plain action, nor a
        // contextual one, nor the consumer receives that upload; the upload of a field that is not sensitive reaches
        // them all.
        LegacyAction legacy = new LegacyAction();
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(legacy, consumer), resolved("t:legacy", "t:consumer"),
                signUpValues(), Set.of("password"));
        set(pipeline, "parsed", new FormDataParser.ParseResult(signUpValues(), List.of(
                new FormDataParser.FormFile("password", "secret.txt", "text/plain", SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new FormDataParser.FormFile("cv", "cv.pdf", "application/pdf", new byte[] {1}))));

        dispatch(pipeline);

        assertEquals(List.of("cv"), legacy.receivedFiles.get().stream().map(SubmittedFile::fieldName).toList());
        assertEquals(List.of("cv"), consumer.received.get().files().stream().map(SubmittedFile::fieldName).toList());
    }

    @Test
    void theAcceptedSubmissionTheEnrichersReadNeverHoldsASensitiveValue() throws Exception {
        // Verifies the jExperience boundary: the enrichers (jExperience's among them) receive AcceptedSubmission, built
        // from the public map, so a sensitive value cannot reach the 200 body nor jCustomer, whatever the field claims.
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer), resolved("t:consumer"), signUpValues(), Set.of("password"));
        dispatch(pipeline);

        AcceptedSubmission accepted = pipeline.accepted();

        assertEquals(Map.of("email", List.of("ada@example.com")), accepted.parameters());
        assertFalse(accepted.toString().contains(SECRET));
    }

    @Test
    void aSensitiveValueNoActionConsumesIsDroppedSilently() throws Exception {
        // Verifies open point 1: a form with a password and only plain actions accepts the submission, the value goes
        // nowhere, and nothing about it reaches the operator's log at the default level.
        LegacyAction legacy = new LegacyAction();
        FormSubmissionPipeline pipeline = pipeline(List.of(legacy), resolved("t:legacy"), signUpValues(), Set.of("password"));

        java.io.PrintStream previous = System.err;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(captured, true, java.nio.charset.StandardCharsets.UTF_8));
        try {
            dispatch(pipeline);
        } finally {
            System.setErr(previous);
        }

        assertEquals(Map.of("email", List.of("ada@example.com")), legacy.received.get());
        assertFalse(captured.toString(java.nio.charset.StandardCharsets.UTF_8).contains(SECRET));
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
    void anUnresolvableSiteGivesAnEmptySiteKey() throws Exception {
        // Verifies the context never carries a null site key: a form whose site cannot be resolved hands the action "".
        when(userFormNode.getResolveSite()).thenThrow(new IllegalStateException("no site"));
        Contextual action = new Contextual("t:ctx", FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(action), resolved("t:ctx"), values());

        dispatch(pipeline);

        assertEquals("", action.received.get().siteKey());
    }

    @Test
    void aBlankMessageDoesNotHideTheMessageOfALaterAction() throws Exception {
        // Verifies a blank message is no message: it does not become the first one, so the real message of a later
        // action reaches the browser instead of the form's generic message.
        Contextual blank = new Contextual("t:blank", FormActionResult.showMessage("  "), null);
        Contextual real = new Contextual("t:real", FormActionResult.showMessage("Check your mailbox"), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(blank, real), resolved("t:blank", "t:real"), values());

        dispatch(pipeline);

        assertEquals("Check your mailbox", pipeline.browserInstruction().message());
    }

    @Test
    void theFirstRedirectAndTheFirstMessageWinInListOrderAndAnUnsafeRedirectIsDropped() throws Exception {
        // Verifies what the pipeline keeps of the actions' answers: an unsafe redirect (another host) is dropped and
        // never reaches the browser; the first safe redirect and the first message stay, later ones are ignored.
        Contextual first = new Contextual("t:first", new FormActionResult("https://evil.example/", null, "Bienvenue"), null);
        Contextual second = new Contextual("t:second", new FormActionResult("/sites/mysite/home.html", null, "Ignored"), null);
        Contextual third = new Contextual("t:third", FormActionResult.redirectTo("/other.html"), null);
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
        Contextual path = new Contextual("t:path", FormActionResult.redirectTo("/sites/mysite/home.html"), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(url, path), resolved("t:url", "t:path"), values());

        dispatch(pipeline);

        assertEquals("https://www.example.com/merci", pipeline.browserInstruction().redirectUrl());
        assertNull(pipeline.browserInstruction().redirectPath());
    }

    @Test
    void eachKindIsHeldToItsOwnRule() throws Exception {
        // Verifies an absolute URL handed to redirectTo(path) is still dropped (a target computed from the request must
        // stay on this server), and a URL that is not http(s) is dropped too, so the next redirect wins.
        Contextual hostPath = new Contextual("t:hostPath", FormActionResult.redirectTo("https://www.example.com/"), null);
        Contextual script = new Contextual("t:script", FormActionResult.redirectToUrl("javascript:alert(1)"), null);
        Contextual userInfo = new Contextual("t:userInfo", FormActionResult.redirectToUrl("https://www.example.com@evil.example/"), null);
        Contextual path = new Contextual("t:path", FormActionResult.redirectTo("/sites/mysite/home.html"), null);
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
    void theCrashOfAnActionHandedTheSecretKeepsItsStackButNotItsText() throws Exception {
        // Verifies the log hygiene of a consumer's crash: a library may quote the password in its message, so the
        // runtime exception is not rethrown whole. The visitor gets the 500 FMDB-500 the servlet answers to any action's
        // crash, and the operator's log, which prints the failure with its causes, keeps every class and every stack
        // frame of the chain and none of its text.
        IllegalArgumentException root = new IllegalArgumentException(SECRET);
        IllegalStateException crash = new IllegalStateException("Cannot hash " + SECRET, root);
        CrashingContextual consumer = new CrashingContextual(SensitiveFields.all(), crash);
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer), resolved("t:crash"), signUpValues(), Set.of("password"));

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_500, error.errorCode);
        assertEquals(500, error.httpStatus());
        assertFalse(error.hasActionProgress());
        Throwable cause = assertInstanceOf(FormSubmissionPipeline.RedactedThrowable.class, error.getCause());
        assertTrue(cause.getMessage().startsWith(IllegalStateException.class.getName()), cause.getMessage());
        assertArrayEquals(crash.getStackTrace(), cause.getStackTrace());
        assertTrue(cause.getCause().getMessage().startsWith(IllegalArgumentException.class.getName()));
        assertArrayEquals(root.getStackTrace(), cause.getCause().getStackTrace());
        assertNull(cause.getCause().getCause());
        String logged = printed(error);
        assertFalse(logged.contains(SECRET), logged);
        assertTrue(logged.contains(getClass().getName()), logged);
    }

    @Test
    void theRefusalOfAnActionHandedTheSecretKeepsItsMessageAndStatusButNotTheTextOfItsCauses() throws Exception {
        // Verifies the same rule for a FormActionException: the action wrote its message and answers for it, so it
        // stays, and the 4xx/5xx status the action chose is still forwarded to the browser, as the SPI promises; the
        // causes, a library's, keep their stack and lose their text.
        IllegalStateException providerError = new IllegalStateException(SECRET);
        FormActionException refusal = new FormActionException("Rejected by the provider", 502, providerError);
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), refusal);
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer), resolved("t:consumer"), signUpValues(), Set.of("password"));

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_008, error.errorCode);
        assertEquals(502, error.httpStatus());
        assertTrue(error.getMessage().endsWith(": Rejected by the provider"), error.getMessage());
        Throwable cause = assertInstanceOf(FormSubmissionPipeline.RedactedThrowable.class, error.getCause());
        assertTrue(cause.getMessage().endsWith("Rejected by the provider"), cause.getMessage());
        assertArrayEquals(refusal.getStackTrace(), cause.getStackTrace());
        assertArrayEquals(providerError.getStackTrace(), cause.getCause().getStackTrace());
        String logged = printed(error);
        assertFalse(logged.contains(SECRET), logged);
    }

    @Test
    void aSensitiveFieldTheLogicHidesOnlyFailSafeIsStillJudgedBeforeTheConsumerRuns() throws Exception {
        // Verifies the step 11b of a password the logic hides on a fail-safe verdict: a visitor forces that verdict by
        // posting without the logic-state header, and the blocking policy still judges the value, so the submission is
        // refused with FMDB-015 and the consumer that opted in is never handed an unjudged password.
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=test");
        when(request.getParameter("fid")).thenReturn(FORM_ID);
        when(userSession.getNodeByIdentifier(FORM_ID)).thenReturn(userFormNode);
        when(userFormNode.isNodeType(FmdbMixin.FORM_ROOT)).thenReturn(true);
        List<String> judged = new ArrayList<>();
        FieldAction policy = new FieldAction() {
            @Override public String getNodeType() { return "myco:passwordPolicy"; }
            @Override public boolean consumesSensitiveValues() { return true; }
            @Override public FieldActionResult execute(JCRNodeWrapper node, FieldActionRequest fieldRequest) {
                judged.add(fieldRequest.value());
                return FieldActionResult.reject("weak");
            }
        };
        ConditionalLogicRule onConsent = new ConditionalLogicRule("logic-c", "cookie", "", "", "", "consent", "exists", null, List.of());
        Map<String, List<ResolvedFieldAction>> fieldActions = Map.of("password", List.of(new ResolvedFieldAction("policy",
                "myco:passwordPolicy", ResolvedFieldAction.Trigger.BLUR, ResolvedFieldAction.Severity.BLOCK,
                ResolvedFieldAction.Unavailable.ACCEPT, true)));
        FormFieldMetadataCollector.Result hiddenPassword = new FormFieldMetadataCollector.Result(metadata(Set.of("password")).fieldInfos(),
                Map.of("password", List.of(onConsent)), Map.of(), Map.of(), fieldActions, Set.of("password"));
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                new FormSubmissionPipeline.Themes(mock(UploadsConfigService.class), mock(CaptchaConfigService.class), fieldActionsConfig()),
                List.of(consumer),
                (formId, locale) -> hiddenPassword,
                () -> template,
                (req, uploads, md) -> new FormDataParser.ParseResult(signUpValues(), List.of()),
                locale -> userSession,
                () -> false);
        pipeline.useFieldActions(new FieldActionDispatcher(() -> List.of(policy), new VerdictCache(), () -> Duration.ZERO, () -> template));
        pipeline.useResponse(response);
        set(pipeline, "resolvedActions", resolved("t:consumer"));

        SubmissionException error = assertThrows(SubmissionException.class, () -> pipeline.run(request));

        assertEquals(ErrorCode.FMDB_015, error.errorCode);
        assertEquals(List.of(SECRET), judged);
        assertNull(consumer.received.get());
        ConditionalLogicEvaluator evaluator = (ConditionalLogicEvaluator) get(pipeline, "logicEvaluator");
        assertEquals(ConditionalLogicEvaluator.Visibility.HIDDEN_FAILSAFE, evaluator.visibility("password"));
    }

    @Test
    void aSensitiveFieldSentMoreThanOnceIsRefusedWithoutItsValues() throws Exception {
        // Verifies step 9a's guard: a password control sends one value, so a sensitive field carrying two is refused
        // with FMDB-010 before any step reads it, and the failure names the field and none of its values.
        Map<String, List<String>> twice = new LinkedHashMap<>();
        twice.put("email", List.of("ada@example.com"));
        twice.put("password", List.of(SECRET, "Other-" + SECRET));
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer), resolved("t:consumer"), twice, Set.of("password"));

        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(pipeline));

        assertEquals(ErrorCode.FMDB_010, error.errorCode);
        assertTrue(error.getMessage().contains("'password'"), error.getMessage());
        assertFalse(error.getMessage().contains(SECRET), error.getMessage());
        assertNull(consumer.received.get());
    }

    @Test
    void aBlankSecondValueOfASensitiveFieldIsNotCounted() throws Exception {
        // Verifies the guard counts answers only: two password fields of one name, one left empty, are an honest
        // submission. The consumer receives the one value the visitor typed, and the map holds no blank entry.
        Map<String, List<String>> oneBlank = new LinkedHashMap<>();
        oneBlank.put("email", List.of("ada@example.com"));
        oneBlank.put("password", List.of(SECRET, ""));
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer), resolved("t:consumer"), oneBlank, Set.of("password"));

        dispatch(pipeline);

        assertEquals(SECRET, consumer.received.get().sensitiveParameter("password"));
        assertEquals(List.of(SECRET), consumer.received.get().sensitiveParameters().get("password"));
    }

    @Test
    void aSensitiveFieldLeftBlankIsNoValueToHandOver() throws Exception {
        // Verifies the secret map holds only what the visitor typed: a password left blank is not in it at all.
        Map<String, List<String>> blank = new LinkedHashMap<>();
        blank.put("email", List.of("ada@example.com"));
        blank.put("password", List.of(""));
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer), resolved("t:consumer"), blank, Set.of("password"));

        dispatch(pipeline);

        assertTrue(consumer.received.get().sensitiveParameters().isEmpty());
    }

    @Test
    void theBuiltInActionsThatStoreMailOrForwardAreAllPlainActions() {
        // Verifies Save to JCR, the two e-mail actions and Forward can never be handed a sensitive value: only a
        // ContextualFormAction may opt in, and none of them is one, so the dispatch hands them the public map only.
        for (Class<?> action : List.of(SaveToJcrFormAction.class, SendEmailNotificationFormAction.class,
                SendEmailContentFormAction.class, ForwardSubmissionFormAction.class)) {
            assertTrue(FormAction.class.isAssignableFrom(action), action.getName());
            assertFalse(ContextualFormAction.class.isAssignableFrom(action), action.getName());
        }
    }

    @Test
    void theFailureOfAnActionNotHandedTheSecretStaysWhole() throws Exception {
        // Verifies the behaviour before the sensitive values is kept for every action that held none of them, on a
        // form with a password: a plain action's runtime exception and a non-consuming contextual action's are
        // rethrown as they are, and a FormActionException keeps its message, its own cause and its status.
        IllegalStateException plainCrash = new IllegalStateException("plain boom");
        FormSubmissionPipeline plain = pipeline(List.of(new CrashingLegacyAction(plainCrash)), resolved("t:crash"),
                signUpValues(), Set.of("password"));
        assertSame(plainCrash, assertThrows(IllegalStateException.class, () -> dispatch(plain)));

        IllegalStateException passiveCrash = new IllegalStateException("passive boom");
        FormSubmissionPipeline passive = pipeline(List.of(new CrashingContextual(SensitiveFields.none(), passiveCrash)),
                resolved("t:crash"), signUpValues(), Set.of("password"));
        assertSame(passiveCrash, assertThrows(IllegalStateException.class, () -> dispatch(passive)));

        FormActionException refusal = new FormActionException("provider down", 502);
        FormSubmissionPipeline refused = pipeline(List.of(new Contextual("t:passive", FormActionResult.none(), refusal)),
                resolved("t:passive"), signUpValues(), Set.of("password"));
        SubmissionException error = assertThrows(SubmissionException.class, () -> dispatch(refused));
        assertTrue(error.getMessage().endsWith(": provider down"), error.getMessage());
        assertSame(refusal, error.getCause());
        assertEquals(502, error.httpStatus());
    }

    @Test
    void theActionAfterAConsumerIsJudgedOnItsOwn() throws Exception {
        // Verifies the flag is the dispatched action's, not the run's: a consumer that succeeded does not make the
        // plain action after it fail opaquely, its runtime exception is rethrown as it is.
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        IllegalStateException crash = new IllegalStateException("plain boom");
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer, new CrashingLegacyAction(crash)),
                resolved("t:consumer", "t:crash"), signUpValues(), Set.of("password"));

        assertSame(crash, assertThrows(IllegalStateException.class, () -> dispatch(pipeline)));
        assertEquals(SECRET, consumer.received.get().sensitiveParameter("password"));
    }

    @Test
    void aPlainActionCanStillWriteIntoTheMapItIsHanded() throws Exception {
        // Verifies the plain action sees no change from step 9a: the map it receives is writable as before, on a form
        // without a sensitive field and on one with a password, where it holds every value but the password.
        WritingAction withoutSecret = new WritingAction();
        dispatch(pipeline(List.of(withoutSecret), resolved("t:writing"), values()));
        WritingAction withSecret = new WritingAction();
        dispatch(pipeline(List.of(withSecret), resolved("t:writing"), signUpValues(), Set.of("password")));

        assertEquals(List.of("42"), withoutSecret.received.get().get("computed"));
        assertEquals(List.of("ada@example.com"), withoutSecret.received.get().get("email"));
        assertEquals(List.of("42"), withSecret.received.get().get("computed"));
        assertEquals(List.of("ada@example.com"), withSecret.received.get().get("email"));
        assertFalse(withSecret.received.get().containsKey("password"));
    }

    @Test
    void theSensitiveValuesAConsumerIsHandedCannotBeChanged() throws Exception {
        // Verifies the other half of the split: the map of sensitive values a consumer reads is unmodifiable.
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = pipeline(List.of(consumer), resolved("t:consumer"), signUpValues(), Set.of("password"));

        dispatch(pipeline);

        Map<String, List<String>> sensitive = consumer.received.get().sensitiveParameters();
        assertEquals(Map.of("password", List.of(SECRET)), sensitive);
        List<String> pin = List.of("1234");
        List<String> password = sensitive.get("password");
        assertThrows(UnsupportedOperationException.class, () -> sensitive.put("pin", pin));
        assertThrows(UnsupportedOperationException.class, password::clear);
    }

    @Test
    void noRequestAttributeHoldsTheParsedValuesAfterAWholeRun() throws Exception {
        // Verifies open point 3: the request every action is handed cannot be a way round step 9a. Step 9 consumed the
        // body, and nothing of the pipeline puts the parsed values back on the request as an attribute — a whole run,
        // from the multipart check to the dispatch, sets none, while the consumer still receives the password.
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=test");
        when(request.getParameter("fid")).thenReturn(FORM_ID);
        when(request.getParameter("lang")).thenReturn("fr");
        when(userSession.getNodeByIdentifier(FORM_ID)).thenReturn(userFormNode);
        when(userFormNode.isNodeType(org.jahia.modules.formidable.engine.api.FmdbMixin.FORM_ROOT)).thenReturn(true);
        LegacyAction legacy = new LegacyAction();
        Contextual consumer = new Contextual("t:consumer", SensitiveFields.all(), FormActionResult.none(), null);
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(
                new FormSubmissionPipeline.Themes(mock(UploadsConfigService.class), mock(CaptchaConfigService.class), mock(FieldActionsConfigService.class)),
                List.of(legacy, consumer),
                (formId, locale) -> metadata(Set.of("password")),
                () -> template,
                (req, uploads, md) -> new FormDataParser.ParseResult(signUpValues(), List.of()),
                locale -> userSession,
                () -> false);
        pipeline.useResponse(response);
        set(pipeline, "resolvedActions", resolved("t:legacy", "t:consumer"));

        pipeline.run(request);

        org.mockito.Mockito.verify(request, org.mockito.Mockito.never()).setAttribute(any(), any());
        assertSame(request, consumer.received.get().request());
        assertEquals(SECRET, consumer.received.get().sensitiveParameter("password"));
        assertFalse(legacy.received.get().containsKey("password"));
    }

    @Test
    void theContextNeverPrintsAValue() {
        // Verifies the log hygiene of the context: a log line that prints it names the fields, never their content.
        FormActionContext context = new FormActionContext(actionNode, null, request, response, userSession, Locale.ENGLISH,
                "mysite", Map.of("email", List.of("ada@example.com")), Map.of("password", List.of(SECRET)), List.of());

        String printed = context.toString();

        assertFalse(printed.contains("ada@example.com"));
        assertFalse(printed.contains(SECRET));
        assertTrue(printed.contains("email"));
        assertTrue(printed.contains("password"));
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

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /** The field-action settings step 11b reads; only the cap on the values per field matters. */
    private static FieldActionsConfigService fieldActionsConfig() {
        FieldActionsConfigService config = mock(FieldActionsConfigService.class);
        when(config.getFieldActionSettings()).thenReturn(new FieldActionSettings(false, Duration.ofSeconds(5),
                Duration.ofSeconds(10), HttpClient.newHttpClient(), Duration.ZERO, 30, 512, 20));
        return config;
    }

    /** The failure as the servlet's log line prints it: its message and its stack, causes included. */
    private static String printed(Throwable failure) {
        StringWriter out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
