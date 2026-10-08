package org.jahia.modules.formidable.engine.imports.jahiaforms;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormsValuesTest {

    /** Forms stored the local midnight of the visitor as a UTC instant: the chosen day minus the offset. */
    private static String storedBy(LocalDate chosen, int offsetHours) {
        return chosen.atStartOfDay().atOffset(ZoneOffset.ofHours(offsetHours)).toInstant().toString();
    }

    @Test
    void theDateRuleGivesBackTheChosenDayFromMinusElevenToPlusTwelve() {
        LocalDate chosen = LocalDate.of(2024, 8, 13);
        for (int offset : new int[] {-11, -4, 0, 2, 9, 11, 12}) {
            assertEquals(Optional.of("2024-08-13"), FormsValues.date(storedBy(chosen, offset)), "offset " + offset);
        }
        // the Paris example of the spec
        assertEquals(Optional.of("2024-08-13"), FormsValues.date("2024-08-12T22:00:00.000Z"));
    }

    @Test
    void theDateRuleIsOneDayEarlyAtPlusThirteenAsDocumented() {
        assertEquals(Optional.of("2024-08-12"), FormsValues.date(storedBy(LocalDate.of(2024, 8, 13), 13)));
    }

    @Test
    void aValueThatIsNoInstantIsKeptAsStoredAndNoted() {
        FormsValues.Converted converted = FormsValues.convert("datePicker", List.of("yesterday"), false);
        assertEquals(List.of("yesterday"), converted.values());
        assertNotNull(converted.note());
    }

    @Test
    void aCountryGivesItsCode() {
        assertEquals(Optional.of("FR"), FormsValues.country("{\"country\":{\"key\":\"FR\",\"value\":\"France\"},\"rendererName\":\"country\"}"));
        assertTrue(FormsValues.country("{\"rendererName\":\"country\"}").isEmpty());
        assertTrue(FormsValues.country("FR").isEmpty());
    }

    @Test
    void aRatingGivesItsNumber() {
        assertEquals(Optional.of("4"), FormsValues.rating("{\"rating\":4,\"rendererName\":\"rating\"}"));
        assertEquals(Optional.of("3"), FormsValues.rating("3"));
        assertTrue(FormsValues.rating("{\"rendererName\":\"rating\"}").isEmpty());
    }

    @Test
    void aMatrixGivesOneLinePerRow() {
        String radios = "{\"matrixRadios\":{\"Price\":\"Good\",\"Service\":\"Poor\"},\"rendererName\":\"matrixRadios\"}";
        assertEquals(Optional.of("Price: Good\nService: Poor"), FormsValues.matrix(radios));
        String checkboxes = "{\"matrixCheckboxes\":{\"Price\":[\"Good\",\"Fair\"]},\"rendererName\":\"matrixCheckboxes\"}";
        assertEquals(Optional.of("Price: Good, Fair"), FormsValues.matrix(checkboxes));
        assertTrue(FormsValues.matrix("{\"rendererName\":\"matrixRadios\"}").isEmpty());
    }

    @Test
    void theRowsOfAMatrixKeepTheOrderOfTheText() {
        // a JSONObject iterates its keys by hash: row1, Zeta, row10, Alpha, row2
        String json = "{\"matrixRadios\":{\"row1\":\"a\",\"row2\":\"b\",\"row10\":\"c\",\"Zeta\":\"d\",\"Alpha\":\"e\"},\"rendererName\":\"matrixRadios\"}";
        assertEquals(Optional.of("row1: a\nrow2: b\nrow10: c\nZeta: d\nAlpha: e"), FormsValues.matrix(json));
    }

    @Test
    void aPasswordIsDroppedAndAFileJsonToo() {
        FormsValues.Converted password = FormsValues.convert("password", List.of(FormsValues.PASSWORD_PLACEHOLDER), false);
        assertTrue(password.values().isEmpty());
        assertEquals("password dropped", password.note());

        FormsValues.Converted file = FormsValues.convert("fileUpload", List.of("{\"url\":[],\"rendererName\":\"fileUpload\"}"), false);
        assertTrue(file.values().isEmpty());
        assertNull(file.note());
    }

    @Test
    void aConsentIsTrueAndACheckboxKeepsTheAcceptedValue() {
        assertEquals(List.of("true"), FormsValues.convert("acceptTermCheckbox", List.of("accepted"), true).values());
        assertEquals(List.of("accepted"), FormsValues.convert("acceptTermCheckbox", List.of("accepted"), false).values());
    }

    @Test
    void everythingElseIsUnchanged() {
        assertEquals(List.of("a", "b"), FormsValues.convert("multipleCheckBoxes", List.of("a", "b"), false).values());
        assertEquals(List.of("Jane"), FormsValues.convert("input", List.of("Jane"), false).values());
        assertEquals(List.of("x"), FormsValues.convert(null, List.of("x"), false).values());
    }
}
