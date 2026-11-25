package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;

@GraphQLName("FmdbqSubmissionPage")
@GraphQLDescription("A page of the held submissions of the site, oldest first")
public class GqlSubmissionPage {

    private final int totalCount;
    private final int offset;
    private final int limit;
    private final List<GqlHeldSubmission> items;

    GqlSubmissionPage(int totalCount, int offset, int limit, List<GqlHeldSubmission> items) {
        this.totalCount = totalCount;
        this.offset = offset;
        this.limit = limit;
        this.items = List.copyOf(items);
    }

    @GraphQLField
    @GraphQLDescription("The number of held submissions that match, on every page")
    public int getTotalCount() {
        return totalCount;
    }

    @GraphQLField
    @GraphQLDescription("The index of the first item of the page")
    public int getOffset() {
        return offset;
    }

    @GraphQLField
    @GraphQLDescription("The size of the page that the server applied: at most 100")
    public int getLimit() {
        return limit;
    }

    @GraphQLField
    @GraphQLNonNull
    public List<@GraphQLNonNull GqlHeldSubmission> getItems() {
        return items;
    }
}
