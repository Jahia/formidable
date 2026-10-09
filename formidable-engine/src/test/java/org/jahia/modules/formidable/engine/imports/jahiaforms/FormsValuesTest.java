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
    void aDateWithAnOffsetAndABareDayAreAccepted() {
        assertEquals(Optional.of("2024-08-13"), FormsValues.date("2024-08-13T00:00:00.000+02:00"));
        assertEquals(Optional.of("2024-08-13"), FormsValues.date("2024-08-13"));
    }

    @Test
    void aValueThatIsNoInstantIsKeptAsStoredAndNoted() {
        FormsValues.Converted converted = FormsValues.convert("datePicker", List.of("yesterday"), null);
        assertEquals(List.of("yesterday"), converted.values());
        assertNotNull(converted.note());
    }

    @Test
    void aCountryGivesItsCode() {
        assertEquals(Optional.of("FR"), FormsValues.country("{\"country\":{\"key\":\"FR\",\"name\":\"France\"},\"rendererName\":\"country\"}"));
        assertTrue(FormsValues.country("{\"rendererName\":\"country\"}").isEmpty());
        assertTrue(FormsValues.country("FR").isEmpty());
    }

    /** The shape of ratingDefinition.directive.jsp: the number sits under "value", beside the renderer, css and type. */
    @Test
    void aRatingGivesItsNumber() {
        assertEquals(Optional.of("4"), FormsValues.rating("{\"rendererName\":\"rating\",\"css\":\"fa-star rated\",\"type\":\"star\",\"value\":4}"));
        assertEquals(Optional.of("3"), FormsValues.rating("3"));
        assertTrue(FormsValues.rating("{\"rendererName\":\"rating\",\"css\":\"fa-star rated\"}").isEmpty());
    }

    /** The shape of the matrix directives: one key per row beside the renderer name, nothing nested. */
    @Test
    void aMatrixGivesOneLinePerRow() {
        String radios = "{\"Price\":\"Good\",\"Service\":\"Poor\",\"rendererName\":\"matrixRadios\"}";
        assertEquals(Optional.of("Price: Good\nService: Poor"), FormsValues.matrix(radios));
        String checkboxes = "{\"Price\":[\"Good\",\"Fair\"],\"rendererName\":\"matrixCheckboxes\"}";
        assertEquals(Optional.of("Price: Good, Fair"), FormsValues.matrix(checkboxes));
        assertTrue(FormsValues.matrix("{\"rendererName\":\"matrixRadios\"}").isEmpty());
        assertTrue(FormsValues.matrix("{\"Price\":\"Good\"}").isEmpty());
    }

    @Test
    void theRowsOfAMatrixKeepTheOrderOfTheText() {
        // a JSONObject iterates its keys by hash: row1, Zeta, row10, Alpha, row2
        String json = "{\"row1\":\"a\",\"row2\":\"b\",\"row10\":\"c\",\"Zeta\":\"d\",\"Alpha\":\"e\",\"rendererName\":\"matrixRadios\"}";
        assertEquals(Optional.of("row1: a\nrow2: b\nrow10: c\nZeta: d\nAlpha: e"), FormsValues.matrix(json));
    }

    @Test
    void aPasswordIsDroppedAndAFileJsonToo() {
        FormsValues.Converted password = FormsValues.convert("password", List.of(FormsValues.PASSWORD_PLACEHOLDER), null);
        assertTrue(password.values().isEmpty());
        assertEquals("password dropped", password.note());

        FormsValues.Converted file = FormsValues.convert("fileUpload", List.of("{\"url\":[],\"rendererName\":\"fileUpload\"}"), null);
        assertTrue(file.values().isEmpty());
        assertNull(file.note());
    }

    /** Forms submits the yes label of the box when ticked, its no label when not (ng-false-value="'{{input.no}}'"). */
    @Test
    void aConsentStoresTrueWhenTickedAndNothingWhenNot() {
        FormsValues.ConsentLabels labels = new FormsValues.ConsentLabels(java.util.Set.of("Accepted", "Accepté"), java.util.Set.of("Not Accepted"));
        assertEquals(List.of("true"), FormsValues.convert("acceptTermCheckbox", List.of("Accepted"), labels).values());
        assertEquals(List.of("true"), FormsValues.convert("acceptTermCheckbox", List.of("Accepté"), labels).values());
        assertEquals(List.of(), FormsValues.convert("acceptTermCheckbox", List.of("Not Accepted"), labels).values());
        assertEquals(List.of(), FormsValues.convert("acceptTermCheckbox", List.of(""), labels).values());
        assertEquals(List.of(), FormsValues.convert("acceptTermCheckbox", List.of(), labels).values());
        // the literal booleans of a box whose labels were left empty
        assertEquals(List.of("true"), FormsValues.convert("acceptTermCheckbox", List.of("true"), null).values());
        assertEquals(List.of(), FormsValues.convert("acceptTermCheckbox", List.of("false"), null).values());
        // a text that is neither label is kept, and said so
        FormsValues.Converted other = FormsValues.convert("acceptTermCheckbox", List.of("maybe"), labels);
        assertEquals(List.of("maybe"), other.values());
        assertNotNull(other.note());
        assertNotNull(FormsValues.convert("acceptTermCheckbox", List.of("Accepted"), null).note());
    }

    @Test
    void everythingElseIsUnchanged() {
        assertEquals(List.of("a", "b"), FormsValues.convert("multipleCheckBoxes", List.of("a", "b"), null).values());
        assertEquals(List.of("Jane"), FormsValues.convert("input", List.of("Jane"), null).values());
        assertEquals(List.of("x"), FormsValues.convert(null, List.of("x"), null).values());
    }
}
