package org.jahia.modules.formidable.jexperience.engine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertiesUnavailableException;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertyCatalog;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;

/**
 * The profile properties the conditional-logic editor offers for a {@code jexperience:profile} rule. The
 * editor lives in formidable-engine, which knows nothing of jExperience, so it asks here; where this module
 * is not installed the field does not exist, and the editor keeps a text input.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
public final class ProfilePropertiesQueryExtension {

    /** What the caller must hold on the node: the editor asks for the field it edits, or for the parent of a new one. */
    static final String REQUIRED_PERMISSION = "jcr:modifyProperties";

    private static final Logger log = LoggerFactory.getLogger(ProfilePropertiesQueryExtension.class);

    private ProfilePropertiesQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("formidableJExperienceProfileProperties")
    @GraphQLDescription("The visitor profile properties a conditional-logic rule may read on the site of the node,"
            + " sorted by label. Requires the right to edit the node.")
    @GraphQLNonNull
    public static GqlProfileProperties profileProperties(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("The path of the field being edited, or of its parent") String path) {
        try {
            return list(JCRSessionFactory.getInstance().getCurrentUserSession(), BundleUtils.getOsgiService(ProfilePropertyCatalog.class, null), path);
        } catch (RepositoryException e) {
            throw new DataFetchingException(e);
        }
    }

    static GqlProfileProperties list(JCRSessionWrapper session, ProfilePropertyCatalog catalog, String path) throws RepositoryException {
        // getNode refuses a path the caller cannot read
        JCRNodeWrapper node = session.getNode(path);
        if (!node.hasPermission(REQUIRED_PERMISSION)) {
            throw new AccessDeniedException("Permission denied on " + path);
        }
        if (catalog == null) {
            return GqlProfileProperties.UNAVAILABLE;
        }
        String siteKey = node.getResolveSite().getSiteKey();
        try {
            return new GqlProfileProperties(true, catalog.readableProfileProperties(siteKey).stream().map(GqlProfileProperty::new).toList());
        } catch (ProfilePropertiesUnavailableException e) {
            log.warn("[ProfilePropertiesQueryExtension] No profile properties for site '{}': {}", siteKey, e.getMessage());
            return GqlProfileProperties.UNAVAILABLE;
        }
    }
}
