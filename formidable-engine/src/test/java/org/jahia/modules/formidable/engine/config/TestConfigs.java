package org.jahia.modules.formidable.engine.config;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The configuration doubles of the tests: a theme's definition answered by the annotation's own defaults, with
 * the values a test sets over them — one proxy for the five themes, in place of a hand-written double per
 * definition — and the check every theme runs on its shipped file.
 */
public final class TestConfigs {

    private TestConfigs() {
    }

    /** The definition at its defaults. */
    public static <C extends Annotation> C of(Class<C> definition) {
        return of(definition, Map.of());
    }

    /** The definition at its defaults, the given attributes set — by method name, a number coerced to the attribute's type. */
    @SuppressWarnings("unchecked")
    public static <C extends Annotation> C of(Class<C> definition, Map<String, ?> values) {
        return (C) Proxy.newProxyInstance(definition.getClassLoader(), new Class<?>[] {definition}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "annotationType":
                    return definition;
                case "toString":
                    return definition.getSimpleName() + values;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    Object value = values.containsKey(method.getName()) ? values.get(method.getName()) : method.getDefaultValue();
                    if (value == null) {
                        throw new IllegalArgumentException("No value and no default for " + method.getName());
                    }
                    return coerce(value, method.getReturnType());
            }
        });
    }

    private static Object coerce(Object value, Class<?> type) {
        if (type == long.class) {
            return ((Number) value).longValue();
        }
        if (type == int.class) {
            return ((Number) value).intValue();
        }
        return value;
    }

    /**
     * Verifies the one thing two copies of a default cannot verify about each other: the .cfg an administrator
     * reads and edits ships the same values as the definition the code falls back on — every attribute, no other
     * key. They agree today, and a drift would be silent: the file would promise one bound and the engine apply
     * another.
     */
    public static void assertShippedFileMatchesDefaults(Class<? extends Annotation> definition, String pid) throws IOException {
        Properties shipped = new Properties();
        try (InputStream in = TestConfigs.class.getResourceAsStream("/META-INF/configurations/" + pid + ".cfg")) {
            assertNotNull(in, "the shipped file " + pid + ".cfg");
            shipped.load(in);
        }
        Map<String, String> defaults = LegacyConfigurationMigration.defaultsOf(definition);
        assertEquals(defaults.keySet(), shipped.stringPropertyNames(), "the keys of " + pid + ".cfg");
        defaults.forEach((key, value) -> assertEquals(value, shipped.getProperty(key), key + " in " + pid + ".cfg"));
    }
}
