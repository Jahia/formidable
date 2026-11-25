package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;

@GraphQLName("FmdbqPurgeReport")
@GraphQLDescription("What a purge of the retention removed")
public class GqlPurgeReport {

    private final int retentionDays;
    private final List<String> purged;
    private final List<String> failed;

    GqlPurgeReport(int retentionDays, List<String> purged, List<String> failed) {
        this.retentionDays = retentionDays;
        this.purged = List.copyOf(purged);
        this.failed = List.copyOf(failed);
    }

    @GraphQLField
    @GraphQLDescription("The limit the purge applied, in days")
    public int getRetentionDays() {
        return retentionDays;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The identifiers of the purged held submissions")
    public List<@GraphQLNonNull String> getPurged() {
        return purged;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The identifiers of the held submissions that could not be purged")
    public List<@GraphQLNonNull String> getFailed() {
        return failed;
    }
}
