package org.jahia.modules.formidable.quarantine.store;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
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
    /**
     * The due submissions under the root of a site. The path is the one literal: JCR-SQL2 takes no bind variable in
     * ISDESCENDANTNODE, and a JCR name holds no bracket. The states and the time are bind variables.
     */
    static final String DUE_QUERY = "SELECT * FROM [" + QuarantineTree.HELD_TYPE + "] AS held"
            + " WHERE ISDESCENDANTNODE(held, [%s])"
            + " AND (held.[" + QuarantineTree.STATE + "] = $held"
            + " OR (held.[" + QuarantineTree.STATE + "] = $checking"
            + " AND (NOT held.[" + QuarantineTree.NEXT_ATTEMPT + "] IS NOT NULL"
            + " OR held.[" + QuarantineTree.NEXT_ATTEMPT + "] <= $now)))";

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
    public boolean anyDue(String siteKey, Instant now) throws QuarantineStoreException {
        try {
            return inSystemSession(JcrQuarantineStore.LIVE_WORKSPACE, session -> {
                String root = QuarantineTree.rootPath(siteKey);
                if (!session.nodeExists(root)) {
                    return false;
                }
                Query query = createQuery(session, String.format(DUE_QUERY, root));
                ValueFactory values = session.getValueFactory();
                query.bindValue("held", values.createValue(HeldStates.HELD));
                query.bindValue("checking", values.createValue(HeldStates.CHECKING));
                query.bindValue("now", values.createValue(calendar(now)));
                query.setLimit(1);
                return query.execute().getNodes().hasNext();
            });
        } catch (RepositoryException e) {
            throw new QuarantineStoreException("The due submissions of site " + siteKey + " cannot be read", e);
        }
    }

    /** Creates a JCR-SQL2 query of the session. A unit test overrides it with a mocked query. */
    Query createQuery(JCRSessionWrapper session, String statement) throws RepositoryException {
        return session.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2);
    }

    private static Calendar calendar(Instant at) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(at.toEpochMilli());
        return calendar;
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
