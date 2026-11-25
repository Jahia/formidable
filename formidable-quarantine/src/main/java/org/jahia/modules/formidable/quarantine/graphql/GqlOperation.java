package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.formidable.quarantine.ops.Operation;

/** An operation of a manager, as the GraphQL API names it: the names of {@link Operation}. */
@GraphQLName("FmdbqOperation")
@GraphQLDescription("An operation of a manager on a held submission")
public enum GqlOperation {
    RETRY, RELEASE, RESUME, REJECT;

    Operation operation() {
        return Operation.valueOf(name());
    }

    static GqlOperation of(Operation operation) {
        return valueOf(operation.name());
    }
}
