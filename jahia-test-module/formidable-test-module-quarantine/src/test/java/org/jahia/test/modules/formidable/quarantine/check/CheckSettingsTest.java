package org.jahia.test.modules.formidable.quarantine.check;

import org.junit.jupiter.api.Test;

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
    void anAnswerAtOnceIsNeverPending() {
        assertThrows(IllegalArgumentException.class, () -> Submitted.answered(Outcome.PENDING));
        Submitted answered = Submitted.answered(Outcome.pass("ok", null));
        assertInstanceOf(Submitted.Answered.class, answered);
        assertEquals("t-1", Submitted.ticket("t-1").id());
    }
}
