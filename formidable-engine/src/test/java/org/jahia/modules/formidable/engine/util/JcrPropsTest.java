package org.jahia.modules.formidable.engine.util;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JcrPropsTest {

    @Test
    void returnsDefaultValuesWhenPropertyIsMissing() throws Exception {
        // Verifies null-safe property access: missing properties should return the
        // caller-provided defaults instead of forcing each consumer to repeat checks.
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.hasProperty("missing")).thenReturn(false);

        // Expected outcome: each accessor returns its explicit default value.
        assertEquals("fallback", JcrProps.string(node, "missing", "fallback"));
        assertEquals(true, JcrProps.bool(node, "missing", true));
        assertEquals(42L, JcrProps.longValue(node, "missing", 42L));
        assertEquals("n/a", JcrProps.dateAsIso(node, "missing", true, "n/a"));
    }

    @Test
    void formatsDatePropertyUsingExpectedIsoShape() throws Exception {
        // Verifies date normalization: JCR calendar values should be converted to the
        // same ISO shapes expected by field validation code.
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(2026, Calendar.JANUARY, 9, 14, 5, 0);

        when(node.hasProperty("min")).thenReturn(true);
        when(node.getProperty("min")).thenReturn(property);
        when(property.getDate()).thenReturn(calendar);

        // Expected outcome: date-only and datetime-local shapes match current parser expectations.
        assertEquals("2026-01-09", JcrProps.dateAsIso(node, "min", false, null));
        assertEquals("2026-01-09T14:05", JcrProps.dateAsIso(node, "min", true, null));
    }
}
