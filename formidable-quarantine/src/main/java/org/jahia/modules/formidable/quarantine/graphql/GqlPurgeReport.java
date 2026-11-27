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
    private final GqlRetentionLimit limit;
    private final List<String> purged;
    private final List<String> failed;

    GqlPurgeReport(int retentionDays, GqlRetentionLimit limit, List<String> purged, List<String> failed) {
        this.retentionDays = retentionDays;
        this.limit = limit;
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
    @GraphQLDescription("Whose limit the purge applied: PLATFORM_UNREAD when the settings of the site could not be"
            + " read, so the longer limit of the platform applied")
    public GqlRetentionLimit getLimit() {
        return limit;
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
