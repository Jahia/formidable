package org.jahia.test.modules.formidable.samples.actions.form;

import org.jahia.modules.formidable.engine.api.FormActionContext;
import org.jahia.modules.formidable.engine.api.FormActionResult;
import org.jahia.modules.formidable.engine.api.SensitiveFields;
import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The two sample actions the end-to-end suite reads back through response headers: what each declares, and that what
 * each reports is a count, a yes or no, or a field name, never a value.
 */
class SensitiveSampleFormActionsTest {

    private static final String PASSWORD = "Sup3r-Secret!";

    private static FormActionContext context(HttpServletResponse response, Map<String, List<String>> sensitive) {
        return new FormActionContext(mock(JCRNodeWrapper.class), null, mock(HttpServletRequest.class), response, null,
                Locale.ENGLISH, "mysite", Map.of("username", List.of("ada"), "email", List.of("ada@example.com")),
                sensitive, List.of());
    }

    private static void assertNoHeaderHolds(HttpServletResponse response, String value) {
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(response, atLeastOnce()).setHeader(anyString(), values.capture());
        values.getAllValues().forEach(header -> assertFalse(header.contains(value), header));
    }

    @Test
    void theConsumerOptsInToEverySensitiveFieldAndReportsACountAndThatItReceivedThePassword() {
        // Verifies the opt-in the suite relies on (all the sensitive fields, whatever the node) and the observable it
        // reads: the number of sensitive fields and whether the password held a value, never the password.
        SensitiveConsumerFormAction action = new SensitiveConsumerFormAction();
        assertSame(SensitiveFields.all(), action.sensitiveFieldsConsumed(mock(JCRNodeWrapper.class)));

        HttpServletResponse response = mock(HttpServletResponse.class);
        FormActionResult result = action.execute(context(response, Map.of("password", List.of(PASSWORD))));

        assertEquals(FormActionResult.none(), result);
        verify(response).setHeader(SensitiveConsumerFormAction.SENSITIVE_FIELDS_HEADER, "1");
        verify(response).setHeader(SensitiveConsumerFormAction.PASSWORD_RECEIVED_HEADER, "true");
        assertNoHeaderHolds(response, PASSWORD);
    }

    @Test
    void theConsumerSaysNotReceivedWhenThePasswordIsBlank() {
        // Verifies the consumer's own guard: a blank value is not a received password. The pipeline hands over no blank
        // value, so the suite never sees this case; the consumer does not rely on it.
        HttpServletResponse response = mock(HttpServletResponse.class);
        new SensitiveConsumerFormAction().execute(context(response, Map.of("password", List.of(" "))));

        verify(response).setHeader(SensitiveConsumerFormAction.SENSITIVE_FIELDS_HEADER, "1");
        verify(response).setHeader(SensitiveConsumerFormAction.PASSWORD_RECEIVED_HEADER, "false");
    }

    @Test
    void theConsumerSaysNotReceivedWhenNoPasswordReachedIt() {
        // Verifies the headers when the pipeline handed nothing: no field counted, and no password received. The count
        // keeps an empty password and no password at all apart.
        HttpServletResponse response = mock(HttpServletResponse.class);
        new SensitiveConsumerFormAction().execute(context(response, Map.of()));

        verify(response).setHeader(SensitiveConsumerFormAction.SENSITIVE_FIELDS_HEADER, "0");
        verify(response).setHeader(SensitiveConsumerFormAction.PASSWORD_RECEIVED_HEADER, "false");
    }

    @Test
    void theNonConsumerKeepsTheDefaultAndReportsTheNamesOfThePublicFields() {
        // Verifies that the sample relies on the interface's default (it consumes nothing) and that its second header
        // carries field names, sorted, never a value.
        SensitiveNonConsumerFormAction action = new SensitiveNonConsumerFormAction();
        assertSame(SensitiveFields.none(), action.sensitiveFieldsConsumed(mock(JCRNodeWrapper.class)));

        HttpServletResponse response = mock(HttpServletResponse.class);
        action.execute(context(response, Map.of()));

        verify(response).setHeader(SensitiveNonConsumerFormAction.SENSITIVE_FIELDS_HEADER, "0");
        verify(response).setHeader(SensitiveNonConsumerFormAction.FIELDS_HEADER, "email,username");
        assertNoHeaderHolds(response, "ada");
    }
}
