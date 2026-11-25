package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scope of the API security filter that the module ships: it grants the two root fields and every object type
 * that they reach, nothing of the platform, and it asks for no privileged access.
 */
class QuarantineApiScopeTest {

    private static final String SCOPE = "/META-INF/configurations/org.jahia.bundles.api.authorization-formidable-quarantine.yml";

    private static String scope() throws IOException {
        try (InputStream in = QuarantineApiScopeTest.class.getResourceAsStream(SCOPE)) {
            assertNotNull(in, SCOPE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The granted names of the scope: its line {@code - api:}, split as the filter splits it. */
    private static Set<String> granted(String scope) {
        for (String line : scope.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- api:")) {
                return new TreeSet<>(Set.of(trimmed.substring("- api:".length()).trim().split("[, ]+")));
            }
        }
        throw new AssertionError("no grant of api in " + SCOPE);
    }

    /** The object types that the root fields reach, through the fields and the lists of each type. */
    private static Set<String> reachedTypes() {
        Set<String> names = new TreeSet<>();
        Deque<Class<?>> toVisit = new ArrayDeque<>(List.of(GqlQuarantine.class, GqlQuarantineMutation.class));
        while (!toVisit.isEmpty()) {
            Class<?> type = toVisit.pop();
            if (type.isEnum() || !type.isAnnotationPresent(GraphQLName.class)
                    || !names.add(type.getAnnotation(GraphQLName.class).value())) {
                continue;
            }
            for (Method method : type.getMethods()) {
                if (method.isAnnotationPresent(GraphQLField.class)) {
                    toVisit.push(method.getReturnType());
                    elementOf(method.getGenericReturnType(), toVisit);
                }
            }
        }
        return names;
    }

    private static void elementOf(Type type, Deque<Class<?>> toVisit) {
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (argument instanceof Class<?> element) {
                    toVisit.push(element);
                }
            }
        }
    }

    @Test
    void theScopeGrantsTheRootFieldsAndEveryTypeTheyReachAndNothingElse() throws IOException {
        Set<String> expected = new TreeSet<>(Set.of("graphql.Query.formidableQuarantine",
                "graphql.Mutation.formidableQuarantine"));
        reachedTypes().forEach(name -> expected.add("graphql." + name));

        assertEquals(expected, granted(scope()));
    }

    @Test
    void theScopeRequiresNoPrivilegedAccessAndMatchesNoNode() throws IOException {
        String scope = scope();

        assertFalse(scope.contains("privileged_user"), "privileged_user");
        assertTrue(scope.contains("node: none"), "node: none");
        assertTrue(scope.contains("origin: hosted"), "a page of this server only");
    }
}
