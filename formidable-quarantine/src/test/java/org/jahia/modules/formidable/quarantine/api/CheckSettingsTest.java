package org.jahia.modules.formidable.quarantine.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckSettingsTest {

    @Test
    void anAbsentSettingTakesTheDefaultThatFailsClosed() {
        assertEquals(OnInconclusive.REVIEW, OnInconclusive.fromValue(null));
        assertEquals(OnInconclusive.REVIEW, OnInconclusive.fromValue(" "));
        assertEquals(OnUnavailable.RETRY, OnUnavailable.fromValue(null));
        assertEquals(OnUnavailable.RETRY, OnUnavailable.fromValue(""));
    }

    @Test
    void eachStoredValueReadsAsItsSetting() {
        assertEquals(OnInconclusive.REVIEW, OnInconclusive.fromValue("review"));
        assertEquals(OnInconclusive.REJECT, OnInconclusive.fromValue("reject"));
        assertEquals(OnUnavailable.RETRY, OnUnavailable.fromValue("retry"));
        assertEquals(OnUnavailable.REJECT, OnUnavailable.fromValue("reject"));
        assertEquals("review", OnInconclusive.REVIEW.value());
        assertEquals("retry", OnUnavailable.RETRY.value());
    }

    @Test
    void noSettingAcceptsAFile() {
        assertThrows(IllegalArgumentException.class, () -> OnInconclusive.fromValue("accept"));
        assertThrows(IllegalArgumentException.class, () -> OnUnavailable.fromValue("accept"));
    }

    @Test
    void theDefaultsAreReviewAndRetry() {
        CheckSettings settings = CheckSettings.withDefaults("fmdbquar:remoteStubCheck", "scan");
        assertEquals(OnInconclusive.REVIEW, settings.onInconclusive());
        assertEquals(OnUnavailable.RETRY, settings.onUnavailable());
    }

    @Test
    void aSettingIsNeverNull() {
        assertThrows(NullPointerException.class,
                () -> new CheckSettings("fmdbquar:remoteStubCheck", "scan", null, OnUnavailable.RETRY));
        assertThrows(NullPointerException.class,
                () -> new CheckSettings("fmdbquar:remoteStubCheck", null, OnInconclusive.REVIEW, OnUnavailable.RETRY));
    }

    @Test
    void settingsWithNoPropertyHoldAnEmptyMap() {
        assertEquals(Map.of(), CheckSettings.withDefaults("acme:check", "scan").properties());
        assertEquals(Map.of(), new CheckSettings("acme:check", "scan", OnInconclusive.REVIEW, OnUnavailable.RETRY,
                null).properties());
    }

    @Test
    void thePropertiesAreACopyThatNobodyChanges() {
        Map<String, List<String>> source = new LinkedHashMap<>();
        List<String> extensions = new ArrayList<>(List.of("pdf", "png"));
        source.put("extensions", extensions);
        source.put("pattern", null);
        CheckSettings settings = new CheckSettings("acme:check", "scan", OnInconclusive.REVIEW, OnUnavailable.RETRY,
                source);
        source.put("mimeTypes", List.of("application/pdf"));
        extensions.add("exe");

        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("extensions", List.of("pdf", "png"));
        expected.put("pattern", List.of());
        assertEquals(expected, settings.properties());
        Map<String, List<String>> properties = settings.properties();
        List<String> values = properties.get("extensions");
        List<String> mimeTypes = List.of("text/plain");
        assertThrows(UnsupportedOperationException.class, () -> properties.put("mimeTypes", mimeTypes));
        assertThrows(UnsupportedOperationException.class, () -> values.add("exe"));
    }

    @Test
    void anAnswerAtOnceIsNeverPending() {
        assertThrows(IllegalArgumentException.class, () -> Submitted.answered(Outcome.PENDING));
        Submitted answered = Submitted.answered(Outcome.pass("ok", null));
        assertInstanceOf(Submitted.Answered.class, answered);
        assertEquals("t-1", Submitted.ticket("t-1").id());
    }
}
