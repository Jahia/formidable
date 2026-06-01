package org.jahia.modules.formidable.engine.util;

import org.jahia.services.content.JCRNodeWrapper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

/**
 * Null-safe JCR property readers with explicit default values.
 */
public final class JcrProps {

    private JcrProps() {
    }

    public static boolean bool(JCRNodeWrapper node, String name, boolean defaultValue) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getBoolean() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long longValue(JCRNodeWrapper node, String name, long defaultValue) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getLong() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String string(JCRNodeWrapper node, String name, String defaultValue) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getString() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String dateAsIso(
            JCRNodeWrapper node,
            String name,
            boolean includeTime,
            String defaultValue
    ) {
        try {
            if (!node.hasProperty(name)) {
                return defaultValue;
            }

            Calendar calendar = node.getProperty(name).getDate();
            if (calendar == null) {
                return defaultValue;
            }

            ZoneId zoneId = calendar.getTimeZone().toZoneId();
            return includeTime
                    ? calendar.toInstant().atZone(zoneId).toLocalDateTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                    : calendar.toInstant().atZone(zoneId).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
