package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** The {@link QuarantineSites} of the module, read with a system session. */
@Component(service = QuarantineSites.class, immediate = true)
public class JcrQuarantineSites implements QuarantineSites {

    /** The mixin of the site settings of the quarantine. */
    public static final String SITE_MIXIN = "fmdbqmix:quarantineSite";
    static final String RETENTION_DAYS = "retentionDays";
    private static final String SITES = "/sites";
    private static final String DEFAULT_WORKSPACE = "default";

    /** Runs the callback in a system session of the workspace. A unit test overrides it with a mocked session. */
    <T> T inSystemSession(String workspace, JCRCallback<T> callback) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, callback);
    }

    @Override
    public List<String> withTree() throws QuarantineStoreException {
        try {
            return inSystemSession(JcrQuarantineStore.LIVE_WORKSPACE, session -> {
                List<String> keys = new ArrayList<>();
                if (!session.nodeExists(SITES)) {
                    return keys;
                }
                JCRNodeIteratorWrapper sites = session.getNode(SITES).getNodes();
                for (JCRNodeWrapper site : sites) {
                    if (site.hasNode(QuarantineTree.ROOT_NAME)
                            && site.getNode(QuarantineTree.ROOT_NAME).isNodeType(QuarantineTree.ROOT_TYPE)) {
                        keys.add(site.getName());
                    }
                }
                return keys;
            });
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("The sites of the quarantine cannot be read", e);
        }
    }

    @Override
    public OptionalLong retentionDays(String siteKey) throws QuarantineStoreException {
        try {
            return inSystemSession(DEFAULT_WORKSPACE, session -> {
                String path = QuarantineTree.sitePath(siteKey);
                if (!session.nodeExists(path)) {
                    return OptionalLong.empty();
                }
                JCRNodeWrapper site = session.getNode(path);
                if (!site.isNodeType(SITE_MIXIN) || !site.hasProperty(RETENTION_DAYS)) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(site.getProperty(RETENTION_DAYS).getLong());
            });
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("The retention of site " + siteKey + " cannot be read", e);
        }
    }
}
