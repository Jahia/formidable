package org.jahia.modules.formidable.content.convert;

import javax.jcr.PropertyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Converts the text a form submitted to the type of the property it is mapped to, read from the property's definition:
 * string, long, double, decimal, boolean, and date (the {@code yyyy-MM-dd} an HTML date input sends). A property that is
 * multiple takes every value of the field; a single one takes the first. Pure and stateless.
 *
 * <p>Strict on purpose: a number that is not a plain decimal literal ({@code 0x10}, {@code 1d}, {@code NaN}), a date
 * that is not an ISO day ({@code 2026-02-31}) or a boolean the converter does not know is refused, never guessed. A
 * date is stored as the start of that day in UTC.</p>
 */
public final class ValueConverter {

    private static final Pattern LONG = Pattern.compile("[+-]?\\d{1,18}");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Set<String> TRUE = Set.of("true", "on", "yes", "1");
    private static final Set<String> FALSE = Set.of("false", "off", "no", "0");

    private ValueConverter() {
    }

    /**
     * @param type a {@link PropertyType} constant
     * @return whether a field can fill a property of that type: string, long, double, decimal, boolean, date
     */
    public static boolean supports(int type) {
        return type == PropertyType.STRING || type == PropertyType.LONG || type == PropertyType.DOUBLE
                || type == PropertyType.DECIMAL || type == PropertyType.BOOLEAN || type == PropertyType.DATE;
    }

    /**
     * Converts the values a field submitted.
     *
     * @param shape the property the field is mapped to
     * @param raw   what the field submitted, may be {@code null}; blank values are ignored
     * @return the converted values: one for a single property, all for a multiple one; empty when the field was left
     *         blank, in which case nothing is written
     * @throws ConversionException on a value that does not fit the type, or a type that is not filled from a field
     */
    public static List<Object> convert(PropertyShape shape, List<String> raw) throws ConversionException {
        List<String> given = new ArrayList<>();
        if (raw != null) {
            for (String value : raw) {
                if (value != null && !value.isBlank()) {
                    given.add(value.trim());
                }
            }
        }
        if (given.isEmpty()) {
            return List.of();
        }
        List<String> used = shape.multiple() ? given : given.subList(0, 1);
        List<Object> converted = new ArrayList<>();
        for (String value : used) {
            converted.add(convertOne(shape, value));
        }
        return converted;
    }

    private static Object convertOne(PropertyShape shape, String value) throws ConversionException {
        switch (shape.type()) {
            case PropertyType.STRING:
                return value;
            case PropertyType.LONG:
                if (!LONG.matcher(value).matches()) {
                    throw refused(shape, "a whole number");
                }
                return Long.parseLong(value);
            case PropertyType.DOUBLE:
                if (!DECIMAL.matcher(value).matches()) {
                    throw refused(shape, "a number");
                }
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number)) {
                    throw refused(shape, "a finite number");
                }
                return number;
            case PropertyType.DECIMAL:
                if (!DECIMAL.matcher(value).matches()) {
                    throw refused(shape, "a number");
                }
                return new BigDecimal(value);
            case PropertyType.BOOLEAN:
                String lower = value.toLowerCase(Locale.ROOT);
                if (TRUE.contains(lower)) {
                    return Boolean.TRUE;
                }
                if (FALSE.contains(lower)) {
                    return Boolean.FALSE;
                }
                throw refused(shape, "true or false");
            case PropertyType.DATE:
                try {
                    LocalDate day = LocalDate.parse(value);
                    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone(ZoneOffset.UTC));
                    calendar.clear();
                    calendar.set(day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
                    return calendar;
                } catch (DateTimeParseException e) {
                    throw refused(shape, "a date as yyyy-MM-dd");
                }
            default:
                throw new ConversionException("Property '" + shape.name() + "' has the type " + PropertyType.nameFromValue(shape.type())
                        + ", which the action does not fill from a field (string, long, double, decimal, boolean and date only)");
        }
    }

    private static ConversionException refused(PropertyShape shape, String expected) {
        // the value is the visitor's: it names the property in the log, never repeats what was typed
        return new ConversionException("Property '" + shape.name() + "' expects " + expected);
    }
}
