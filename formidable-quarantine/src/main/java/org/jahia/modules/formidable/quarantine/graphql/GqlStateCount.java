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
    public GqlState getState() {
        return state;
    }

    @GraphQLField
    public int getCount() {
        return count;
    }
}
