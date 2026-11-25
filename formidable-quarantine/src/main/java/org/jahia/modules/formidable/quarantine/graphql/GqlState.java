package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.formidable.quarantine.state.HeldState;

/** The state of a held submission, as the GraphQL API names it: the names of {@link HeldState}. */
@GraphQLName("FmdbqState")
@GraphQLDescription("The state of a held submission of the quarantine")
public enum GqlState {
    HELD, CHECKING, RELEASING, COMPLETED, REJECTED, NEEDS_REVIEW;

    /** @return the state of the value that a held submission stores */
    static GqlState of(String stored) {
        return valueOf(HeldState.fromValue(stored).name());
    }
}
