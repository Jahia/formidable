package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

/** A form of the site that has held submissions, for the filter of the manager screen: never a value. */
@GraphQLName("FmdbqForm")
@GraphQLDescription("A form of the site that has held submissions, and their number")
public class GqlForm {

    private final SiteQuarantine site;
    private final String formId;
    private final int count;

    GqlForm(SiteQuarantine site, String formId, int count) {
        this.site = site;
        this.formId = formId;
        this.count = count;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The identifier of the form")
    public String getFormId() {
        return formId;
    }

    @GraphQLField
    @GraphQLDescription("The title of the live form in the default language of the site; null when the form is gone")
    public String getFormTitle() {
        return site.form(formId).title();
    }

    @GraphQLField
    @GraphQLDescription("The number of held submissions of the form, in every state")
    public int getCount() {
        return count;
    }
}
