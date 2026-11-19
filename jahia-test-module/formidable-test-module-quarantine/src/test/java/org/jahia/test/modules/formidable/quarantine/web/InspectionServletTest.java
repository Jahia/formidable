package org.jahia.test.modules.formidable.quarantine.web;

import org.jahia.modules.formidable.engine.api.HeldSubmission;
import org.jahia.modules.formidable.engine.api.SubmissionOrigin;
import org.jahia.modules.formidable.engine.api.SubmittedFile;
import org.jahia.test.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.test.modules.formidable.quarantine.store.HeldStates;
import org.jahia.test.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.test.modules.formidable.quarantine.store.QuarantineStoreException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The inspection answers a server administrator alone, and answers names, states, sizes and hashes: never a value,
 * never a sensitive value, never a byte.
 */
class InspectionServletTest {

    private static final String SITE = "formidableTestSite";
    private static final String FORM_ID = "6a1f0c3e-0000-4000-8000-000000000001";
    private static final String SECRET = "s3cr3t-Pa55word";
    private static final String PUBLIC_VALUE = "Ada Lovelace";
    private static final String FILE_TEXT = "VERDICT:PASS a note attached to the claim";
    private static final String HELD_PATH = "/sites/" + SITE + "/formidable-quarantine/" + FORM_ID + "/2026/11/19/held-1";

    private QuarantineStore store;

    /** One call of the servlet, answered into a status and a body. */
    private record Call(int status, String body) {
    }

    @BeforeEach
    void setUp() throws Exception {
        store = mock(QuarantineStore.class);
        HeldRecord record = new HeldRecord(HELD_PATH, FORM_ID, SITE, HeldStates.HELD,
                Instant.parse("2026-11-19T18:30:00Z"), 0, null, 0, List.of("fullName"), List.of("password"), true,
                List.of(new ItemRecord("item0", "supportingDocument", "claim-note.txt", "text/plain", 41,
                        "f7c11b5f5115eb9d0f4f8619af8870daf212b37948739f8534e4088a078e7ffd", true)));
        when(store.list(SITE, FORM_ID)).thenReturn(List.of(record));
        // what a read would answer: the servlet must never ask for it
        HeldSubmission held = new HeldSubmission(FORM_ID, SITE, Locale.ENGLISH, Map.of("fullName", List.of(PUBLIC_VALUE)),
                Map.of("password", List.of(SECRET)),
                List.of(new SubmittedFile("supportingDocument", "claim-note.txt", "text/plain",
                        FILE_TEXT.getBytes(StandardCharsets.UTF_8))),
                new SubmissionOrigin(null, null, null));
        when(store.read(anyString())).thenReturn(Optional.of(held));
    }

    private Call get(boolean administrator, String site, String form) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(InspectionServlet.SITE_PARAMETER)).thenReturn(site);
        when(request.getParameter(InspectionServlet.FORM_PARAMETER)).thenReturn(form);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out, true));
        int[] status = new int[1];
        doAnswer(call -> {
            status[0] = call.getArgument(0);
            return null;
        }).when(response).setStatus(anyInt());
        InspectionServlet servlet = new InspectionServlet(() -> administrator);
        servlet.setStore(store);

        servlet.doGet(request, response);

        return new Call(status[0], out.toString());
    }

    @Test
    void aCallerWhoIsNotAServerAdministratorIsRefusedAndReadsNothing() throws Exception {
        Call call = get(false, SITE, FORM_ID);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, call.status());
        assertFalse(call.body().contains(HELD_PATH), call.body());
        assertFalse(call.body().contains("claim-note.txt"), call.body());
        verify(store, never()).list(anyString(), anyString());
        verify(store, never()).read(any());
    }

    @Test
    void aServerAdministratorReadsTheStateTheFieldNamesAndTheItems() throws Exception {
        Call call = get(true, SITE, FORM_ID);

        assertEquals(HttpServletResponse.SC_OK, call.status());
        JSONObject body = new JSONObject(call.body());
        JSONObject held = body.getJSONArray("submissions").getJSONObject(0);
        assertEquals(HELD_PATH, held.getString("path"));
        assertEquals("held", held.getString("state"));
        assertEquals("fullName", held.getJSONArray("fields").getString(0));
        assertEquals("password", held.getJSONArray("sensitiveFields").getString(0));
        assertTrue(held.getBoolean("valuesPresent"));
        JSONObject item = held.getJSONArray("items").getJSONObject(0);
        assertEquals(Set.of("name", "fieldName", "fileName", "mimeType", "size", "sha256", "binaryPresent"), item.keySet());
        assertEquals("claim-note.txt", item.getString("fileName"));
        assertEquals(41, item.getLong("size"));
        assertTrue(item.getBoolean("binaryPresent"));
    }

    @Test
    void theAnswerHoldsNoValueNoSensitiveValueAndNoByte() throws Exception {
        Call call = get(true, SITE, FORM_ID);

        assertFalse(call.body().contains(SECRET), call.body());
        assertFalse(call.body().contains(PUBLIC_VALUE), call.body());
        assertFalse(call.body().contains("VERDICT"), call.body());
        verify(store, never()).read(any());
    }

    @Test
    void aTreeThatCannotBeReadAnswersAnErrorAndNoRecord() throws Exception {
        when(store.list(SITE, FORM_ID)).thenThrow(new QuarantineStoreException("the repository is down"));

        Call call = get(true, SITE, FORM_ID);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, call.status());
        assertEquals(Set.of("error"), new JSONObject(call.body()).keySet());
        assertFalse(call.body().contains("repository is down"), call.body());
    }

    @Test
    void anItemWithNoFieldNameNorFileNameKeepsBothKeysAsNull() {
        HeldRecord record = new HeldRecord(HELD_PATH, FORM_ID, SITE, HeldStates.HELD,
                Instant.parse("2026-11-19T18:30:00Z"), 0, null, 0, List.of(), List.of(), true,
                List.of(new ItemRecord("item0", null, null, null, 0, "e3b0", true)));

        JSONObject item = InspectionServlet.inspection(SITE, FORM_ID, List.of(record))
                .getJSONArray("submissions").getJSONObject(0).getJSONArray("items").getJSONObject(0);

        assertEquals(Set.of("name", "fieldName", "fileName", "mimeType", "size", "sha256", "binaryPresent"), item.keySet());
        assertTrue(item.isNull("fieldName"));
        assertTrue(item.isNull("fileName"));
    }

    @Test
    void aParameterThatIsMissingOrNamesAPathIsRefused() throws Exception {
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, get(true, SITE, null).status());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, get(true, "../users", FORM_ID).status());
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, get(true, SITE, FORM_ID + "/2026").status());
        verify(store, never()).list(anyString(), anyString());
    }
}
