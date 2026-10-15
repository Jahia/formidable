package org.jahia.modules.formidable.content;

import org.jahia.modules.formidable.content.convert.ConversionException;
import org.jahia.modules.formidable.content.convert.PropertyShape;
import org.jahia.modules.formidable.content.convert.ValueConverter;
import org.junit.jupiter.api.Test;

import javax.jcr.PropertyType;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueConverterTest {

    private static PropertyShape single(int type) {
        return new PropertyShape("p", type, false, false);
    }

    private static PropertyShape multiple(int type) {
        return new PropertyShape("p", type, true, false);
    }

    @Test
    void stringsComeThroughTrimmed() throws ConversionException {
        assertEquals(List.of("hello"), ValueConverter.convert(single(PropertyType.STRING), List.of("  hello ")));
    }

    @Test
    void longsAreWholeNumbers() throws ConversionException {
        assertEquals(List.of(42L), ValueConverter.convert(single(PropertyType.LONG), List.of("42")));
        assertEquals(List.of(-7L), ValueConverter.convert(single(PropertyType.LONG), List.of("-7")));
        for (String bad : List.of("4.2", "abc", "0x10", "1e3", "9999999999999999999", "1 2")) {
            assertThrows(ConversionException.class, () -> ValueConverter.convert(single(PropertyType.LONG), List.of(bad)), bad);
        }
    }

    @Test
    void doublesAndDecimalsArePlainLiterals() throws ConversionException {
        assertEquals(List.of(1250.5), ValueConverter.convert(single(PropertyType.DOUBLE), List.of("1250.5")));
        assertEquals(List.of(1000.0), ValueConverter.convert(single(PropertyType.DOUBLE), List.of("1e3")));
        assertEquals(List.of(new BigDecimal("12.30")), ValueConverter.convert(single(PropertyType.DECIMAL), List.of("12.30")));
        for (String bad : List.of("NaN", "Infinity", "0x1p3", "1d", "1,5", "abc", "1e999")) {
            assertThrows(ConversionException.class, () -> ValueConverter.convert(single(PropertyType.DOUBLE), List.of(bad)), bad);
        }
    }

    @Test
    void booleansAcceptTheUsualSpellings() throws ConversionException {
        for (String yes : List.of("true", "TRUE", "on", "yes", "1")) {
            assertEquals(List.of(true), ValueConverter.convert(single(PropertyType.BOOLEAN), List.of(yes)), yes);
        }
        for (String no : List.of("false", "off", "No", "0")) {
            assertEquals(List.of(false), ValueConverter.convert(single(PropertyType.BOOLEAN), List.of(no)), no);
        }
        assertThrows(ConversionException.class, () -> ValueConverter.convert(single(PropertyType.BOOLEAN), List.of("maybe")));
    }

    @Test
    void aDateIsAnIsoDayStoredAsTheStartOfThatDayInUtc() throws ConversionException {
        Calendar date = (Calendar) ValueConverter.convert(single(PropertyType.DATE), List.of("2026-10-15")).get(0);
        assertEquals(TimeZone.getTimeZone("UTC"), date.getTimeZone());
        assertEquals(2026, date.get(Calendar.YEAR));
        assertEquals(Calendar.OCTOBER, date.get(Calendar.MONTH));
        assertEquals(15, date.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, date.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, date.get(Calendar.MILLISECOND));
        for (String bad : List.of("15/10/2026", "2026-02-31", "2026-13-01", "2026-10-15T10:00:00", "tomorrow", "2026-1-5")) {
            assertThrows(ConversionException.class, () -> ValueConverter.convert(single(PropertyType.DATE), List.of(bad)), bad);
        }
    }

    @Test
    void aMultiplePropertyTakesEveryValueASingleOneTheFirst() throws ConversionException {
        assertEquals(List.of("a", "b", "c"), ValueConverter.convert(multiple(PropertyType.STRING), List.of("a", " ", "b", "c")));
        assertEquals(List.of(1L, 2L), ValueConverter.convert(multiple(PropertyType.LONG), List.of("1", "2")));
        assertEquals(List.of("a"), ValueConverter.convert(single(PropertyType.STRING), List.of("a", "b")));
    }

    @Test
    void oneBadValueOfAMultiplePropertyRefusesTheLot() {
        assertThrows(ConversionException.class, () -> ValueConverter.convert(multiple(PropertyType.LONG), List.of("1", "x")));
    }

    @Test
    void aFieldLeftBlankWritesNothing() throws ConversionException {
        assertTrue(ValueConverter.convert(single(PropertyType.LONG), null).isEmpty());
        assertTrue(ValueConverter.convert(single(PropertyType.LONG), List.of()).isEmpty());
        assertTrue(ValueConverter.convert(single(PropertyType.LONG), List.of("", "  ")).isEmpty());
    }

    @Test
    void aTypeThatIsNotFilledFromTextIsRefused() {
        ConversionException error = assertThrows(ConversionException.class,
                () -> ValueConverter.convert(single(PropertyType.WEAKREFERENCE), List.of("whatever")));
        assertTrue(error.getMessage().contains("WeakReference"));
        assertFalse(ValueConverter.supports(PropertyType.BINARY));
        assertFalse(ValueConverter.supports(PropertyType.NAME));
        assertTrue(ValueConverter.supports(PropertyType.DATE));
    }

    @Test
    void theMessageNamesThePropertyButNeverRepeatsTheValue() {
        ConversionException error = assertThrows(ConversionException.class,
                () -> ValueConverter.convert(new PropertyShape("durationMinutes", PropertyType.LONG, false, false), List.of("secret-text")));
        assertTrue(error.getMessage().contains("durationMinutes"));
        assertFalse(error.getMessage().contains("secret-text"));
    }
}
