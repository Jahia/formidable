package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

/**
 * The operations on the quarantine of a site. The field checks the permission of the user on the site before anything
 * else, and every operation below it acts only on the quarantine of that site.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
public final class QuarantineMutationExtension {

    private QuarantineMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("formidableQuarantine")
    @GraphQLDescription("The operations on the quarantine of a site, for a manager of the site (permission"
            + " formidableQuarantineManage on the site). Null when the user does not manage the site, or when no site"
            + " has this key")
    public static GqlQuarantineMutation formidableQuarantine(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("The key of the site") String siteKey,
            DataFetchingEnvironment environment) {
        SiteQuarantine site = QuarantineQueryExtension.api().open(siteKey, environment.getGraphQlContext());
        return site == null ? null : new GqlQuarantineMutation(site);
    }
}
