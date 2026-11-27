package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

/** The operations on the quarantine of one site, for a manager of it. The resolver that returns it checked the permission. */
@GraphQLName("FmdbqQuarantineMutation")
@GraphQLDescription("The operations of a manager on the quarantine of a site")
public class GqlQuarantineMutation {

    private final SiteQuarantine site;

    GqlQuarantineMutation(SiteQuarantine site) {
        this.site = site;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Runs an operation on a held submission of the site, writes the audit record with the name of"
            + " the user, and answers the submission as the operation left it. An unknown identifier or a state that"
            + " does not allow the operation answers an error whose extensions.code is NOT_FOUND or CONFLICT, and"
            + " nothing ran; an error with no code is a failure")
    public GqlHeldSubmission operate(
            @GraphQLName("id") @GraphQLNonNull @GraphQLDescription("The identifier of the held submission in the site")
            String id,
            @GraphQLName("operation") @GraphQLNonNull @GraphQLDescription("The operation to run")
            GqlOperation operation) {
        return site.operate(id, operation);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Purges the rejected held submissions of the site whose last change is older than the limit:"
            + " the configured retention, or olderThanDays for this call. Server administrators only: any other user"
            + " gets an error whose extensions.code is FORBIDDEN")
    public GqlPurgeReport purge(@GraphQLName("olderThanDays")
                                @GraphQLDescription("The limit for this call, in days; the configured one when absent")
                                Integer olderThanDays) {
        return site.purge(olderThanDays);
    }
}
