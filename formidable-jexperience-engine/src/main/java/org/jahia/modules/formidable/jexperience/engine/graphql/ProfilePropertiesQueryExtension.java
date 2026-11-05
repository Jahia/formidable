package org.jahia.modules.formidable.jexperience.engine.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.formidable.engine.api.FmdbMixin;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertiesUnavailableException;
import org.jahia.modules.formidable.jexperience.engine.profile.ProfilePropertyCatalog;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.sites.JahiaSitesService;
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
            + " sorted by label. Requires a form of a site, or a node in one, that the caller may edit.")
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
        // a form of a site, and nothing else: getResolveSite() answers the system site for any other node
        JCRSiteNode site = node.getPath().startsWith("/sites/") ? node.getResolveSite() : null;
        if (site == null || JahiaSitesService.SYSTEM_SITE_KEY.equals(site.getSiteKey()) || !inForm(node)) {
            throw new AccessDeniedException("Not a form of a site: " + path);
        }
        if (catalog == null) {
            return GqlProfileProperties.UNAVAILABLE;
        }
        String siteKey = site.getSiteKey();
        try {
            return new GqlProfileProperties(true, catalog.readableProfileProperties(siteKey).stream().map(GqlProfileProperty::new).toList());
        } catch (ProfilePropertiesUnavailableException e) {
            log.warn("[ProfilePropertiesQueryExtension] No profile properties for site '{}': {}", siteKey, e.getMessage());
            return GqlProfileProperties.UNAVAILABLE;
        }
    }

    /** Whether the node is a form, or sits in one: the editor asks for a field, or for the form or step a new one goes into. */
    private static boolean inForm(JCRNodeWrapper node) throws RepositoryException {
        for (JCRNodeWrapper current = node; current != null; current = current.getDepth() > 0 ? current.getParent() : null) {
            if (current.isNodeType(FmdbMixin.FORM_ROOT)) {
                return true;
            }
        }
        return false;
    }
}
