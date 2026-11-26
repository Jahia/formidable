package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

@GraphQLName("FmdbqStateCount")
@GraphQLDescription("The number of held submissions of the site in one state")
public class GqlStateCount {

    private final GqlState state;
    private final int count;

    GqlStateCount(GqlState state, int count) {
        this.state = state;
        this.count = count;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The state")
    public GqlState getState() {
        return state;
    }

    @GraphQLField
    @GraphQLDescription("The number of held submissions of the site in the state")
    public int getCount() {
        return count;
    }
}
