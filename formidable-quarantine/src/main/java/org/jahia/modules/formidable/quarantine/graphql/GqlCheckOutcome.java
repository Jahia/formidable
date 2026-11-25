package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;

@GraphQLName("FmdbqCheckOutcome")
@GraphQLDescription("The last outcome of one check for one file: never the ticket, never a byte")
public class GqlCheckOutcome {

    private final OutcomeRecord outcome;
    private final String type;

    GqlCheckOutcome(OutcomeRecord outcome, String type) {
        this.outcome = outcome;
        this.type = type;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The name of the check node in the \"Checks\" list of the form")
    public String getName() {
        return outcome.checkName();
    }

    @GraphQLField
    @GraphQLDescription("The node type of the check; null when the form no longer lists it")
    public String getType() {
        return type;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("PASS, FAIL, INCONCLUSIVE, PENDING, or FAILED when the check could not answer")
    public String getVerdict() {
        return outcome.outcome();
    }

    @GraphQLField
    @GraphQLDescription("The reason code of the check")
    public String getReasonCode() {
        return outcome.reason();
    }

    @GraphQLField
    @GraphQLDescription("For FAILED: true when a later attempt may succeed")
    public boolean isRetryable() {
        return outcome.retryable();
    }

    @GraphQLField
    @GraphQLDescription("True when the check answered a ticket that the run polls")
    public boolean isTicketPending() {
        return outcome.ticket() != null;
    }

    @GraphQLField
    @GraphQLDescription("The count of retryable failures in a row")
    public int getAttempts() {
        return outcome.attempts();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The SHA-256 of the bytes the outcome was given for")
    public String getSha256() {
        return outcome.sha256();
    }

    @GraphQLField
    @GraphQLDescription("When the outcome was recorded, ISO-8601 in UTC")
    public String getAt() {
        return outcome.at() == null ? null : outcome.at().toString();
    }
}
