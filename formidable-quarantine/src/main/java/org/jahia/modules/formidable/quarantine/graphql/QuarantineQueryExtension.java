package org.jahia.modules.formidable.quarantine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.osgi.BundleUtils;

/**
 * The query of the quarantine of a site. The field checks the permission of the user on the site before anything
 * else, and every field below it reads only the quarantine of that site.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
public final class QuarantineQueryExtension {

    private QuarantineQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("formidableQuarantine")
    @GraphQLDescription("The quarantine of the uploads of a site, for a manager of the site (permission"
            + " formidableQuarantineManage on the site). Null when the user does not manage the site, or when no site"
            + " has this key")
    public static GqlQuarantine formidableQuarantine(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("The key of the site") String siteKey,
            DataFetchingEnvironment environment) {
        SiteQuarantine site = api().open(siteKey, environment.getGraphQlContext());
        return site == null ? null : new GqlQuarantine(site);
    }

    static ManagerApi api() {
        ManagerApi api = BundleUtils.getOsgiService(ManagerApi.class, null);
        if (api == null) {
            throw new DataFetchingException("The quarantine is not available");
        }
        return api;
    }
}
