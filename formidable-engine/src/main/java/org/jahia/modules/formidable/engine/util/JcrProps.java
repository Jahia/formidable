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

    public static Double doubleOrNull(JCRNodeWrapper node, String name) {
        try {
            return node.hasProperty(name) ? node.getProperty(name).getDouble() : null;
        } catch (Exception e) {
            return null;
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

            return formatCalendar(node.getProperty(name).getDate(), includeTime, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Like {@link #dateAsIso}, but read on the underlying Jackrabbit node: a value
     * stored under a property definition that later moved away (e.g. the date bounds
     * of pre-0.4 fields) has no applicable definition anymore, which hides it from
     * the wrapper API while it still exists in storage.
     */
    public static String rawDateAsIso(
            JCRNodeWrapper node,
            String name,
            boolean includeTime,
            String defaultValue
    ) {
        try {
            javax.jcr.Node realNode = node.getRealNode();
            if (!realNode.hasProperty(name)) {
                return defaultValue;
            }

            return formatCalendar(realNode.getProperty(name).getDate(), includeTime, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String formatCalendar(Calendar calendar, boolean includeTime, String defaultValue) {
        if (calendar == null) {
            return defaultValue;
        }

        ZoneId zoneId = calendar.getTimeZone().toZoneId();
        return includeTime
                ? calendar.toInstant().atZone(zoneId).toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                : calendar.toInstant().atZone(zoneId).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
