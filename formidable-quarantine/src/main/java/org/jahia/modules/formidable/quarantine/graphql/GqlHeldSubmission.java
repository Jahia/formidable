package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.formidable.quarantine.ops.QuarantineOperations;
import org.jahia.modules.formidable.quarantine.state.HeldState;
import org.jahia.modules.formidable.quarantine.store.DecisionRecord;
import org.jahia.modules.formidable.quarantine.store.HeldRecord;
import org.jahia.modules.formidable.quarantine.store.ItemRecord;
import org.jahia.modules.formidable.quarantine.store.OutcomeRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One held submission, for a manager of its site. The record comes with it; the outcomes, the decision, the audit and
 * the values are read when a query first selects one of them, for every submission of the request at once, with the
 * system session of the store, inside the site that the resolver checked. Each field that computes answers a failure
 * with the fixed message of {@link SiteQuarantine}.
 */
@GraphQLName("FmdbqHeldSubmission")
@GraphQLDescription("A held submission of the quarantine: its state, its files and their checks, and its audit")
public class GqlHeldSubmission {

    private final SiteQuarantine site;
    private final HeldRecord held;

    GqlHeldSubmission(SiteQuarantine site, HeldRecord held) {
        this.site = site;
        this.held = held;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The identifier of the held submission in its site: <formId>/YYYY/MM/DD/held-<uuid>")
    public String getId() {
        return site.guarded("name", () -> HeldIds.idOf(site.siteKey(), held.path()));
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The identifier of the form")
    public String getFormId() {
        return held.formId();
    }

    @GraphQLField
    @GraphQLDescription("The title of the live form in the default language of the site; null when the form is gone")
    public String getFormTitle() {
        return site.form(held.formId()).title();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The time of intake, ISO-8601 in UTC")
    public String getCreatedAt() {
        return instant(held.createdAt());
    }

    @GraphQLField
    @GraphQLDescription("The last change of the state, ISO-8601 in UTC")
    public String getChangedAt() {
        return instant(held.changedAt());
    }

    @GraphQLField
    @GraphQLNonNull
    public GqlState getState() {
        return site.guarded("read the state", () -> GqlState.of(held.state()));
    }

    @GraphQLField
    @GraphQLDescription("The number of attempts of the checks so far")
    public int getAttempts() {
        return held.attempts();
    }

    @GraphQLField
    @GraphQLDescription("When the next attempt is due, ISO-8601 in UTC; null when none is")
    public String getNextAttempt() {
        return instant(held.nextAttempt());
    }

    @GraphQLField
    @GraphQLDescription("The number of actions after release that ran: the index a resume starts from")
    public int getCompletedActions() {
        return held.completedActions();
    }

    @GraphQLField
    @GraphQLDescription("The reason of the decision that stopped the run; null when there is none")
    public String getReason() {
        DecisionRecord decision = site.details(held.path()).decision();
        return decision == null ? null : decision.reason();
    }

    @GraphQLField
    @GraphQLDescription("The decision that stopped the run; null when there is none")
    public GqlDecision getDecision() {
        DecisionRecord decision = site.details(held.path()).decision();
        return decision == null ? null : new GqlDecision(decision);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The names of the public fields, in the order of the submission")
    public List<@GraphQLNonNull String> getFields() {
        return held.valueFields();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The names of the sensitive fields: never their values")
    public List<@GraphQLNonNull String> getSensitiveFields() {
        return held.sensitiveFields();
    }

    @GraphQLField
    @GraphQLDescription("False once the values were deleted")
    public boolean isValuesPresent() {
        return held.valuesPresent();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The files, in the order of the submission, with the outcome of each check")
    public List<@GraphQLNonNull GqlFile> getFiles() {
        List<OutcomeRecord> outcomes = site.details(held.path()).outcomes();
        Map<String, String> types = site.form(held.formId()).checkTypes();
        return site.guarded("read the files", () -> {
            List<GqlFile> files = new ArrayList<>();
            for (ItemRecord item : held.items()) {
                List<GqlCheckOutcome> checks = new ArrayList<>();
                for (OutcomeRecord outcome : outcomes) {
                    if (outcome.itemName().equals(item.name())) {
                        checks.add(new GqlCheckOutcome(outcome, types.get(outcome.checkName())));
                    }
                }
                files.add(new GqlFile(item, checks));
            }
            return files;
        });
    }

    @GraphQLField
    @GraphQLDescription("The public values; null unless the form shows them (showValues). Never a sensitive value")
    public List<@GraphQLNonNull GqlValue> getPublicValues() {
        // null, not empty: the form does not show its values, which differs from a form whose values were deleted
        List<GqlValue> values = null;
        if (site.form(held.formId()).showValues()) {
            values = new ArrayList<>();
            for (Map.Entry<String, List<String>> field : site.details(held.path()).publicValues().entrySet()) {
                values.add(new GqlValue(field.getKey(), field.getValue()));
            }
        }
        return values;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The audit records, oldest first")
    public List<@GraphQLNonNull GqlAuditRecord> getAudit() {
        return site.details(held.path()).audit().stream().map(GqlAuditRecord::new).toList();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The operations that the state and the decision allow")
    public List<@GraphQLNonNull GqlOperation> getAllowedOperations() {
        DecisionRecord decision = site.details(held.path()).decision();
        return site.guarded("read the operations", () -> QuarantineOperations.allowedOperations(
                HeldState.fromValue(held.state()), decision == null ? null : decision.reason()).stream()
                .map(GqlOperation::of).toList());
    }

    private static String instant(Instant at) {
        return at == null ? null : at.toString();
    }
}
