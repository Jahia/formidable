package org.jahia.modules.formidable.engine.config.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * A theme's definition read the way the metatype reads it — the settings by their attribute id, at the annotation's
 * defaults — and the values of a configuration in the form a {@code .cfg} file reads back. What the completion of a
 * theme's file and the migration of the single PID both rely on.
 */
public final class ConfigurationAttributes {

    /** A configuration loaded from a file carries the file's name under this key; one made without a file does not. */
    public static final String FILEINSTALL_FILENAME = "felix.fileinstall.filename";

    private ConfigurationAttributes() {
    }

    /**
     * The theme's settings and their defaults as text: the attribute ids of the definition, derived from its
     * methods the way the metatype does ({@link #attributeId}), so an attribute whose id differs from its method
     * name is read like the others.
     */
    public static Map<String, String> defaultsOf(Class<? extends Annotation> definition) {
        Map<String, String> defaults = new TreeMap<>();
        defaultValuesOf(definition).forEach((setting, value) -> defaults.put(setting, asText(value)));
        return defaults;
    }

    /** The theme's settings and their defaults as the strings written into a configuration ({@link #asStrings}). */
    public static Map<String, Object> defaultStringsOf(Class<? extends Annotation> definition) {
        Map<String, Object> defaults = new TreeMap<>();
        defaultValuesOf(definition).forEach((setting, value) -> defaults.put(setting, asStrings(value)));
        return defaults;
    }

    private static Map<String, Object> defaultValuesOf(Class<? extends Annotation> definition) {
        Map<String, Object> defaults = new TreeMap<>();
        for (Method method : definition.getDeclaredMethods()) {
            Object defaultValue = method.getDefaultValue();
            if (defaultValue != null) {
                defaults.put(attributeId(method.getName()), defaultValue);
            }
        }
        return defaults;
    }

    /**
     * The attribute id the metatype derives from an annotation method name (OSGi Compendium, component property
     * types and metatype annotations): {@code $_$} becomes {@code -}, {@code $$} becomes {@code $}, a lone
     * {@code $} is dropped, {@code __} becomes {@code _} and a lone {@code _} becomes {@code .}. Plain camelCase
     * names are their own id.
     */
    public static String attributeId(String methodName) {
        StringBuilder id = new StringBuilder(methodName.length());
        int i = 0;
        while (i < methodName.length()) {
            if (methodName.startsWith("$_$", i)) {
                id.append('-');
                i += 3;
            } else if (methodName.startsWith("$$", i)) {
                id.append('$');
                i += 2;
            } else if (methodName.startsWith("__", i)) {
                id.append('_');
                i += 2;
            } else {
                char c = methodName.charAt(i);
                if (c == '_') {
                    id.append('.');
                } else if (c != '$') {
                    // a lone $ is dropped
                    id.append(c);
                }
                i++;
            }
        }
        return id.toString();
    }

    /** A value as text, a multi-valued one by its elements — what "the same value" means here. */
    public static String asText(Object value) {
        return value instanceof Object[] array ? Arrays.toString(array) : String.valueOf(value);
    }

    /**
     * A value as the strings to write into a configuration: a multi-valued one as an array of strings, a single
     * one as a string — the metatype coerces the strings back to the attribute's type. A typed value would be
     * persisted in fileinstall's typed syntax, which a {@code .cfg} file does not read back.
     */
    public static Object asStrings(Object value) {
        if (value instanceof Object[] array) {
            return Arrays.stream(array).map(String::valueOf).toArray(String[]::new);
        }
        return String.valueOf(value);
    }
}
