package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.formidable.quarantine.store.AuditRecord;

@GraphQLName("FmdbqAuditRecord")
@GraphQLDescription("Who changed the state of a held submission, how and when: never a value")
public class GqlAuditRecord {

    private final AuditRecord audit;

    GqlAuditRecord(AuditRecord audit) {
        this.audit = audit;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("retry, release, resume, reject, or run when the run of the checks moved the state")
    public String getOperation() {
        return audit.operation();
    }

    @GraphQLField
    @GraphQLDescription("The name of the user of the operation; null for the run")
    public String getUser() {
        return audit.userName();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The stored name of the state before")
    public String getFrom() {
        return audit.fromState();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The stored name of the state after")
    public String getTo() {
        return audit.toState();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("When the operation ended, ISO-8601 in UTC")
    public String getAt() {
        return audit.at().toString();
    }
}
