package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;

@GraphQLName("FmdbqDecision")
@GraphQLDescription("Why the run stopped a held submission")
public class GqlDecision {

    private final DecisionRecord decision;

    GqlDecision(DecisionRecord decision) {
        this.decision = decision;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("A reason of the chain, such as CHECK_FAILED, or INTEGRITY_FAILED, ACTION_FAILED, FORM_GONE")
    public String getReason() {
        return decision.reason();
    }

    @GraphQLField
    @GraphQLDescription("The item the reason is about, such as item0")
    public String getItem() {
        return decision.itemName();
    }

    @GraphQLField
    @GraphQLDescription("The name of the check node the reason is about")
    public String getCheck() {
        return decision.checkName();
    }

    @GraphQLField
    @GraphQLDescription("The SHA-256 of the file the reason is about")
    public String getSha256() {
        return decision.sha256();
    }

    @GraphQLField
    @GraphQLDescription("For ACTION_FAILED: the index of the action after release to resume from")
    public Integer getFailedAction() {
        return decision.failedAction();
    }

    @GraphQLField
    @GraphQLDescription("When the decision was taken, ISO-8601 in UTC")
    public String getAt() {
        return decision.at() == null ? null : decision.at().toString();
    }
}
