package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.actions.FormDataParser;
import org.jahia.modules.formidable.engine.api.FieldAction;
import org.jahia.modules.formidable.engine.api.FieldActionRequest;
import org.jahia.modules.formidable.engine.api.FieldActionResult;
import org.jahia.modules.formidable.engine.api.FmdbProperty;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.config.FormidableConfigService;
import org.jahia.modules.formidable.engine.fieldactions.FieldActionDispatcher;
import org.jahia.modules.formidable.engine.fieldactions.ResolvedFieldAction;
import org.jahia.modules.formidable.engine.fieldactions.ResolvedFieldAction.Severity;
import org.jahia.modules.formidable.engine.fieldactions.ResolvedFieldAction.Trigger;
import org.jahia.modules.formidable.engine.fieldactions.ResolvedFieldAction.Unavailable;
import org.jahia.modules.formidable.engine.fieldactions.VerdictCache;
import org.jahia.modules.formidable.engine.logic.ConditionalLogicEvaluator;
import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step 11b of the pipeline, on its own: the blocking field actions run again server-side, on the fields the visitor
 * answered and the logic shows, and a refusal is FMDB-015 with the messages the response carries.
 */
class FormSubmissionPipelineFieldActionsTest {

    private static final String TYPE = "myco:crmLookupAction";

