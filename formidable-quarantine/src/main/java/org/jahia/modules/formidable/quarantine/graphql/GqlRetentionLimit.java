package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;

/** Whose limit a purge applied, as the GraphQL API names it: the names of {@link QuarantineRetention.Limit}. */
@GraphQLName("FmdbqRetentionLimit")
@GraphQLDescription("Whose limit a purge of the retention applied")
public enum GqlRetentionLimit {
    @GraphQLDescription("The setting of the platform: the site sets none, a higher one, or a negative one")
    PLATFORM,
    @GraphQLDescription("The setting of the platform, because the settings of the site could not be read: the failure"
            + " is in the log of the server")
    PLATFORM_UNREAD,
    @GraphQLDescription("The lower setting of the site")
    SITE,
    @GraphQLDescription("The limit given for this call, olderThanDays")
    CALL;

    static GqlRetentionLimit of(QuarantineRetention.Limit limit) {
        return valueOf(limit.name());
    }
}
