package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDefaultValue;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;
import java.util.function.Supplier;

/** The quarantine of one site, for a manager of it. The resolver that returns it checked the permission. */
@GraphQLName("FmdbqQuarantine")
@GraphQLDescription("The quarantine of the uploads of the forms of a site, for a manager of the site")
public class GqlQuarantine {

    private final SiteQuarantine site;

    GqlQuarantine(SiteQuarantine site) {
        this.site = site;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The key of the site")
    public String getSiteKey() {
        return site.siteKey();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The number of held submissions of the site in each state")
    public List<@GraphQLNonNull GqlStateCount> getCounts() {
        return site.counts();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The forms of the site that have held submissions, with their number, by title")
    public List<@GraphQLNonNull GqlForm> getForms() {
        return site.forms();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("A page of the held submissions of the site, or of one form, oldest first. limit is at most 100")
    public GqlSubmissionPage submissions(
            @GraphQLName("formId") @GraphQLDescription("The identifier of a form of the site") String formId,
            @GraphQLName("states") @GraphQLDescription("The states to keep; every state when absent")
            List<@GraphQLNonNull GqlState> states,
            @GraphQLName("offset") @GraphQLDefaultValue(DefaultOffset.class)
            @GraphQLDescription("The index of the first item; a negative value reads as 0") Integer offset,
            @GraphQLName("limit") @GraphQLDefaultValue(DefaultLimit.class)
            @GraphQLDescription("The size of the page, at most 100: a larger value reads as 100") Integer limit) {
        return site.page(formId, states, offset, limit);
    }

    @GraphQLField
    @GraphQLDescription("The held submission of the site with this identifier; null when the site has none")
    public GqlHeldSubmission submission(@GraphQLName("id") @GraphQLNonNull
                                        @GraphQLDescription("The identifier of the held submission in the site")
                                        String id) {
        return site.submission(id);
    }

    /** The default of the argument {@code offset}: the first item. */
    public static class DefaultOffset implements Supplier<Object> {
        @Override
        public Object get() {
            return 0;
        }
    }

    /** The default of the argument {@code limit}. */
    public static class DefaultLimit implements Supplier<Object> {
        @Override
        public Object get() {
            return SiteQuarantine.DEFAULT_LIMIT;
        }
    }
}