    /** A dispatcher over one Java action for the type, counting its calls, its node carrying the given message. */
    private static FieldActionDispatcher dispatcher(FieldActionResult result, AtomicInteger calls) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(property.getString()).thenReturn("We do not know ${value}");
        when(node.hasProperty(FmdbProperty.REJECTION_MESSAGE)).thenReturn(true);
        when(node.getProperty(FmdbProperty.REJECTION_MESSAGE)).thenReturn(property);
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNodeByIdentifier(any())).thenReturn(node);
        JCRTemplate template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(session));
        FieldAction action = new FieldAction() {
            @Override
            public String getNodeType() {
                return TYPE;
            }

            @Override
            public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
                calls.incrementAndGet();
                return result;
            }
        };
        return new FieldActionDispatcher(() -> List.of(action), new VerdictCache(), () -> Duration.ZERO, () -> template);
    }

    /** A pipeline past step 11, with the given field actions, submitted values and logic verdicts. */
    private static FormSubmissionPipeline pipelineAtStep11b(FieldActionDispatcher dispatcher,
                                                            Map<String, List<ResolvedFieldAction>> fieldActions,
                                                            Map<String, List<String>> parameters,
                                                            ConditionalLogicEvaluator evaluator) throws Exception {
        FormSubmissionPipeline pipeline = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of(),
                mock(FormidableOptionsSourceService.class), () -> false);
        pipeline.useFieldActions(dispatcher);
        set(pipeline, "formId", "8f7e2a10-0000-4000-8000-000000000001");
        set(pipeline, "locale", Locale.ENGLISH);
        set(pipeline, "fieldMetadata", new FormFieldMetadataCollector.Result(Map.of(), Map.of(), Map.of(), Map.of(), fieldActions));
        set(pipeline, "parsed", new FormDataParser.ParseResult(parameters, List.of()));
        set(pipeline, "logicEvaluator", evaluator);
        return pipeline;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void runFieldActions(FormSubmissionPipeline pipeline) throws Exception {
        Method method = FormSubmissionPipeline.class.getDeclaredMethod("runFieldActions", HttpServletRequest.class, HttpServletResponse.class);
        method.setAccessible(true);
        try {
            method.invoke(pipeline, mock(HttpServletRequest.class), mock(HttpServletResponse.class));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static ResolvedFieldAction action(String id, Severity severity) {
        return new ResolvedFieldAction(id, TYPE, Trigger.BLUR, severity, Unavailable.ACCEPT);
    }

    private static ConditionalLogicEvaluator allVisible() {
        return mock(ConditionalLogicEvaluator.class);
    }

    @Test
    void aBlockingRefusalRejectsTheSubmissionWithItsMessagesAnchoredOnTheField() throws Exception {
        // Verifies the authority: the browser may have skipped the pre-check, the blocking action runs here and its
        // refusal is FMDB-015 carrying the contributor's message for the browser to anchor — value interpolated,
        // escaped — with no action progress, since no form action ran.
        AtomicInteger calls = new AtomicInteger();
        FormSubmissionPipeline pipeline = pipelineAtStep11b(dispatcher(FieldActionResult.reject("unknown"), calls),
                Map.of("email", List.of(action("a1", Severity.BLOCK))),
                Map.of("email", List.of("<ada>@example.com"), "name", List.of("Ada")),
                allVisible());

        SubmissionException error = assertThrows(SubmissionException.class, () -> runFieldActions(pipeline));

        assertEquals(ErrorCode.FMDB_015, error.errorCode);
        assertEquals(422, error.httpStatus());
        assertEquals(1, error.messages().size());
        assertEquals("email", error.messages().get(0).field());
        assertEquals("We do not know &lt;ada&gt;@example.com", error.messages().get(0).html());
        assertFalse(error.hasActionProgress());
        assertEquals(1, calls.get());
    }

    @Test
    void anAcceptedValueAHiddenFieldAndAnUnansweredFieldLetTheSubmissionThrough() throws Exception {
        // Verifies the three ways past the step: the action accepts; the logic hides the field, so it is not
        // judged at all; the visitor left the field blank, so there is nothing to judge — the required check's rule.
        AtomicInteger accepting = new AtomicInteger();
        runFieldActions(pipelineAtStep11b(dispatcher(FieldActionResult.accept(), accepting),
                Map.of("email", List.of(action("a1", Severity.BLOCK))), Map.of("email", List.of("ada@example.com")), allVisible()));
        assertEquals(1, accepting.get());

        AtomicInteger hidden = new AtomicInteger();
        ConditionalLogicEvaluator hiding = mock(ConditionalLogicEvaluator.class);
        when(hiding.isHidden("email")).thenReturn(true);
        runFieldActions(pipelineAtStep11b(dispatcher(FieldActionResult.reject("x"), hidden),
                Map.of("email", List.of(action("a1", Severity.BLOCK))), Map.of("email", List.of("ada@example.com")), hiding));
        assertEquals(0, hidden.get());

        AtomicInteger unanswered = new AtomicInteger();
        runFieldActions(pipelineAtStep11b(dispatcher(FieldActionResult.reject("x"), unanswered),
                Map.of("email", List.of(action("a1", Severity.BLOCK))), Map.of("email", List.of("", "  ")), allVisible()));
        assertEquals(0, unanswered.get());
    }

    @Test
    void aWarningActionDoesNotRunAgainAtSubmission() throws Exception {
        // Verifies the blockingOnly rule at the pipeline's seat: an action that only warns had its say at the
        // pre-check; here it is neither run nor reported.
        AtomicInteger calls = new AtomicInteger();
        runFieldActions(pipelineAtStep11b(dispatcher(FieldActionResult.reject("x"), calls),
                Map.of("email", List.of(action("w1", Severity.WARN))), Map.of("email", List.of("ada@example.com")), allVisible()));

        assertEquals(0, calls.get());
    }

    @Test
    void withoutADispatcherOrWithoutFieldActionsTheStepIsANoOp() throws Exception {
        // Verifies the two idle cases: a pipeline built without the field-action runtime (the tests' 4-arg
        // constructor, an instance without the component — the step then warns that the checks did not run), and a
        // form whose fields declare no action.
        FormSubmissionPipeline noDispatcher = new FormSubmissionPipeline(mock(FormidableConfigService.class), List.<FormAction>of(),
                mock(FormidableOptionsSourceService.class), () -> false);
        set(noDispatcher, "fieldMetadata", new FormFieldMetadataCollector.Result(Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("email", List.of(action("a1", Severity.BLOCK)))));
        set(noDispatcher, "parsed", new FormDataParser.ParseResult(Map.of("email", List.of("x")), List.of()));
        runFieldActions(noDispatcher);

        AtomicInteger calls = new AtomicInteger();
        runFieldActions(pipelineAtStep11b(dispatcher(FieldActionResult.reject("x"), calls), Map.of(),
                Map.of("email", List.of("ada@example.com")), allVisible()));
        assertEquals(0, calls.get());
    }

    @Test
    void everyNonBlankValueOfAMultiValuedFieldIsJudgedAndTheRefusedOneFailsTheSubmission() throws Exception {
        // Verifies the authority over the whole answer: a checkbox group, or two fields sharing a name, submits several
        // values and every one of them is stored and sent on — so every non-blank one is judged, in order, and a
        // refusal of the second one is FMDB-015 as the first one's would be. The blank one in between is not judged.
        AtomicInteger calls = new AtomicInteger();
        FieldAction refusingSpam = new FieldAction() {
            @Override
            public String getNodeType() {
                return TYPE;
            }

            @Override
            public FieldActionResult execute(JCRNodeWrapper actionNode, FieldActionRequest request) {
                calls.incrementAndGet();
                return "spam".equals(request.value()) ? FieldActionResult.reject("blocked word") : FieldActionResult.accept();
            }
        };
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        when(session.getNodeByIdentifier(any())).thenReturn(node);
        JCRTemplate template = mock(JCRTemplate.class);
        when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                .thenAnswer(call -> ((JCRCallback<?>) call.getArgument(3)).doInJCR(session));
        FieldActionDispatcher dispatcher = new FieldActionDispatcher(() -> List.of(refusingSpam), new VerdictCache(), () -> Duration.ZERO, () -> template);
        FormSubmissionPipeline pipeline = pipelineAtStep11b(dispatcher,
                Map.of("topics", List.of(action("a1", Severity.BLOCK))),
                Map.of("topics", List.of("sports", "  ", "spam", "music")),
                allVisible());

        SubmissionException error = assertThrows(SubmissionException.class, () -> runFieldActions(pipeline));

        assertEquals(ErrorCode.FMDB_015, error.errorCode);
        assertEquals("topics", error.messages().get(0).field());
        assertEquals(2, calls.get(), "sports accepted, the blank skipped, spam refused, music never reached");
    }
}
