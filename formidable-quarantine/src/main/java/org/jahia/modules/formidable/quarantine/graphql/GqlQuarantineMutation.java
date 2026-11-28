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
            + " the user, and answers the submission as the operation left it. An unknown identifier answers an error"
            + " whose extensions.code is NOT_FOUND; a state that does not allow the operation, or a run of the"
            + " quarantine that holds the site or moved the submission first, answers CONFLICT; nothing ran. An error"
            + " with no code is a failure")
    public GqlHeldSubmission operate(
            @GraphQLName("id") @GraphQLNonNull @GraphQLDescription("The identifier of the held submission in the site")
            String id,
            @GraphQLName("operation") @GraphQLNonNull @GraphQLDescription("The operation to run")
            GqlOperation operation) {
        return site.operate(id, operation);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Purges the completed and rejected held submissions of the site whose last change is older"
            + " than the limit: the retention of the platform, or the lower one of the site, or olderThanDays for this"
            + " call. The report says whose limit applied. Server administrators only: a manager of the site who is"
            + " not one gets an error whose extensions.code is FORBIDDEN")
    public GqlPurgeReport purge(@GraphQLName("olderThanDays")
                                @GraphQLDescription("The limit for this call, in days; the limit of the site when"
                                        + " absent")
                                Integer olderThanDays) {
        return site.purge(olderThanDays);
    }
}
