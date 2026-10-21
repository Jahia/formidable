package org.jahia.modules.formidable.engine.servlet;

import org.jahia.modules.formidable.engine.api.AcceptedSubmission;
import org.jahia.modules.formidable.engine.api.FormAction;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.config.captcha.CaptchaConfigService;
import org.jahia.modules.formidable.engine.config.fieldactions.FieldActionsConfigService;
import org.jahia.modules.formidable.engine.config.uploads.UploadsConfigService;
import org.jahia.modules.formidable.engine.options.FormidableOptionsSourceService;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the response carries of a contextual action: {@code redirect}, {@code userMessage}, and the keys no enricher may take. */
class FormSubmitServletBrowserInstructionTest {

    private static FormSubmitServlet servlet(SubmissionException rejection, FormActionResult instruction) {
        FormSubmitServlet servlet = new FormSubmitServlet() {
            @Override
            boolean isRequestAllowed() {
                return true;
            }

            @Override
            FormSubmissionPipeline createPipeline() {
                return new FormSubmissionPipeline(new FormSubmissionPipeline.Themes(mock(UploadsConfigService.class), mock(CaptchaConfigService.class), mock(FieldActionsConfigService.class)), List.<FormAction>of(),
                        mock(FormidableOptionsSourceService.class), () -> false) {
                    @Override
                    void run(HttpServletRequest req) throws SubmissionException {
                        if (rejection != null) {
                            throw rejection;
                        }
                    }

                    @Override
                    AcceptedSubmission accepted() {
                        return new AcceptedSubmission(mock(JCRNodeWrapper.class), "mysite", Locale.ENGLISH, Map.of());
                    }

                    @Override
                    FormActionResult browserInstruction() {
                        return instruction;
                    }
                };
            }
        };
        servlet.setUploadsConfig(mock(UploadsConfigService.class));
        servlet.setCaptchaConfig(mock(CaptchaConfigService.class));
        servlet.setFieldActionsConfig(mock(FieldActionsConfigService.class));
        return servlet;
    }

    private record Answer(int status, JSONObject body) {
    }

    private static Answer post(FormSubmitServlet servlet) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        servlet.doPost(mock(HttpServletRequest.class), response);
        ArgumentCaptor<Integer> status = ArgumentCaptor.forClass(Integer.class);
        verify(response, atLeastOnce()).setStatus(status.capture());
        return new Answer(status.getValue(), new JSONObject(out.toString()));
    }

    @Test
    void anAcceptedSubmissionCarriesTheRedirectAndTheEscapedMessage() throws Exception {
        // Verifies the success body: the redirect as the pipeline validated it, and the action's plain message escaped
        // here, since the browser renders userMessage as HTML like every other message of the island.
        Answer answer = post(servlet(null, new FormActionResult("/sites/mysite/account.html", "Bienvenue <b>Ada</b> & merci")));

        assertEquals(200, answer.status());
        assertTrue(answer.body().getBoolean("success"));
        assertEquals("/sites/mysite/account.html", answer.body().getString("redirect"));
        assertEquals("Bienvenue &lt;b&gt;Ada&lt;/b&gt; &amp; merci", answer.body().getString("userMessage"));
    }

    @Test
    void anActionThatSaidNothingAddsNothing() throws Exception {
        // Verifies backward compatibility: no instruction, a body identical to the one before the SPI existed.
        Answer answer = post(servlet(null, FormActionResult.none()));

        assertEquals(200, answer.status());
        assertFalse(answer.body().has("redirect"));
        assertFalse(answer.body().has("userMessage"));
    }

    @Test
    void anEnricherCannotTakeRedirectNorUserMessage() throws Exception {
        // Verifies the reservation: a response enricher (a third-party module) must not be able to send the visitor
        // elsewhere, nor to put its own text where the action's belongs.
        FormSubmitServlet servlet = servlet(null, FormActionResult.none());
        servlet.bindResponseEnricher(submission -> Map.of("redirect", "https://evil.example", "userMessage", "pwned", "mine", "kept"));

        Answer answer = post(servlet);

        assertFalse(answer.body().has("redirect"));
        assertFalse(answer.body().has("userMessage"));
        assertEquals("kept", answer.body().getString("mine"));
    }

    @Test
    void aRejectionCarriesNeitherKey() throws Exception {
        // Verifies the instruction belongs to an accepted submission only: a rejected one keeps the visitor on the form.
        Answer answer = post(servlet(new SubmissionException(ErrorCode.FMDB_010, "bad format"),
                new FormActionResult("/sites/mysite/account.html", "Bienvenue")));

        assertFalse(answer.body().getBoolean("success"));
        assertFalse(answer.body().has("redirect"));
        assertFalse(answer.body().has("userMessage"));
    }

    @Test
    void theReservedKeysIncludeTheTwoNewOnes() {
        assertTrue(FormSubmitServlet.RESERVED_KEYS.containsAll(java.util.Set.of("redirect", "userMessage")));
    }
}
