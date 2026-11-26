package org.jahia.modules.formidable.quarantine.graphql;

import graphql.GraphQLContext;
import org.jahia.modules.formidable.quarantine.ops.Managers;
import org.jahia.modules.formidable.quarantine.ops.QuarantineOperations;
import org.jahia.modules.formidable.quarantine.ops.QuarantineRetention;
import org.jahia.modules.formidable.quarantine.store.CheckStore;
import org.jahia.modules.formidable.quarantine.store.HeldQueries;
import org.jahia.modules.formidable.quarantine.store.QuarantineStore;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The entry of the GraphQL API into the module, an OSGi service: the resolvers of graphql-core are not DS components,
 * so they look this service up. It opens the quarantine of a site only for a manager of the site: the permission
 * {@value Managers#PERMISSION} on the live site node, checked in a session of the user of the request.
 */
@Component(service = ManagerApi.class, immediate = true)
public class ManagerApi {

    private static final Logger log = LoggerFactory.getLogger(ManagerApi.class);
    /** The key, in the context of a GraphQL request, of the quarantines that the request opened, by site key. */
    static final String OPENED = ManagerApi.class.getName() + ".opened";

    private final Managers managers;
    private final FormReader forms;
    private QuarantineStore store;
    private CheckStore checkStore;
    private HeldQueries queries;
    private QuarantineOperations operations;
    private QuarantineRetention retention;

    public ManagerApi() {
        this(Managers.ofThePlatform(), new JcrFormReader());
    }

    ManagerApi(Managers managers, FormReader forms) {
        this.managers = managers;
        this.forms = forms;
    }

    @Reference
    public void setStore(QuarantineStore store) {
        this.store = store;
    }

    @Reference
    public void setCheckStore(CheckStore checkStore) {
        this.checkStore = checkStore;
    }

    @Reference
    public void setQueries(HeldQueries queries) {
        this.queries = queries;
    }

    @Reference
    public void setOperations(QuarantineOperations operations) {
        this.operations = operations;
    }

    @Reference
    public void setRetention(QuarantineRetention retention) {
        this.retention = retention;
    }

    /**
     * The quarantine of the site, for the user of the request, once per GraphQL request: each alias of the field
     * {@code formidableQuarantine} on the same site shares the instance, so its counts, totals, forms and records are
     * read once.
     *
     * @param request the context of the GraphQL request, which keeps the quarantines it opened
     * @return as {@link #open(String)}
     */
    SiteQuarantine open(String siteKey, GraphQLContext request) {
        synchronized (request) {
            Map<String, SiteQuarantine> opened = request.get(OPENED);
            if (opened == null) {
                opened = new HashMap<>();
                request.put(OPENED, opened);
            }
            SiteQuarantine site = opened.get(siteKey);
            if (site == null) {
                site = open(siteKey);
                if (site != null) {
                    opened.put(siteKey, site);
                }
            }
            return site;
        }
    }

    /**
     * The quarantine of the site, for the user of the request.
     *
     * @return {@code null} when the key names no site, or a site that the user does not manage: the two answers are
     * the same
     */
    SiteQuarantine open(String siteKey) {
        boolean manager;
        try {
            manager = HeldIds.isSiteKey(siteKey) && managers.mayManage(siteKey);
        } catch (RuntimeException e) {
            // the class alone, as every failure of the API
            if (log.isWarnEnabled()) {
                log.warn("Could not check the manager of site {}: {}", siteKey, e.getClass().getName());
            }
            throw new DataFetchingException(SiteQuarantine.READ_FAILED);
        }
        if (!manager) {
            return null;
        }
        return new SiteQuarantine(siteKey, new SiteQuarantine.Services(store, checkStore, queries,
                operations::apply, retention::purge, managers, forms));
    }
}
