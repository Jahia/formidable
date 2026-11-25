package org.jahia.modules.formidable.quarantine;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Map;

/** A configuration of an OSGi metatype annotation for a test: the values given, the defaults of the type else. */
public final class Configs {

    private Configs() {
    }

    /** @param values the values, by the name of the method of the annotation */
    public static <A extends Annotation> A of(Class<A> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if ("annotationType".equals(method.getName())) {
                return type;
            }
            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }
            Object fallback = method.getDefaultValue();
            if (fallback == null) {
                throw new UnsupportedOperationException(method.getName());
            }
            return fallback;
        }));
    }
}
