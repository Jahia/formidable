package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The scope of the API security filter that the module ships, parsed as the filter reads it: one scope, applied to a
 * page of this server, with no constraint, and one grant that matches no node and names the root fields of the
 * extensions and every object type they reach, nothing else.
 */
class QuarantineApiScopeTest {

    private static final String SCOPE_FILE =
            "/META-INF/configurations/org.jahia.bundles.api.authorization-formidable-quarantine.yml";
    private static final String SCOPE = "formidable-quarantine";
    private static final List<Class<?>> EXTENSIONS =
            List.of(QuarantineQueryExtension.class, QuarantineMutationExtension.class);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scopes() throws IOException {
        try (InputStream in = QuarantineApiScopeTest.class.getResourceAsStream(SCOPE_FILE)) {
            assertNotNull(in, SCOPE_FILE);
            return (Map<String, Object>) new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> theScope() throws IOException {
        Map<String, Object> scopes = scopes();
        assertEquals(Set.of(SCOPE), scopes.keySet(), "the scopes of the file");
        return (Map<String, Object>) scopes.get(SCOPE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> theGrant() throws IOException {
        List<Map<String, Object>> grants = (List<Map<String, Object>>) theScope().get("grants");
        assertEquals(1, grants.size(), "the grants of the scope");
        return grants.get(0);
    }

    /** The names a grant lists, split as the filter splits them: on commas and spaces. */
    private static Set<String> names(Object api) {
        return new TreeSet<>(List.of(String.valueOf(api).trim().split("[, ]+")));
    }

    /** {@code graphql.<Query|Mutation>.<field>} for each root field that the extensions add. */
    private static Set<String> rootFields() {
        Set<String> fields = new TreeSet<>();
        for (Class<?> extension : EXTENSIONS) {
            Class<?> target = extension.getAnnotation(GraphQLTypeExtension.class).value();
            String type = target == DXGraphQLProvider.Mutation.class ? "Mutation" : "Query";
            for (Method method : fieldsOf(extension)) {
                fields.add("graphql." + type + "." + method.getAnnotation(GraphQLName.class).value());
            }
        }
        return fields;
    }

    private static List<Method> fieldsOf(Class<?> type) {
        return Arrays.stream(type.getMethods()).filter(method -> method.isAnnotationPresent(GraphQLField.class))
                .toList();
    }

    /** The object types that the root fields reach, through the fields and the lists of each type. */
    private static Set<String> reachedTypes() {
        Deque<Class<?>> toVisit = new ArrayDeque<>();
        EXTENSIONS.forEach(extension -> fieldsOf(extension).forEach(method -> visit(method, toVisit)));
        Set<String> names = new TreeSet<>();
        while (!toVisit.isEmpty()) {
            Class<?> type = toVisit.pop();
            if (!type.isEnum() && type.isAnnotationPresent(GraphQLName.class)
                    && names.add(type.getAnnotation(GraphQLName.class).value())) {
                fieldsOf(type).forEach(method -> visit(method, toVisit));
            }
        }
        return names;
    }

    private static void visit(Method field, Deque<Class<?>> toVisit) {
        toVisit.push(field.getReturnType());
        Type type = field.getGenericReturnType();
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (argument instanceof Class<?> element) {
                    toVisit.push(element);
                }
            }
        }
    }

    @Test
    void theOneGrantNamesTheRootFieldsAndEveryTypeTheyReachAndNothingElse() throws IOException {
        Set<String> expected = rootFields();
        reachedTypes().forEach(name -> expected.add("graphql." + name));

        Map<String, Object> grant = theGrant();

        assertEquals(Set.of("api", "node"), grant.keySet(), "the keys of the grant");
        assertEquals(expected, names(grant.get("api")));
        assertEquals("none", grant.get("node"), "the grant matches no node");
    }

    @Test
    void theScopeAppliesToAPageOfThisServerAndAsksForNoConstraint() throws IOException {
        Map<String, Object> scope = theScope();

        assertEquals(List.of(Map.of("origin", "hosted")), scope.get("auto_apply"));
        assertFalse(scope.containsKey("constraints"), "a constraint, such as privileged_user");
    }
}
